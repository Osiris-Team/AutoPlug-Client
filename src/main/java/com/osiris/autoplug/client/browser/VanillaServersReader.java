package com.osiris.autoplug.client.browser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Small read-only NBT decoder. Vanilla's file and unknown tags are never rewritten. */
public final class VanillaServersReader {
    private static final int MAX_BYTES = 16 * 1024 * 1024, MAX_ITEMS = 10000, MAX_DEPTH = 32;

    public List<SavedServer> read(Path file) throws IOException {
        if (!Files.exists(file)) return Collections.emptyList();
        Map<String, Object> root = readCompound(file);
        List<SavedServer> result = new ArrayList<>();
        Object servers = root.get("servers");
        if (!(servers instanceof List<?>)) return result;
        for (Object value : (List<?>) servers) {
            if (!(value instanceof Map<?, ?>)) continue;
            Map<?, ?> entry = (Map<?, ?>) value;
            if (!(entry.get("ip") instanceof String)) continue;
            try {
                result.add(new SavedServer(entry.get("name") instanceof String ? (String) entry.get("name") : "", (String) entry.get("ip")));
            } catch (IllegalArgumentException ignored) { /* An invalid favorite must not hide valid entries. */ }
        }
        return result;
    }

    /** Shared bounded decoder for servers.dat and read-only local level.dat metadata. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readCompound(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) throw new IOException("NBT file exceeds the 16 MiB import limit.");
        try (BufferedInputStream raw = new BufferedInputStream(Files.newInputStream(file))) {
            raw.mark(2);
            int first = raw.read(), second = raw.read();
            raw.reset();
            InputStream decoded = first == 0x1f && second == 0x8b ? new GZIPInputStream(raw) : raw;
            try (DataInputStream input = new DataInputStream(new LimitedStream(decoded))) {
                if (input.readUnsignedByte() != 10) throw new IOException("NBT file must contain a compound.");
                input.readUTF();
                Map<String, Object> root = (Map<String, Object>) readValue(input, 10, 0, new int[]{100000});
                if (input.read() != -1) throw new IOException("Trailing data in NBT file.");
                return root;
            }
        }
    }

    private Object readValue(DataInputStream input, int type, int depth, int[] budget) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting is too deep.");
        if (--budget[0] < 0) throw new IOException("NBT contains too many values.");
        switch (type) {
            case 1: return input.readByte();
            case 2: return input.readShort();
            case 3: return input.readInt();
            case 4: return input.readLong();
            case 5: return input.readFloat();
            case 6: return input.readDouble();
            case 7: skip(input, length(input, MAX_BYTES)); return null;
            case 8: return input.readUTF();
            case 9:
                int childType = input.readUnsignedByte(), count = length(input, MAX_ITEMS);
                if (childType > 12 || (childType == 0 && count != 0)) throw new IOException("Invalid NBT list type.");
                List<Object> values = new ArrayList<>(count);
                for (int i = 0; i < count; i++) values.add(readValue(input, childType, depth + 1, budget));
                return values;
            case 10:
                Map<String, Object> compound = new LinkedHashMap<>();
                for (int i = 0; i <= MAX_ITEMS; i++) {
                    int child = input.readUnsignedByte();
                    if (child == 0) return compound;
                    if (i == MAX_ITEMS) throw new IOException("Too many NBT compound entries.");
                    String name = input.readUTF();
                    if (compound.containsKey(name)) throw new IOException("Duplicate NBT compound entry: " + name);
                    compound.put(name, readValue(input, child, depth + 1, budget));
                }
                throw new IOException("Invalid NBT compound.");
            case 11: skip(input, length(input, MAX_BYTES / 4) * 4); return null;
            case 12: skip(input, length(input, MAX_BYTES / 8) * 8); return null;
            default: throw new IOException("Unknown NBT tag type: " + type);
        }
    }
    private int length(DataInputStream in, int limit) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > limit) throw new IOException("Invalid NBT collection length.");
        return size;
    }
    private void skip(DataInputStream in, int bytes) throws IOException {
        byte[] buffer = new byte[Math.min(bytes, 8192)];
        while (bytes > 0) { int n = Math.min(bytes, buffer.length); in.readFully(buffer, 0, n); bytes -= n; }
    }
    private static final class LimitedStream extends FilterInputStream {
        private long remaining = MAX_BYTES;
        LimitedStream(InputStream in) { super(in); }
        @Override public int read() throws IOException {
            if (remaining <= 0) throw new IOException("Decoded NBT exceeds the import limit.");
            int value = in.read(); if (value >= 0) remaining--; return value;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (remaining <= 0) throw new IOException("Decoded NBT exceeds the import limit.");
            int n = in.read(b, off, (int) Math.min(len, remaining)); if (n > 0) remaining -= n; return n;
        }
    }
}
