package com.osiris.autoplug.client.mc.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ClientStorageAndServersTest {
    @TempDir Path temporary;

    @Test void validatesIpv6StructureWithoutResolvingAnyHost() {
        for (String invalid : new String[]{"[::::]", "[1::2::3]", "[:1::]", "[::1:]", "[1:2:3]", "[1:2:3:4:5:6:7:8:9]", "[::ffff:300.1.2.3]", "[localhost]", "a..invalid", "-bad.invalid", "bad-.invalid", "999.1.2.3"}) {
            assertThrows(IllegalArgumentException.class, () -> new ServerAddress(invalid), invalid);
        }
        assertEquals("[::1]:25565", new ServerAddress("::1").toString());
        assertEquals("[2001:db8::1]:25577", new ServerAddress("[2001:DB8::1]:25577").toString());
        assertEquals("[::ffff:192.0.2.1]:25565", new ServerAddress("::ffff:192.0.2.1").toString());
        assertEquals("example.invalid:25565", new ServerAddress("EXAMPLE.invalid.").toString());
        assertEquals("xn--espaol-zwa.invalid:25565", new ServerAddress("español.invalid").toString());
    }

    @Test void settingsRoundTripRejectsUnknownSecretKeysAndInvalidOfflineFlags() throws Exception {
        Path root = temporary.toRealPath(); ClientSettings settings = new ClientSettings(root);
        settings.set("username", "Fixture"); settings.set("offlineFixtureMode", "false"); settings.save();
        assertEquals("Fixture", new ClientSettings(root).get("username", ""));
        assertThrows(IllegalArgumentException.class, () -> settings.set("accessToken", "fixture-only"));
        assertThrows(IllegalArgumentException.class, () -> settings.set("offlineFixtureMode", "yes"));
        assertThrows(IllegalArgumentException.class, () -> settings.set("username", "bad name"));
        Files.write(root.resolve("client-settings.properties"), "accessToken=fixture-not-a-secret\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> new ClientSettings(root));
    }

    @Test void storageRejectsLinkedFileAndParentWithoutWritingThroughThem() throws Exception {
        Path root = temporary.toRealPath(), outside = Files.createDirectory(root.resolve("outside"));
        Path protectedFile = outside.resolve("original.properties"); Files.write(protectedFile, "original=yes\n".getBytes(StandardCharsets.US_ASCII));
        Path linked = root.resolve("linked.properties"); Files.createSymbolicLink(linked, protectedFile);
        ClientStorage storage = new ClientStorage(); Properties values = new Properties(); values.setProperty("new", "value");
        assertThrows(IOException.class, () -> storage.read(linked));
        assertThrows(IOException.class, () -> storage.write(linked, values));
        Path linkedParent = root.resolve("parent-link"); Files.createSymbolicLink(linkedParent, outside);
        assertThrows(IOException.class, () -> storage.write(linkedParent.resolve("new-directory/file.properties"), values));
        assertFalse(Files.exists(outside.resolve("new-directory")));
        assertEquals("original=yes\n", new String(Files.readAllBytes(protectedFile), StandardCharsets.US_ASCII));
    }

    @Test void oversizedOrMalformedSettingsFailAndPreservePreviousFile() throws Exception {
        Path root = temporary.toRealPath(), file = root.resolve("settings.properties");
        ClientStorage storage = new ClientStorage(); Properties first = new Properties(); first.setProperty("first", "kept"); storage.write(file, first);
        byte[] before = Files.readAllBytes(file);
        Properties huge = new Properties(); huge.setProperty("huge", new String(new char[8 * 1024 * 1024 + 1]).replace('\0', 'x'));
        assertThrows(IOException.class, () -> storage.write(file, huge)); assertArrayEquals(before, Files.readAllBytes(file));
        Files.write(file, new byte[8 * 1024 * 1024 + 1]); assertThrows(IOException.class, () -> storage.read(file));
        Files.write(file, "bad=\\uXYZ1\n".getBytes(StandardCharsets.US_ASCII)); assertThrows(IOException.class, () -> storage.read(file));
        try (java.util.stream.Stream<Path> files = Files.list(root)) { assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".settings-"))); }
    }

    @Test void savedServerCrudPersistsCanonicalAddressesAndRejectsDuplicateIds() throws Exception {
        Path root = temporary.toRealPath(); SavedServerRepository servers = new SavedServerRepository(root);
        SavedServerRepository.SavedServer first = servers.add("First", "EXAMPLE.invalid", "VANILLA");
        assertEquals("example.invalid:25565", servers.list().get(0).getAddress());
        servers.update(first.getId(), "Renamed", "localhost:25566", "FABRIC");
        assertEquals("FABRIC", new SavedServerRepository(root).list().get(0).getLoader());
        Properties corrupt = new ClientStorage().read(root.resolve("saved-servers.properties"));
        corrupt.setProperty("count", "2");
        for (String field : new String[]{"id", "name", "address", "loader"}) corrupt.setProperty("server.1." + field, corrupt.getProperty("server.0." + field));
        new ClientStorage().write(root.resolve("saved-servers.properties"), corrupt);
        assertThrows(IOException.class, servers::list);
    }

    @Test void vanillaImportIsAtomicDeduplicatedAndNeverChangesTheOriginal() throws Exception {
        Path root = temporary.toRealPath(), vanilla = root.resolve("synthetic-servers.dat");
        SavedServerRepository servers = new SavedServerRepository(root);
        servers.add("Existing", "example.invalid", "VANILLA");
        byte[] original = nbt(new String[][]{{"Duplicate", "EXAMPLE.invalid"}, {"New", "new.invalid:25570"}});
        Files.write(vanilla, original);
        assertEquals(1, servers.importVanilla(vanilla, "VANILLA")); assertEquals(0, servers.importVanilla(vanilla, "VANILLA"));
        assertArrayEquals(original, Files.readAllBytes(vanilla));
        byte[] savedBefore = Files.readAllBytes(root.resolve("saved-servers.properties"));
        Files.write(vanilla, nbt(new String[][]{{"Would be added", "third.invalid"}, {"Invalid", "[::::]"}}));
        assertThrows(IOException.class, () -> servers.importVanilla(vanilla, "VANILLA"));
        assertArrayEquals(savedBefore, Files.readAllBytes(root.resolve("saved-servers.properties")));
    }

    private static byte[] nbt(String[][] entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(9); out.writeUTF("servers"); out.writeByte(10); out.writeInt(entries.length);
            for (String[] entry : entries) {
                out.writeByte(8); out.writeUTF("name"); out.writeUTF(entry[0]);
                out.writeByte(8); out.writeUTF("ip"); out.writeUTF(entry[1]); out.writeByte(0);
            }
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }
}
