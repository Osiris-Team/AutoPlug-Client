package com.osiris.autoplug.client.mc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Read-only importer for a Java Edition servers.dat NBT compound. Only server
 * names and addresses are returned; unknown NBT fields are validated and skipped.
 * Supports uncompressed and gzip data, not Bedrock/little-endian NBT or zlib.
 */
public final class VanillaServerListReader {
    public static final Limits DEFAULT_LIMITS = new Limits(
            8 * 1024 * 1024, 8 * 1024 * 1024, 64, 65535, 100000, 4096, 100000);

    private final Limits limits;

    public VanillaServerListReader() {
        this(DEFAULT_LIMITS);
    }

    public VanillaServerListReader(Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Opens and closes the file, without creating, changing or replacing it. */
    public List<ServerEntry> read(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return read(input);
        }
    }

    /** Reads one complete NBT document; the caller's stream is left open. */
    public List<ServerEntry> read(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        PushbackInputStream encoded = new PushbackInputStream(new LimitedInputStream(
                new NonClosingInputStream(input), limits.maxInputBytes), 2);
        int first = encoded.read();
        int second = encoded.read();
        if (second != -1) encoded.unread(second);
        if (first != -1) encoded.unread(first);
        try (InputStream decoded = first == 0x1f && second == 0x8b
                ? new GZIPInputStream(encoded) : encoded) {
            DataInputStream data = new DataInputStream(new LimitedInputStream(
                    decoded, limits.maxDecodedBytes));
            Parser parser = new Parser(data, limits);
            List<ServerEntry> result = parser.readRoot();
            // Reading through EOF also verifies a gzip trailer/checksum.
            if (data.read() != -1) throw new IOException("Trailing data after NBT root");
            return Collections.unmodifiableList(result);
        } catch (EOFException e) {
            throw new IOException("Truncated servers.dat NBT", e);
        }
    }

    public static final class ServerEntry {
        private final String name;
        private final String address;

        private ServerEntry(String name, String address) {
            this.name = name;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        /** Original ip string, including an optional port; no connection is made. */
        public String getAddress() {
            return address;
        }
    }

    /** Independent encoded/decoded byte limits also bound gzip expansion. */
    public static final class Limits {
        private final long maxInputBytes;
        private final long maxDecodedBytes;
        private final int maxDepth;
        private final int maxStringBytes;
        private final int maxCollectionLength;
        private final int maxServers;
        private final int maxTags;

        public Limits(long maxInputBytes, long maxDecodedBytes, int maxDepth,
                      int maxStringBytes, int maxCollectionLength, int maxServers, int maxTags) {
            if (maxInputBytes < 1 || maxDecodedBytes < 1 || maxDepth < 1
                    || maxStringBytes < 1 || maxStringBytes > 65535
                    || maxCollectionLength < 1 || maxServers < 1 || maxTags < 1) {
                throw new IllegalArgumentException("NBT limits must be positive; strings are at most 65535 bytes");
            }
            this.maxInputBytes = maxInputBytes;
            this.maxDecodedBytes = maxDecodedBytes;
            this.maxDepth = maxDepth;
            this.maxStringBytes = maxStringBytes;
            this.maxCollectionLength = maxCollectionLength;
            this.maxServers = maxServers;
            this.maxTags = maxTags;
        }
    }

    private static final class Parser {
        private final DataInputStream in;
        private final Limits limits;
        private final byte[] scratch = new byte[8192];
        private long tags;

        private Parser(DataInputStream in, Limits limits) {
            this.in = in;
            this.limits = limits;
        }

        private List<ServerEntry> readRoot() throws IOException {
            if (readType() != 10) throw new IOException("NBT root must be a compound");
            readString(); // Root name is not part of the server data.
            enter(1);
            List<ServerEntry> servers = null;
            int type;
            while ((type = readType()) != 0) {
                String key = readString();
                if ("servers".equals(key)) {
                    if (servers != null || type != 9) {
                        throw new IOException("Expected one servers list");
                    }
                    servers = readServers(2);
                } else {
                    skip(type, 2);
                }
            }
            if (servers == null) throw new IOException("NBT compound has no servers list");
            return servers;
        }

