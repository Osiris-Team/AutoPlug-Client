package com.osiris.autoplug.client.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ServerBrowserTest {
    @TempDir Path directory;

    @Test void parsesDefaultExplicitAndIpv6AddressesWithoutDns() {
        assertEquals("example.com", ServerAddress.parse(" EXAMPLE.com:25565 ").toString());
        ServerAddress ipv6 = ServerAddress.parse("[2001:db8::1]:25566");
        assertEquals("2001:db8::1", ipv6.host); assertEquals(25566, ipv6.port);
        assertEquals("[::1]", ServerAddress.parse("::1").toString());
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("https://example.com"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("example.com:65536"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("example.com:0"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("a b:1"));
    }

    @Test void importsRawAndCompressedNbtWithoutChangingVanillaFile() throws Exception {
        byte[] raw = favorites();
        Path vanilla = directory.resolve("servers.dat"); Files.write(vanilla, raw);
        List<SavedServer> read = new VanillaServersReader().read(vanilla);
        assertEquals(2, read.size()); assertEquals("Friends 世界", read.get(0).name);
        assertEquals("example.com", read.get(0).address); assertEquals("[::1]:25566", read.get(1).address);
        ServerBrowserService service = new ServerBrowserService(directory.resolve("autoplug/servers.json"), vanilla);
        assertEquals(2, service.importVanilla()); assertEquals(0, service.importVanilla());
        assertArrayEquals(raw, Files.readAllBytes(vanilla));
        service.add("Renamed favorite", "EXAMPLE.com:25565");
        List<SavedServer> saved = new ServerBrowserService(directory.resolve("autoplug/servers.json"), vanilla).list();
        assertEquals(2, saved.size()); assertEquals("Renamed favorite", saved.get(1).name);
        service.remove("example.com"); assertEquals(1, service.list().size());
        Path compressed = directory.resolve("compressed.dat");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(compressed))) { output.write(raw); }
        assertEquals(2, new VanillaServersReader().read(compressed).size());
    }

    @Test void corruptFavoritesArePreservedInsteadOfOverwritten() throws Exception {
        Path storage = directory.resolve("servers.json"); byte[] malformed = "{broken".getBytes(StandardCharsets.UTF_8); Files.write(storage, malformed);
        ServerBrowserService service = new ServerBrowserService(storage, directory.resolve("missing.dat"));
        assertThrows(IOException.class, () -> service.add("new", "example.com"));
        assertArrayEquals(malformed, Files.readAllBytes(storage));
        assertTrue(new VanillaServersReader().read(directory.resolve("missing.dat")).isEmpty());
    }

    @Test void rejectsHostileNbtLengthsDepthAndTruncation() throws Exception {
        Path file = directory.resolve("invalid.dat");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(9); out.writeUTF("servers"); out.writeByte(10); out.writeInt(Integer.MAX_VALUE);
        }
        assertThrows(IOException.class, () -> new VanillaServersReader().read(file));
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i < 40; i++) { out.writeByte(10); out.writeUTF("nested"); }
        }
        assertThrows(IOException.class, () -> new VanillaServersReader().read(file));
        Files.write(file, new byte[]{10, 0, 0, 8, 0});
        assertThrows(IOException.class, () -> new VanillaServersReader().read(file));
    }

    private byte[] favorites() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(10); out.writeUTF("");
            // Exercise unrelated array/list/scalar types commonly added by mods.
            out.writeByte(7); out.writeUTF("unknownBytes"); out.writeInt(2); out.write(new byte[]{1, 2});
            out.writeByte(11); out.writeUTF("unknownInts"); out.writeInt(1); out.writeInt(42);
            out.writeByte(12); out.writeUTF("unknownLongs"); out.writeInt(1); out.writeLong(42);
            out.writeByte(9); out.writeUTF("servers"); out.writeByte(10); out.writeInt(3);
            string(out, "name", "Friends 世界"); string(out, "ip", "EXAMPLE.com:25565");
            out.writeByte(1); out.writeUTF("acceptTextures"); out.writeByte(1); out.writeByte(0);
            string(out, "name", "Local IPv6"); string(out, "ip", "[::1]:25566"); out.writeByte(0);
            string(out, "name", "Malformed favorite"); string(out, "ip", "https://invalid.example"); out.writeByte(0);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }
    private void string(DataOutputStream out, String key, String value) throws IOException { out.writeByte(8); out.writeUTF(key); out.writeUTF(value); }
}
