package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileUpdatesTest {
    @TempDir Path temp;
    private void jar(Path path, String version) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("fabric.mod.json"));
            out.write(("{\"id\":\"example\",\"name\":\"Example\",\"version\":\"" + version + "\",\"authors\":[\"Test\"]}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
    private Profile profile(ProfileStore store) throws Exception {
        Profile p = store.create("Test", "1.20.1", "FABRIC", ProfileType.MODS);
        jar(p.getDirectory().resolve("mods/example.jar"), "2.0"); return p;
    }
    private SearchResult release(SearchResult.Type type) { return new SearchResult(null, type, "1.0", "https://example.invalid/mod.jar", ".jar", null, null, false); }
    @Test void checkNeverDownloadsAndDowngradeReplacesOnlyTheSelectedProfile() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); Profile p = profile(store);
        Profile clone = store.cloneProfile(p.id, "1.19.4"); AtomicInteger downloads = new AtomicInteger();
        ProfileUpdates updates = new ProfileUpdates(store, (pr, entry, file) -> release(SearchResult.Type.UPDATE_AVAILABLE), (r, dest) -> { downloads.incrementAndGet(); jar(dest, "1.0"); });
        byte[] original = Files.readAllBytes(p.getDirectory().resolve("mods/example.jar"));
        ProfileUpdates.Plan plan = updates.check(clone.id);
        assertEquals(0, downloads.get()); assertEquals(ProfileUpdates.Status.REPLACE, plan.changes.get(0).status);
        updates.apply(plan, true);
        assertEquals(1, downloads.get()); assertFalse(store.get(clone.id).migrationPending);
        assertArrayEquals(original, Files.readAllBytes(p.getDirectory().resolve("mods/example.jar")));
        assertFalse(java.util.Arrays.equals(original, Files.readAllBytes(clone.getDirectory().resolve("mods/example.jar"))));
    }
    @Test void providerFailureDoesNotDisableOrChangeInstalledJars() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); Profile p = profile(store); p.migrationPending = true; store.save(p);
        ProfileUpdates updates = new ProfileUpdates(store, (pr, entry, file) -> release(SearchResult.Type.API_ERROR), (r, dest) -> fail("No download expected"));
        assertThrows(IOException.class, () -> updates.update(p.id, true));
        assertTrue(Files.exists(p.getDirectory().resolve("mods/example.jar")));
        assertFalse(Files.exists(p.getDirectory().resolve("mods/example.jar.disabled")));
        assertTrue(store.get(p.id).migrationPending);
    }
    @Test void unverifiedMigrationNeedsConfirmationAndDisablesWithBackup() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); Profile p = profile(store); p.migrationPending = true; store.save(p);
        ProfileUpdates updates = new ProfileUpdates(store, (pr, entry, file) -> null, (r, dest) -> fail("No download expected"));
        assertThrows(IOException.class, () -> updates.update(p.id, false));
        updates.update(p.id, true);
        assertFalse(Files.exists(p.getDirectory().resolve("mods/example.jar")));
        assertTrue(Files.exists(p.getDirectory().resolve("mods/example.jar.disabled")));
        assertFalse(store.get(p.id).migrationPending);
    }
    @Test void corruptDownloadLeavesOriginalIntact() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); Profile p = profile(store);
        byte[] original = Files.readAllBytes(p.getDirectory().resolve("mods/example.jar"));
        ProfileUpdates updates = new ProfileUpdates(store, (pr, entry, file) -> { SearchResult r = release(SearchResult.Type.UPDATE_AVAILABLE); r.sha1 = "0000000000000000000000000000000000000000"; return r; }, (r, dest) -> jar(dest, "bad"));
        assertThrows(IOException.class, () -> updates.update(p.id, true));
        assertArrayEquals(original, Files.readAllBytes(p.getDirectory().resolve("mods/example.jar")));
    }
    @Test void stalePlanCannotOverwriteNewerLocalFiles() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); Profile p = profile(store);
        ProfileUpdates updates = new ProfileUpdates(store, (pr, entry, file) -> release(SearchResult.Type.UPDATE_AVAILABLE), (r, dest) -> fail("No download expected"));
        ProfileUpdates.Plan plan = updates.check(p.id); jar(p.getDirectory().resolve("mods/example.jar"), "changed");
        assertThrows(IOException.class, () -> updates.apply(plan, true));
    }
    @Test void jarAddedAfterReviewCannotEscapeMigration() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); Profile p = profile(store); p.migrationPending = true; store.save(p);
        ProfileUpdates updates = new ProfileUpdates(store, (pr, entry, file) -> release(SearchResult.Type.UP_TO_DATE), (r, dest) -> fail("No download expected"));
        ProfileUpdates.Plan plan = updates.check(p.id); jar(p.getDirectory().resolve("mods/unreviewed.jar"), "1.0");
        assertThrows(IOException.class, () -> updates.apply(plan, true));
        assertTrue(store.get(p.id).migrationPending);
    }
    @Test void providerIdentityChangedAfterReviewRequiresNewPlan() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); Profile p = profile(store);
        ProfileUpdates updates = new ProfileUpdates(store, (pr, entry, file) -> release(SearchResult.Type.UPDATE_AVAILABLE), (r, dest) -> fail("No download expected"));
        ProfileUpdates.Plan plan = updates.check(p.id);
        ProfileCollection collection = new ProfileCollection().scan(p); collection.entries.get(0).modrinthId = "different"; collection.save(p);
        assertThrows(IOException.class, () -> updates.apply(plan, true));
    }
    @Test void failedMetadataCommitRestoresBothJarAndMetadata() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean failSave = new java.util.concurrent.atomic.AtomicBoolean();
        ProfileStore store = new ProfileStore(temp.resolve("profiles")) {
            @Override public void save(Profile p) throws IOException { if (failSave.get()) throw new IOException("Simulated disk failure"); super.save(p); }
        };
        Profile p = profile(store); ProfileCollection collection = new ProfileCollection().scan(p); collection.save(p);
        byte[] originalJar = Files.readAllBytes(p.getDirectory().resolve("mods/example.jar"));
        byte[] originalMetadata = Files.readAllBytes(p.getDirectory().resolve("collection.json"));
        ProfileUpdates updates = new ProfileUpdates(store, (pr, entry, file) -> release(SearchResult.Type.UPDATE_AVAILABLE), (r, dest) -> jar(dest, "1.0"));
        ProfileUpdates.Plan plan = updates.check(p.id); failSave.set(true);
        assertThrows(IOException.class, () -> updates.apply(plan, true));
        assertArrayEquals(originalJar, Files.readAllBytes(p.getDirectory().resolve("mods/example.jar")));
        assertArrayEquals(originalMetadata, Files.readAllBytes(p.getDirectory().resolve("collection.json")));
    }
}
