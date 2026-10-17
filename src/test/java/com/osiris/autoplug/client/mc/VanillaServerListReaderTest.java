package com.osiris.autoplug.client.mc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class VanillaServerListReaderTest {
    private final VanillaServerListReader reader = new VanillaServerListReader();

    @Test
    void readsPlainSyntheticServerNamesAndAddresses() throws Exception {
        List<VanillaServerListReader.ServerEntry> entries = reader.read(input(servers("Home", "localhost:25565")));
        assertEquals(1, entries.size());
        assertEquals("Home", entries.get(0).getName());
        assertEquals("localhost:25565", entries.get(0).getAddress());
        assertThrows(UnsupportedOperationException.class, entries::clear);
    }

    @Test
    void readsGzipAndPreservesJavaModifiedUtfIncludingSupplementaryCharacters() throws Exception {
        String name = "Español 中文 \u0000 \ud83d\ude80";
        List<VanillaServerListReader.ServerEntry> entries = reader.read(input(gzip(servers(name, "[::1]:25565"))));
        assertEquals(name, entries.get(0).getName());
        assertEquals("[::1]:25565", entries.get(0).getAddress());
    }

    @Test
    void readsPathWithoutChangingItsBytes() throws Exception {
        byte[] fixture = gzip(servers("Synthetic", "example.invalid"));
        Path file = Files.createTempFile("autoplug-nbt-fixture-", ".dat");
        try {
            Files.write(file, fixture);
            assertEquals("Synthetic", reader.read(file).get(0).getName());
            assertArrayEquals(fixture, Files.readAllBytes(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void leavesCallerOwnedStreamOpenForPlainAndGzip() throws Exception {
        for (byte[] fixture : new byte[][]{servers("A", "a.invalid"), gzip(servers("B", "b.invalid"))}) {
            TrackingInput input = new TrackingInput(fixture);
            reader.read(input);
            assertFalse(input.closed);
        }
    }

    @Test
    void skipsUnknownScalarArrayListAndNestedCompoundTags() throws Exception {
        byte[] fixture = document(out -> {
            named(out, 1, "byte"); out.writeByte(7);
            named(out, 2, "short"); out.writeShort(123);
            named(out, 3, "int"); out.writeInt(123);
            named(out, 4, "long"); out.writeLong(Long.MAX_VALUE);
            named(out, 5, "float"); out.writeFloat(1.5f);
            named(out, 6, "double"); out.writeDouble(2.5);
            named(out, 7, "bytes"); out.writeInt(3); out.write(new byte[]{1, 2, 3});
            named(out, 8, "text"); out.writeUTF("ignored");
            named(out, 9, "list"); out.writeByte(3); out.writeInt(2); out.writeInt(1); out.writeInt(2);
            named(out, 10, "compound"); named(out, 8, "child"); out.writeUTF("nested"); out.writeByte(0);
            named(out, 11, "ints"); out.writeInt(1); out.writeInt(123);
            named(out, 12, "longs"); out.writeInt(1); out.writeLong(456);
            writeServers(out, "Keep", "keep.invalid", true);
        });
        assertEquals("Keep", reader.read(input(fixture)).get(0).getName());
    }

    @Test
    void acceptsEmptyCompoundListAndEmptyEndList() throws Exception {
        for (int elementType : new int[]{0, 10}) {
            byte[] fixture = document(out -> {
                named(out, 9, "servers"); out.writeByte(elementType); out.writeInt(0);
            });
            assertTrue(reader.read(input(fixture)).isEmpty());
        }
    }

    @Test
    void preservesOrderAndRequiresBothListLengthLimits() throws Exception {
        byte[] fixture = document(out -> {
            named(out, 9, "servers"); out.writeByte(10); out.writeInt(2);
            named(out, 8, "name"); out.writeUTF("First");
            named(out, 8, "ip"); out.writeUTF("first.invalid"); out.writeByte(0);
            named(out, 8, "name"); out.writeUTF("Second");
            named(out, 8, "ip"); out.writeUTF("second.invalid"); out.writeByte(0);
        });
        List<VanillaServerListReader.ServerEntry> entries = reader.read(input(fixture));
        assertEquals("First", entries.get(0).getName());
        assertEquals("second.invalid", entries.get(1).getAddress());
        assertThrows(IOException.class, () -> limited(10000, 10000, 64, 65535, 1, 10, 100)
                .read(input(fixture)));
        assertThrows(IOException.class, () -> limited(10000, 10000, 64, 65535, 10, 1, 100)
                .read(input(fixture)));
    }

    @Test
    void rejectsEveryTruncatedPrefixWithoutReturningPartialResults() throws Exception {
        byte[] fixture = servers("Do not partially import", "host.invalid");
        for (int length = 0; length < fixture.length; length++) {
            final byte[] prefix = Arrays.copyOf(fixture, length);
            assertThrows(IOException.class, () -> reader.read(input(prefix)), "prefix " + length);
        }
    }

    @Test
    void rejectsTruncatedAndCorruptGzip() throws Exception {
        byte[] fixture = gzip(servers("A", "a.invalid"));
        assertThrows(IOException.class, () -> reader.read(input(Arrays.copyOf(fixture, fixture.length - 1))));
        fixture[fixture.length - 8] ^= 1;
        assertThrows(IOException.class, () -> reader.read(input(fixture)));
    }

    @Test
    void encodedByteLimitIsInclusive() throws Exception {
        byte[] fixture = servers("A", "a.invalid");
        assertEquals(1, limited(fixture.length, 10000, 64, 65535, 1000, 1000, 1000)
                .read(input(fixture)).size());
        assertThrows(IOException.class, () -> limited(fixture.length - 1, 10000, 64, 65535, 1000, 1000, 1000)
                .read(input(fixture)));
    }

    @Test
    void decompressedByteLimitBoundsHighlyCompressibleUnknownData() throws Exception {
        byte[] fixture = gzip(document(out -> {
            named(out, 7, "padding"); out.writeInt(3000); out.write(new byte[3000]);
            writeServers(out, "A", "a.invalid", false);
        }));
        assertTrue(fixture.length < 1000);
        assertThrows(IOException.class, () -> limited(1000, 1000, 64, 65535, 10000, 1000, 1000)
                .read(input(fixture)));
    }

    @Test
    void rejectsExcessiveNestingAndTagCount() throws Exception {
        byte[] fixture = document(out -> {
            named(out, 10, "one"); named(out, 10, "two");
            named(out, 8, "three"); out.writeUTF("leaf"); out.writeByte(0); out.writeByte(0);
            writeServers(out, "A", "a.invalid", false);
        });
        assertThrows(IOException.class, () -> limited(10000, 10000, 3, 65535, 1000, 1000, 1000)
                .read(input(fixture)));
        assertThrows(IOException.class, () -> limited(10000, 10000, 64, 65535, 1000, 1000, 3)
                .read(input(servers("A", "a.invalid"))));
    }

    @Test
    void rejectsExcessiveStringServerAndCollectionLengthsBeforeAllocation() throws Exception {
        assertThrows(IOException.class, () -> limited(10000, 10000, 64, 12, 1000, 1000, 1000)
                .read(input(servers("Long server name", "a.invalid"))));
        byte[] serversTooMany = document(out -> {
            named(out, 9, "servers"); out.writeByte(10); out.writeInt(Integer.MAX_VALUE);
        });
        assertThrows(IOException.class, () -> reader.read(input(serversTooMany)));
        byte[] arrayTooLarge = document(out -> {
            named(out, 12, "array"); out.writeInt(Integer.MAX_VALUE);
        });
        assertThrows(IOException.class, () -> reader.read(input(arrayTooLarge)));
        byte[] listTooLarge = document(out -> {
            named(out, 9, "other"); out.writeByte(10); out.writeInt(Integer.MAX_VALUE);
        });
        assertThrows(IOException.class, () -> reader.read(input(listTooLarge)));
    }

    @Test
    void rejectsNegativeCollectionLengths() throws Exception {
        for (int type : new int[]{7, 9, 11, 12}) {
            byte[] fixture = document(out -> {
                named(out, type, "unknown");
                if (type == 9) out.writeByte(10);
                out.writeInt(-1);
            });
            assertThrows(IOException.class, () -> reader.read(input(fixture)));
        }
    }

    @Test
    void rejectsIllegalTagIdsAndNonemptyEndLists() throws Exception {
        for (int type : new int[]{13, 255}) {
            assertThrows(IOException.class, () -> reader.read(input(document(out -> out.writeByte(type)))));
            assertThrows(IOException.class, () -> reader.read(input(document(out -> {
                named(out, 9, "servers"); out.writeByte(type); out.writeInt(0);
            }))));
        }
        assertThrows(IOException.class, () -> reader.read(input(document(out -> {
            named(out, 9, "unknown"); out.writeByte(0); out.writeInt(1);
        }))));
    }

    @Test
    void rejectsWrongRootTypeAndTrailingBytes() throws Exception {
        assertThrows(IOException.class, () -> reader.read(input(new byte[]{0})));
        byte[] valid = servers("A", "a.invalid");
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IOException.class, () -> reader.read(input(trailing)));
    }

    @Test
    void rejectsMissingDuplicateAndWrongTypedSchemaFields() throws Exception {
        assertThrows(IOException.class, () -> reader.read(input(document(out -> {}))));
        assertThrows(IOException.class, () -> reader.read(input(document(out -> {
            writeServers(out, "A", "a.invalid", false); writeServers(out, "B", "b.invalid", false);
        }))));
        assertThrows(IOException.class, () -> reader.read(input(document(out -> {
            named(out, 9, "servers"); out.writeByte(10); out.writeInt(1);
            named(out, 8, "name"); out.writeUTF("Missing ip"); out.writeByte(0);
        }))));
        assertThrows(IOException.class, () -> reader.read(input(document(out -> {
            named(out, 9, "servers"); out.writeByte(10); out.writeInt(1);
            named(out, 1, "name"); out.writeByte(0); out.writeByte(0);
        }))));
        assertThrows(IOException.class, () -> reader.read(input(document(out -> {
            named(out, 9, "servers"); out.writeByte(10); out.writeInt(1);
            named(out, 8, "name"); out.writeUTF("A"); named(out, 8, "name"); out.writeUTF("B"); out.writeByte(0);
        }))));
    }

    @Test
    void rejectsMalformedModifiedUtfInsteadOfReplacingBytes() throws Exception {
        byte[] fixture = document(out -> {
            named(out, 9, "servers"); out.writeByte(10); out.writeInt(1);
            named(out, 8, "name"); out.writeShort(1); out.writeByte(0xff);
        });
        assertThrows(IOException.class, () -> reader.read(input(fixture)));
    }

    @Test
    void rejectsInvalidLimitConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> limited(0, 100, 1, 10, 10, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> limited(100, 100, 1, 65536, 10, 10, 10));
    }

    private static VanillaServerListReader limited(long encoded, long decoded, int depth, int string,
                                                   int collection, int servers, int tags) {
        return new VanillaServerListReader(new VanillaServerListReader.Limits(
                encoded, decoded, depth, string, collection, servers, tags));
    }

    private static ByteArrayInputStream input(byte[] fixture) {
        return new ByteArrayInputStream(fixture);
    }

    private static byte[] servers(String name, String address) throws IOException {
        return document(out -> writeServers(out, name, address, false));
    }

    private static byte[] document(Writer body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            named(out, 10, "");
            body.write(out);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static void named(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    private static void writeServers(DataOutputStream out, String name, String address, boolean unknown) throws IOException {
        named(out, 9, "servers"); out.writeByte(10); out.writeInt(1);
        named(out, 8, "name"); out.writeUTF(name);
        if (unknown) { named(out, 1, "acceptTextures"); out.writeByte(1); }
        named(out, 8, "ip"); out.writeUTF(address);
        if (unknown) { named(out, 8, "icon"); out.writeUTF("synthetic-icon"); }
        out.writeByte(0);
    }

    private static byte[] gzip(byte[] plain) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) { out.write(plain); }
        return bytes.toByteArray();
    }

    private interface Writer { void write(DataOutputStream out) throws IOException; }

    private static final class TrackingInput extends ByteArrayInputStream {
        private boolean closed;
        private TrackingInput(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
}
