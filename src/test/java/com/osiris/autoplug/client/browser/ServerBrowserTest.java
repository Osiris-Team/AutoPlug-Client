package com.osiris.autoplug.client.browser;

import com.osiris.autoplug.client.utils.MineStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerBrowserTest {
    @TempDir Path temp;
    @Test void addressesValidatePortsAndBracketedIpv6WithoutAcceptingUrlsOrCommandFlags() {
        assertEquals("example.org", ServerAddress.parse(" EXAMPLE.ORG:25565 ").toString());
        assertEquals("[::1]:25566", ServerAddress.parse("[::1]:25566").toString());
        for (String invalid : new String[]{"", "https://server", "-x", "host:0", "host:65536", "host:wat", "::1", "host\n--flag", "host/path"}) assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse(invalid));
    }
    @Test void rawAndGzipNbtRoundTripAllTagTypesAndPreserveUnknownServerFields() throws Exception {
        Map<String, MinecraftServersDat.Tag> root = new LinkedHashMap<>();
        root.put("byte", new MinecraftServersDat.Tag(1, (byte)1)); root.put("short", new MinecraftServersDat.Tag(2, (short)2)); root.put("int", new MinecraftServersDat.Tag(3, 3)); root.put("long", new MinecraftServersDat.Tag(4, 4L));
        root.put("float", new MinecraftServersDat.Tag(5, 1.25f)); root.put("double", new MinecraftServersDat.Tag(6, 2.5)); root.put("bytes", new MinecraftServersDat.Tag(7, new byte[]{1,2}));
        root.put("string", new MinecraftServersDat.Tag(8, "Bos 🌲\0 café")); root.put("list", new MinecraftServersDat.Tag(9, new MinecraftServersDat.ListValue(8, Collections.singletonList(new MinecraftServersDat.Tag(8, "item")))));
        root.put("compound", new MinecraftServersDat.Tag(10, new LinkedHashMap<String, MinecraftServersDat.Tag>())); root.put("ints", new MinecraftServersDat.Tag(11, new int[]{-1,2})); root.put("longs", new MinecraftServersDat.Tag(12, new long[]{-3,4}));
        Map<String, MinecraftServersDat.Tag> server = new LinkedHashMap<>(); server.put("name", new MinecraftServersDat.Tag(8, "🌲 World")); server.put("ip", new MinecraftServersDat.Tag(8, "localhost:25566")); server.put("icon", new MinecraftServersDat.Tag(8, "data:image/png;base64,fixture")); server.put("acceptTextures", new MinecraftServersDat.Tag(1, (byte)1));
        root.put("servers", new MinecraftServersDat.Tag(9, new MinecraftServersDat.ListValue(10, Collections.singletonList(new MinecraftServersDat.Tag(10, server)))));
        for (boolean compressed : new boolean[]{false, true}) {
            MinecraftServersDat.Document original = new MinecraftServersDat.Document("Root", new MinecraftServersDat.Tag(10, root), compressed);
            byte[] before = bytes(original); MinecraftServersDat.Document loaded = MinecraftServersDat.read(new ByteArrayInputStream(before));
            assertArrayEquals(before, bytes(loaded)); assertEquals("🌲 World", loaded.servers().get(0).name);
            MinecraftServersDat.Document changed = loaded.withServers(Collections.singletonList(new SavedServer("Renamed", ServerAddress.parse("localhost:25566"), "FABRIC")));
            assertEquals("Renamed", MinecraftServersDat.read(new ByteArrayInputStream(bytes(changed))).servers().get(0).name);
            assertTrue(new String(bytes(new MinecraftServersDat.Document(changed.name, changed.root, false)), StandardCharsets.ISO_8859_1).contains("data:image/png;base64,fixture"));
        }
    }
    @Test void nbtRejectsTruncationNegativeArrayLengthsDuplicatesAndTrailingData() throws Exception {
        byte[] valid = bytes(MinecraftServersDat.empty());
        assertThrows(IOException.class, () -> MinecraftServersDat.read(new ByteArrayInputStream(Arrays.copyOf(valid, valid.length - 1))));
        ByteArrayOutputStream out = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(out); data.writeByte(10); data.writeUTF(""); data.writeByte(7); data.writeUTF("bad"); data.writeInt(-1);
        assertThrows(IOException.class, () -> MinecraftServersDat.read(new ByteArrayInputStream(out.toByteArray())));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1); assertThrows(IOException.class, () -> MinecraftServersDat.read(new ByteArrayInputStream(trailing)));
        out.reset(); data.writeByte(10); data.writeUTF(""); for (int i = 0; i < 2; i++) { data.writeByte(1); data.writeUTF("same"); data.writeByte(i); } data.writeByte(0);
        assertThrows(IOException.class, () -> MinecraftServersDat.read(new ByteArrayInputStream(out.toByteArray())));
    }
    @Test void catalogImportDeduplicatesAddressesAndExportPreservesIconsWithBackup() throws Exception {
        SavedServer original = new SavedServer("Local", ServerAddress.parse("localhost"), "FABRIC");
        ServerCatalog catalog = new ServerCatalog(temp.resolve("autoplug")); catalog.save(Collections.singletonList(original));
        Path vanilla = temp.resolve("servers.dat");
        MinecraftServersDat.Document document = MinecraftServersDat.empty().withServers(Arrays.asList(new SavedServer("Duplicate", ServerAddress.parse("localhost:25565"), "VANILLA"), new SavedServer("Other", ServerAddress.parse("example.org"), "VANILLA")));
        Files.write(vanilla, bytes(document)); assertEquals(1, catalog.importVanilla(vanilla)); assertEquals(2, catalog.list().size()); assertEquals("FABRIC", catalog.list().get(0).loaderHint);
        byte[] previous = Files.readAllBytes(vanilla); Path backup = catalog.exportVanilla(vanilla); assertArrayEquals(previous, Files.readAllBytes(backup)); assertEquals("Local", MinecraftServersDat.read(vanilla).servers().get(0).name);
        assertThrows(IOException.class, () -> catalog.save(Arrays.asList(original, original)));
    }
    @Test void actualExistingMineStatParsesProtocolAndStringAndStructuredUnicodeMotds() throws Exception {
        for (String description : new String[]{"\"§aCafé 🌲\"", "{\"text\":\"§aCafé\",\"extra\":[\" 🌲\",{\"text\":\"!\"}]}"}) {
            String json = "{\"version\":{\"name\":\"Paper 1.20.4\",\"protocol\":765},\"players\":{\"online\":3,\"max\":20},\"description\":" + description + "}";
            MineStat status = fixture(json.getBytes(StandardCharsets.UTF_8), 0, false);
            assertTrue(status.isServerUp()); assertEquals(765, status.getProtocol()); assertEquals(3, status.getCurrentPlayers()); assertEquals(20, status.getMaximumPlayers()); assertTrue(status.getStrippedMotd().startsWith("Café 🌲")); assertTrue(status.getLatency() >= 0);
        }
    }
    @Test void staleUiEditsRetainNewlyImportedServersAndRejectConflictingEdits() throws Exception {
        ServerCatalog catalog = new ServerCatalog(temp.resolve("autoplug")); SavedServer selected = new SavedServer("Local", ServerAddress.parse("localhost"), "VANILLA"); catalog.save(Collections.singletonList(selected));
        Path vanilla = temp.resolve("servers.dat"); Files.write(vanilla, bytes(MinecraftServersDat.empty().withServers(Collections.singletonList(new SavedServer("Imported", ServerAddress.parse("example.org"), "VANILLA")))));
        catalog.importVanilla(vanilla); SavedServer renamed = new SavedServer("Renamed", selected.address, "FABRIC"); catalog.edit(selected, renamed);
        assertEquals(2, catalog.list().size()); assertEquals("Imported", catalog.list().get(1).name);
        assertThrows(IOException.class, () -> catalog.edit(selected, new SavedServer("Stale", selected.address, "FORGE"))); assertThrows(IOException.class, () -> catalog.remove(selected));
        catalog.remove(renamed); assertEquals(1, catalog.list().size()); assertEquals("Imported", catalog.list().get(0).name);
    }
    @Test void mineStatRejectsOversizedTruncatedOrInvalidStatusAndBoundsReadTimeout() throws Exception {
        assertFalse(fixture(new byte[]{'{','}'}, 0, false).isServerUp());
        assertFalse(fixture(new byte[0], 1024 * 1024 + 1, false).isServerUp());
        assertFalse(fixture(new byte[0], 100, false).isServerUp());
        long start = System.nanoTime(); MineStat timeout = fixture(new byte[0], 0, true);
        assertEquals(MineStat.Retval.TIMEOUT, timeout.pingResult); assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3500);
    }
    @Test void autoResolutionRejectsProxyVersionRangesRatherThanGuessing() {
        SavedServer server = new SavedServer("Test", ServerAddress.parse("localhost"), "FORGE");
        assertEquals("1.20.4", new ServerStatus(server, true, "", "Paper 1.20.4", 765, 0, 20, 1, "").gameVersion());
        assertEquals("26.3", new ServerStatus(server, true, "", "26.3", 0, 0, 20, 1, "").gameVersion());
        assertEquals("26.4-snapshot-2", new ServerStatus(server, true, "", "26.4-snapshot-2", 0, 0, 20, 1, "").gameVersion());
        for (String version : new String[]{"1.20.2-pre1", "1.20.2-rc1", "26.1-pre-1", "26.1-rc-1"}) assertEquals(version, new ServerStatus(server, true, "", version, 0, 0, 20, 1, "").gameVersion());
        assertThrows(IllegalStateException.class, () -> new ServerStatus(server, true, "", "1.8 - 1.21.1", 0, 0, 20, 1, "").gameVersion());
        assertThrows(IllegalStateException.class, () -> new ServerStatus(server, false, "", "1.20.4", 0, 0, 20, 1, "").gameVersion());
    }
    private static byte[] bytes(MinecraftServersDat.Document document) throws Exception { ByteArrayOutputStream output = new ByteArrayOutputStream(); MinecraftServersDat.write(document, output); return output.toByteArray(); }
    private static MineStat fixture(byte[] json, int declaredSize, boolean delay) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> responder = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000); DataInputStream input = new DataInputStream(socket.getInputStream()); int length = varInt(input); byte[] handshake = new byte[length]; input.readFully(handshake); assertEquals(0, handshake[0]); assertEquals(1, varInt(input)); assertEquals(0, input.readUnsignedByte());
                    if (delay) { Thread.sleep(1400); return; }
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    if (declaredSize > 0) { varInt(output, declaredSize); output.flush(); return; }
                    ByteArrayOutputStream packet = new ByteArrayOutputStream(); DataOutputStream payload = new DataOutputStream(packet); varInt(payload, 0); varInt(payload, json.length); payload.write(json); varInt(output, packet.size()); output.write(packet.toByteArray()); output.flush();
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            try { MineStat result = new MineStat(InetAddress.getLoopbackAddress().getHostAddress(), server.getLocalPort(), 1, MineStat.Request.JSON); responder.get(4, TimeUnit.SECONDS); return result; }
            finally { executor.shutdownNow(); }
        }
    }
    private static int varInt(DataInputStream input) throws Exception { int value = 0; for (int i = 0; i < 5; i++) { int next = input.readUnsignedByte(); value |= (next & 127) << (7 * i); if ((next & 128) == 0) return value; } throw new IOException("Invalid varint"); }
    private static void varInt(DataOutputStream output, int value) throws Exception { do { int next = value & 127; value >>>= 7; output.writeByte(next | (value == 0 ? 0 : 128)); } while (value != 0); }
}
