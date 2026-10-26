package com.osiris.autoplug.client.browser;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Small bounded NBT reader/writer. Preserves unknown root and per-server tags during export. */
public final class MinecraftServersDat {
    private static final int MAX_BYTES = 32 * 1024 * 1024, MAX_ITEMS = 100_000, MAX_DEPTH = 64;
    public static final class Tag {
        public final int type; public final Object value;
        public Tag(int type, Object value) { this.type = type; this.value = value; }
    }
    public static final class ListValue {
        public final int elementType; public final List<Tag> values;
        public ListValue(int elementType, List<Tag> values) { this.elementType = elementType; this.values = values; }
    }
    public static final class Document {
        public final String name; public final Tag root; public final boolean compressed;
        public Document(String name, Tag root, boolean compressed) { this.name = name; this.root = root; this.compressed = compressed; }
        public List<SavedServer> servers() throws IOException {
            Map<String, Tag> values = compound(root);
            Tag servers = values.get("servers");
            if (servers == null) return Collections.emptyList();
            if (servers.type != 9 || ((ListValue)servers.value).elementType != 10) throw new IOException("Invalid servers list");
            List<SavedServer> result = new ArrayList<>();
            for (Tag item : ((ListValue)servers.value).values) {
                Map<String, Tag> entry = compound(item);
                try { result.add(new SavedServer(string(entry.get("name")), ServerAddress.parse(string(entry.get("ip"))), "VANILLA")); }
                catch (IllegalArgumentException error) { throw new IOException("Invalid saved server", error); }
            }
            return result;
        }
        public Document withServers(List<SavedServer> replacements) throws IOException {
            Map<String, Tag> values = new LinkedHashMap<>(compound(root));
            Map<ServerAddress, Tag> previous = new LinkedHashMap<>();
            Tag old = values.get("servers");
            if (old != null) {
                servers(); // Validate before replacing, so malformed data is not silently discarded.
                for (Tag item : ((ListValue)old.value).values) {
                    Map<String, Tag> entry = compound(item);
                    previous.put(ServerAddress.parse(string(entry.get("ip"))), item);
                }
            }
            List<Tag> entries = new ArrayList<>();
            for (SavedServer server : replacements) {
                Tag retained = previous.get(server.address);
                Map<String, Tag> entry = retained == null ? new LinkedHashMap<>() : new LinkedHashMap<>(compound(retained));
                entry.put("name", new Tag(8, server.name)); entry.put("ip", new Tag(8, server.address.toString()));
                entries.add(new Tag(10, entry));
            }
            values.put("servers", new Tag(9, new ListValue(10, entries)));
            return new Document(name, new Tag(10, values), compressed);
        }
    }
    public static Document empty() { return new Document("", new Tag(10, new LinkedHashMap<String, Tag>()), false); }
    public static Document read(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) throw new IOException("Invalid or oversized servers.dat");
        try (InputStream input = Files.newInputStream(file)) { return read(input); }
    }
    public static Document read(InputStream input) throws IOException {
        PushbackInputStream source = new PushbackInputStream(input, 2);
        int first = source.read(), second = source.read();
        if (second >= 0) source.unread(second); if (first >= 0) source.unread(first);
        boolean gzip = first == 0x1f && second == 0x8b;
        InputStream payload = gzip ? new GZIPInputStream(source) : source;
        try (DataInputStream data = new DataInputStream(new LimitedInput(payload))) {
            int type = data.readUnsignedByte();
            if (type != 10) throw new IOException("servers.dat root must be an NBT compound");
            String name = data.readUTF();
            Tag root = readTag(data, type, 0);
            if (data.read() != -1) throw new IOException("Trailing NBT data");
            return new Document(name, root, gzip);
        }
    }
    private static Tag readTag(DataInputStream input, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting limit exceeded");
        switch (type) {
            case 1: return new Tag(type, input.readByte());
            case 2: return new Tag(type, input.readShort());
            case 3: return new Tag(type, input.readInt());
            case 4: return new Tag(type, input.readLong());
            case 5: return new Tag(type, input.readFloat());
            case 6: return new Tag(type, input.readDouble());
            case 7: { byte[] bytes = new byte[count(input, MAX_BYTES)]; input.readFully(bytes); return new Tag(type, bytes); }
            case 8: return new Tag(type, input.readUTF());
            case 9: {
                int childType = input.readUnsignedByte(), size = count(input, MAX_ITEMS);
                if (childType > 12 || (childType == 0 && size != 0)) throw new IOException("Invalid NBT list type");
                List<Tag> values = new ArrayList<>();
                for (int i = 0; i < size; i++) values.add(readTag(input, childType, depth + 1));
                return new Tag(type, new ListValue(childType, values));
            }
            case 10: {
                Map<String, Tag> values = new LinkedHashMap<>();
                int child;
                while ((child = input.readUnsignedByte()) != 0) {
                    if (values.size() >= MAX_ITEMS) throw new IOException("NBT compound limit exceeded");
                    String key = input.readUTF();
                    if (values.containsKey(key)) throw new IOException("Duplicate NBT tag");
                    values.put(key, readTag(input, child, depth + 1));
                }
                return new Tag(type, values);
            }
            case 11: { int[] values = new int[count(input, MAX_BYTES / 4)]; for (int i = 0; i < values.length; i++) values[i] = input.readInt(); return new Tag(type, values); }
            case 12: { long[] values = new long[count(input, MAX_BYTES / 8)]; for (int i = 0; i < values.length; i++) values[i] = input.readLong(); return new Tag(type, values); }
            default: throw new IOException("Unsupported NBT type " + type);
        }
    }
    private static int count(DataInputStream input, int limit) throws IOException { int size = input.readInt(); if (size < 0 || size > limit) throw new IOException("Invalid NBT length"); return size; }
    @SuppressWarnings("unchecked") private static Map<String, Tag> compound(Tag tag) throws IOException { if (tag == null || tag.type != 10) throw new IOException("Expected compound"); return (Map<String, Tag>)tag.value; }
    private static String string(Tag tag) throws IOException { if (tag == null || tag.type != 8) throw new IOException("Expected string"); return (String)tag.value; }
    public static void write(Document document, OutputStream output) throws IOException {
        OutputStream payload = document.compressed ? new GZIPOutputStream(output) : output;
        try (DataOutputStream data = new DataOutputStream(payload)) { data.writeByte(10); data.writeUTF(document.name); writeTag(data, document.root, 0); }
    }
    private static void writeTag(DataOutputStream output, Tag tag, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting limit exceeded");
        switch (tag.type) {
            case 1: output.writeByte(((Number)tag.value).byteValue()); break;
            case 2: output.writeShort(((Number)tag.value).shortValue()); break;
            case 3: output.writeInt(((Number)tag.value).intValue()); break;
            case 4: output.writeLong(((Number)tag.value).longValue()); break;
            case 5: output.writeFloat(((Number)tag.value).floatValue()); break;
            case 6: output.writeDouble(((Number)tag.value).doubleValue()); break;
            case 7: { byte[] bytes = (byte[])tag.value; output.writeInt(bytes.length); output.write(bytes); break; }
            case 8: output.writeUTF((String)tag.value); break;
            case 9: { ListValue list = (ListValue)tag.value; output.writeByte(list.elementType); output.writeInt(list.values.size()); for (Tag child : list.values) { if (child.type != list.elementType) throw new IOException("Mixed NBT list"); writeTag(output, child, depth + 1); } break; }
            case 10: { for (Map.Entry<String, Tag> entry : compound(tag).entrySet()) { output.writeByte(entry.getValue().type); output.writeUTF(entry.getKey()); writeTag(output, entry.getValue(), depth + 1); } output.writeByte(0); break; }
            case 11: { int[] values = (int[])tag.value; output.writeInt(values.length); for (int value : values) output.writeInt(value); break; }
            case 12: { long[] values = (long[])tag.value; output.writeInt(values.length); for (long value : values) output.writeLong(value); break; }
            default: throw new IOException("Unsupported NBT type");
        }
    }
    private static final class LimitedInput extends FilterInputStream {
        private int remaining = MAX_BYTES;
        LimitedInput(InputStream source) { super(source); }
        @Override public int read() throws IOException { int result = in.read(); if (result >= 0 && --remaining < 0) throw new IOException("NBT size limit exceeded"); return result; }
        @Override public int read(byte[] bytes, int offset, int size) throws IOException { if (size == 0) return 0; int count = in.read(bytes, offset, Math.min(size, Math.max(1, remaining))); if (count > 0 && (remaining -= count) < 0) throw new IOException("NBT size limit exceeded"); return count; }
    }
}