        private List<ServerEntry> readServers(int depth) throws IOException {
            enter(depth);
            int childType = readType();
            int count = readLength(Math.min(limits.maxServers, limits.maxCollectionLength));
            if (childType != 10 && !(count == 0 && childType == 0)) {
                throw new IOException("servers must contain compounds");
            }
            List<ServerEntry> result = new ArrayList<>(Math.min(count, 64));
            for (int i = 0; i < count; i++) result.add(readServer(depth + 1));
            return result;
        }

        private ServerEntry readServer(int depth) throws IOException {
            enter(depth);
            String name = null;
            String address = null;
            int type;
            while ((type = readType()) != 0) {
                String key = readString();
                if ("name".equals(key) || "ip".equals(key)) {
                    if (type != 8) throw new IOException("Server name/ip must be strings");
                    enter(depth + 1);
                    if ("name".equals(key)) {
                        if (name != null) throw new IOException("Duplicate server name");
                        name = readString();
                    } else {
                        if (address != null) throw new IOException("Duplicate server ip");
                        address = readString();
                    }
                } else {
                    skip(type, depth + 1);
                }
            }
            if (name == null || address == null) throw new IOException("Server is missing name or ip");
            return new ServerEntry(name, address);
        }

        private void skip(int type, int depth) throws IOException {
            enter(depth);
            switch (type) {
                case 1: skipBytes(1); break;
                case 2: skipBytes(2); break;
                case 3: case 5: skipBytes(4); break;
                case 4: case 6: skipBytes(8); break;
                case 7: skipBytes(readLength(limits.maxCollectionLength)); break;
                case 8: readString(); break;
                case 9:
                    int childType = readType();
                    int length = readLength(limits.maxCollectionLength);
                    if (childType == 0 && length != 0) throw new IOException("Nonempty TAG_End list");
                    for (int i = 0; i < length; i++) skip(childType, depth + 1);
                    break;
                case 10:
                    int child;
                    while ((child = readType()) != 0) {
                        readString();
                        skip(child, depth + 1);
                    }
                    break;
                case 11: skipBytes(4L * readLength(limits.maxCollectionLength)); break;
                case 12: skipBytes(8L * readLength(limits.maxCollectionLength)); break;
                default: throw new IOException("Invalid NBT payload type");
            }
        }

        private void enter(int depth) throws IOException {
            if (depth > limits.maxDepth) throw new IOException("NBT depth limit exceeded");
            if (++tags > limits.maxTags) throw new IOException("NBT tag limit exceeded");
        }

        private int readType() throws IOException {
            int type = in.readUnsignedByte();
            if (type > 12) throw new IOException("Illegal NBT tag type");
            return type;
        }

        private int readLength(int maximum) throws IOException {
            int length = in.readInt();
            if (length < 0 || length > maximum) throw new IOException("NBT collection length limit exceeded");
            return length;
        }

        private String readString() throws IOException {
            int length = in.readUnsignedShort();
            if (length > limits.maxStringBytes) throw new IOException("NBT string length limit exceeded");
            byte[] data = new byte[length + 2];
            data[0] = (byte) (length >>> 8);
            data[1] = (byte) length;
            in.readFully(data, 2, length);
            // Java Edition NBT strings use DataInput's modified UTF-8 encoding.
            return new DataInputStream(new ByteArrayInputStream(data)).readUTF();
        }

        private void skipBytes(long length) throws IOException {
            while (length > 0) {
                int count = (int) Math.min(scratch.length, length);
                in.readFully(scratch, 0, count);
                length -= count;
            }
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximum;
        private long count;

        private LimitedInputStream(InputStream input, long maximum) {
            super(input);
            this.maximum = maximum;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1 && ++count > maximum) throw new IOException("NBT byte limit exceeded");
            return value;
        }

        @Override
        public int read(byte[] data, int offset, int length) throws IOException {
            if (length == 0) return 0;
            long remaining = maximum - count;
            int request = remaining < length ? (int) (remaining + 1) : length;
            int read = in.read(data, offset, request);
            if (read > 0 && (count += read) > maximum) throw new IOException("NBT byte limit exceeded");
            return read;
        }
    }

    private static final class NonClosingInputStream extends FilterInputStream {
        private NonClosingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public void close() {
            // The read(InputStream) overload does not own the caller's stream.
        }
    }
}
