package com.osiris.autoplug.client.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ProfileStoreTest {
    @TempDir Path temp;
    @Test void cloneSharesJarsButIsolatesMutableConfiguration() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles"));
        Profile source = store.create("Original", "1.20.1", "FABRIC", ProfileType.MODS);
        Files.write(source.getDirectory().resolve("mods/test.jar"), new byte[]{1, 2, 3});
        Files.createDirectories(source.getDirectory().resolve("config"));
        Files.write(source.getDirectory().resolve("config/a.txt"), "before".getBytes(StandardCharsets.UTF_8));
        Profile target = store.cloneProfile(source.id, "1.21.1");
        assertTrue(target.migrationPending);
        Path targetJar = target.getDirectory().resolve("mods/test.jar");
        Path cached = store.getCache().store(source.getDirectory().resolve("mods/test.jar"));
        assertTrue(Files.isSameFile(cached, targetJar));
        Files.write(target.getDirectory().resolve("config/a.txt"), "after".getBytes(StandardCharsets.UTF_8));
        assertEquals("before", new String(Files.readAllBytes(source.getDirectory().resolve("config/a.txt")), StandardCharsets.UTF_8));
        Path replacement = temp.resolve("replacement.jar"); Files.write(replacement, new byte[]{4, 5, 6});
        store.getCache().link(store.getCache().store(replacement), targetJar);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(cached));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(source.getDirectory().resolve("mods/test.jar")));
        assertNotEquals(source.id, store.get(target.id).id);
    }
    @Test void lockPreventsMutatingAnActiveProfileAndDeleteIsRecoverable() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles"));
        Profile p = store.create("Server", "1.21.1", "PAPER", ProfileType.PLUGINS);
        try (ProfileLease lease = new ProfileLease(p.getDirectory())) {
            assertThrows(java.io.IOException.class, () -> store.delete(p.id));
            assertThrows(java.io.IOException.class, () -> store.cloneProfile(p.id, "1.20.1"));
        }
        store.delete(p.id);
        assertTrue(store.list().isEmpty());
        try (java.util.stream.Stream<Path> files = Files.list(temp.resolve("trash"))) { assertEquals(1, files.count()); }
    }
    @Test void rejectsTraversalAndMismatchedMetadata() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles"));
        assertThrows(java.io.IOException.class, () -> store.get("../outside"));
        Profile p = store.create("Client", "1.21.1", "VANILLA", ProfileType.MODS);
        assertThrows(java.io.IOException.class, () -> new ProfileCollection().resolve(p, "../../outside.jar"));
        p.id = "other";
        new JsonFiles().write(store.getRoot().resolve(p.getDirectory().getFileName()).resolve("profile.json"), p);
        assertThrows(java.io.IOException.class, () -> store.get(p.getDirectory().getFileName().toString()));
    }
    @Test void cloningNeverCopiesTransientCredentialsOrNativeRuntimeFiles() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); Profile p = store.create("Client", "1.21.1", "VANILLA", ProfileType.MODS);
        Files.createDirectories(p.getDirectory().resolve(".autoplug/natives"));
        Files.write(p.getDirectory().resolve(".autoplug/launch-private.args"), "test-token".getBytes(StandardCharsets.UTF_8));
        Files.write(p.getDirectory().resolve(".autoplug/natives/test.dll"), new byte[]{1});
        Files.write(p.getDirectory().resolve("accounts.json"), "test-secret".getBytes(StandardCharsets.UTF_8));
        Profile copy = store.cloneProfile(p.id, p.gameVersion);
        assertFalse(Files.exists(copy.getDirectory().resolve(".autoplug")));
        assertFalse(Files.exists(copy.getDirectory().resolve("accounts.json")));
    }
}
