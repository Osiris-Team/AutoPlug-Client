/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedProfilesTest {
    @TempDir Path directory;
    private Path jar(Path file, String contents) throws IOException {
        Files.createDirectories(file.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("fixture.txt")); zip.write(contents.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return file;
    }
    private ProfileUpdater.Resolution release(String project, String version, String filename, Path bytes) throws IOException {
        return new ProfileUpdater.Resolution(true, project, version, version, filename, bytes.toUri().toString(), SharedArtifactCache.hash(bytes, "SHA-1"), SharedArtifactCache.hash(bytes, "SHA-512"), Collections.emptyList());
    }
    private ProfileUpdater updater(ProfileStore store, ProfileUpdater.Resolver resolver) {
        return new ProfileUpdater(store.getCache(), resolver, (url, destination) -> Files.copy(Paths.get(java.net.URI.create(url)), destination));
    }
    @Test void persistsBothTypesTemplatesAndIsolatedDirectories() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug"));
        Profile mods = store.create("survival", Profile.Type.MODS, "1.20.1", "fabric", true);
        Profile plugins = store.create("hub", Profile.Type.PLUGINS, "1.21.1", "paper", false);
        assertEquals("FABRIC", mods.loader); assertTrue(mods.isTemplate);
        assertEquals(mods.directoryPath.resolve("mods"), mods.assetsDirectory());
        assertEquals(plugins.directoryPath.resolve("plugins"), plugins.assetsDirectory());
        assertEquals(2, new ProfileStore(store.getRoot()).list().size());
        assertFalse(store.setTemplate("survival", false).isTemplate);
        assertThrows(IllegalArgumentException.class, () -> store.create("../escape", Profile.Type.MODS, "1.20", "fabric", false));
        assertThrows(IOException.class, () -> store.create("hub", Profile.Type.MODS, "1.20", "fabric", false));
    }
    @Test void concurrentStoresPreserveIndependentDefaultsDuringActivationAndDeletion() throws Exception {
        ProfileStore first = new ProfileStore(directory.resolve("autoplug"));
        ProfileStore second = new ProfileStore(first.getRoot().resolve("unused").resolve(".."));
        first.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false);
        first.create("plugins", Profile.Type.PLUGINS, "1.20.1", "paper", false);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 32; round++) {
                Files.deleteIfExists(first.getRoot().resolve("active-profiles.properties"));
                CountDownLatch start = new CountDownLatch(1);
                Future<Profile> mods = workers.submit(() -> { start.await(); return first.setActive("mods"); });
                Future<Profile> plugins = workers.submit(() -> { start.await(); return second.setActive("plugins"); });
                start.countDown(); mods.get(10, TimeUnit.SECONDS); plugins.get(10, TimeUnit.SECONDS);
                assertEquals("mods", second.getActive(Profile.Type.MODS).id, "Lost MODS default in round " + round);
                assertEquals("plugins", first.getActive(Profile.Type.PLUGINS).id, "Lost PLUGINS default in round " + round);
            }
            CountDownLatch start = new CountDownLatch(1);
            Future<?> deletion = workers.submit(() -> { start.await(); first.delete("mods"); return null; });
            Future<Profile> activation = workers.submit(() -> { start.await(); return second.setActive("plugins"); });
            start.countDown(); deletion.get(10, TimeUnit.SECONDS); activation.get(10, TimeUnit.SECONDS);
            assertNull(second.getActive(Profile.Type.MODS));
            assertEquals("plugins", first.getActive(Profile.Type.PLUGINS).id);
        } finally { workers.shutdownNow(); }
    }
    @Test void corruptOrIncompleteMetadataFailsWithActionableIOException() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); store.create("profile", Profile.Type.MODS, "1.20.1", "fabric", false);
        Path metadata = store.getRoot().resolve("profiles/profile.properties");
        Files.write(metadata, Collections.singletonList("id=profile")); assertThrows(IOException.class, () -> store.get("profile"));
        Files.write(metadata, Arrays.asList("id=profile", "type=MODS", "gameVersion=1.20.1", "loader=fabric", "isTemplate=perhaps")); assertThrows(IOException.class, () -> store.get("profile"));
    }
    @Test void clonesContentThroughSharedCacheButCopiesMutableConfiguration() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug"));
        Profile source = store.create("base", Profile.Type.MODS, "1.21.1", "fabric", true);
        jar(source.assetsDirectory().resolve("mod.jar"), "newer");
        Files.createDirectories(source.directoryPath.resolve("config"));
        Files.write(source.directoryPath.resolve("config/settings.txt"), Collections.singletonList("source"));
        Files.write(source.directoryPath.resolve("credentials.json"), Collections.singletonList("must not clone"));
        Profile clone = store.clone("base", "older", "1.20.1");
        assertEquals("1.20.1", clone.gameVersion); assertFalse(clone.isTemplate);
        assertEquals(SharedArtifactCache.hash(source.assetsDirectory().resolve("mod.jar"), "SHA-256"), SharedArtifactCache.hash(clone.assetsDirectory().resolve("mod.jar"), "SHA-256"));
        try (java.util.stream.Stream<Path> files = Files.list(store.getCache().getRoot())) { assertEquals(1, files.count()); }
        Files.write(clone.directoryPath.resolve("config/settings.txt"), Collections.singletonList("clone"));
        assertEquals("source", Files.readAllLines(source.directoryPath.resolve("config/settings.txt")).get(0));
        assertFalse(Files.exists(clone.directoryPath.resolve("credentials.json")));
        store.delete("older"); assertTrue(Files.exists(source.assetsDirectory().resolve("mod.jar")));
    }
    @Test void migratesToOlderReleaseDespiteNewerInstalledFileTimestamp() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug"));
        Profile profile = store.create("older", Profile.Type.MODS, "1.20.1", "fabric", false);
        Path original = jar(profile.assetsDirectory().resolve("new.jar"), "new");
        Path replacement = jar(directory.resolve("older-release.jar"), "old");
        Files.setLastModifiedTime(original, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        ProfileUpdater updater = updater(store, (p, path) -> release("project", "1.0", "old.jar", replacement));
        ProfileUpdater.Plan plan = updater.check(profile);
        assertEquals(ProfileUpdater.Action.UPDATE, plan.changes.get(0).action);
        updater.apply(plan, false);
        assertFalse(Files.exists(original)); assertEquals(SharedArtifactCache.hash(replacement, "SHA-256"), SharedArtifactCache.hash(profile.assetsDirectory().resolve("old.jar"), "SHA-256"));
    }
    @Test void explicitDisablingConfirmationAndLaterReEnable() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug"));
        Profile profile = store.create("mods", Profile.Type.MODS, "1.21.1", "fabric", false);
        Path original = jar(profile.assetsDirectory().resolve("unavailable.jar"), "local");
        ProfileUpdater unavailable = updater(store, (p, path) -> ProfileUpdater.Resolution.unsupported("project"));
        ProfileUpdater.Plan plan = unavailable.check(profile);
        assertEquals(1, plan.disabled().size()); assertThrows(IOException.class, () -> unavailable.apply(plan, false)); assertTrue(Files.exists(original));
        unavailable.apply(plan, true); assertFalse(Files.exists(original)); assertTrue(Files.exists(original.resolveSibling("unavailable.jar.disabled")));
        Path restored = jar(directory.resolve("restored.jar"), "compatible");
        ProfileUpdater available = updater(store, (p, path) -> release("project", "2", "available.jar", restored));
        ProfileUpdater.Plan restoredPlan = available.check(profile);
        assertEquals(ProfileUpdater.Action.ENABLE, restoredPlan.changes.get(0).action);
        available.apply(restoredPlan, false); assertFalse(Files.exists(original.resolveSibling("unavailable.jar.disabled"))); assertTrue(Files.exists(profile.assetsDirectory().resolve("available.jar")));
    }
    @Test void apiFailureDoesNotBecomeUnsupportedOrMutateFiles() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("plugins", Profile.Type.PLUGINS, "1.20.1", "paper", false);
        Path original = jar(profile.assetsDirectory().resolve("plugin.jar"), "original"); String hash = SharedArtifactCache.hash(original, "SHA-256");
        ProfileUpdater updater = updater(store, (p, path) -> { throw new java.net.SocketTimeoutException("HTTP timeout"); });
        ProfileUpdater.Plan plan = updater.check(profile);
        assertEquals(1, plan.errors.size()); assertTrue(plan.disabled().isEmpty());
        assertThrows(IOException.class, () -> updater.apply(plan, true)); assertEquals(hash, SharedArtifactCache.hash(original, "SHA-256"));
    }
    @Test void failedSecondDownloadLeavesEveryOriginalIntact() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "forge", false);
        Path first = jar(profile.assetsDirectory().resolve("first.jar"), "first-old"), second = jar(profile.assetsDirectory().resolve("second.jar"), "second-old");
        Path replacement = jar(directory.resolve("release.jar"), "new"); String firstHash = SharedArtifactCache.hash(first, "SHA-256"), secondHash = SharedArtifactCache.hash(second, "SHA-256");
        int[] downloads = {0};
        ProfileUpdater updater = new ProfileUpdater(store.getCache(), (p, path) -> release(path.getFileName().toString(), "2", path.getFileName().toString(), replacement), (url, destination) -> { if (++downloads[0] == 2) throw new IOException("Download failed"); Files.copy(replacement, destination); });
        ProfileUpdater.Plan plan = updater.check(profile); assertThrows(IOException.class, () -> updater.apply(plan, false));
        assertEquals(firstHash, SharedArtifactCache.hash(first, "SHA-256")); assertEquals(secondHash, SharedArtifactCache.hash(second, "SHA-256"));
    }
    @Test void rejectsChangedFilesAndUnreviewedDestinationCollision() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false);
        Path original = jar(profile.assetsDirectory().resolve("original.jar"), "original"), replacement = jar(directory.resolve("release.jar"), "new");
        ProfileUpdater updater = updater(store, (p, path) -> release("project", "2", "target.jar", replacement));
        ProfileUpdater.Plan plan = updater.check(profile); jar(original, "changed-after-check"); assertThrows(IOException.class, () -> updater.apply(plan, false));
        ProfileUpdater.Plan updated = updater.check(profile); jar(profile.assetsDirectory().resolve("target.jar"), "unreviewed-new-file"); assertThrows(IOException.class, () -> updater.apply(updated, false));
    }
    @Test void checksumMismatchRejectsUntrustedDownloadBeforeReplacement() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false);
        Path original = jar(profile.assetsDirectory().resolve("mod.jar"), "original"), expected = jar(directory.resolve("expected.jar"), "expected"), wrong = jar(directory.resolve("wrong.jar"), "wrong");
        String hash = SharedArtifactCache.hash(original, "SHA-256");
        ProfileUpdater updater = new ProfileUpdater(store.getCache(), (p, path) -> release("project", "2", "mod.jar", expected), (url, destination) -> Files.copy(wrong, destination));
        ProfileUpdater.Plan plan = updater.check(profile); assertThrows(IOException.class, () -> updater.apply(plan, false)); assertEquals(hash, SharedArtifactCache.hash(original, "SHA-256"));
    }
    @Test void cliExtendsLegacyCommandsAndMigratesPluginClone() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile source = store.create("hub", Profile.Type.PLUGINS, "1.21.1", "paper", true);
        Path original = jar(source.assetsDirectory().resolve("plugin.jar"), "old"); Path replacement = jar(directory.resolve("new.jar"), "new");
        ProfileUpdater updater = updater(store, (p, path) -> release("plugin", "1", "plugin.jar", replacement)); ProfileCommands commands = new ProfileCommands(store, updater);
        String originalHash = SharedArtifactCache.hash(original, "SHA-256");
        List<String> output = new ArrayList<>(); assertFalse(commands.handle(".check plugins", output::add));
        assertTrue(commands.handle(".check plugins --profile hub", output::add)); assertEquals(originalHash, SharedArtifactCache.hash(original, "SHA-256"));
        assertTrue(commands.handle(".profiles clone hub 1.20.1 older", output::add));
        assertEquals(SharedArtifactCache.hash(replacement, "SHA-256"), SharedArtifactCache.hash(store.get("older").assetsDirectory().resolve("plugin.jar"), "SHA-256"));
        assertTrue(output.stream().anyMatch(text -> text.contains("Cloned and migrated")));
        assertEquals("1.21.1", store.get("hub").gameVersion);
    }
    @Test void cliCloneWithoutDisableConfirmationIsCleanedUp() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile source = store.create("base", Profile.Type.MODS, "1.20.1", "fabric", true); jar(source.assetsDirectory().resolve("mod.jar"), "old");
        ProfileCommands commands = new ProfileCommands(store, updater(store, (p, path) -> ProfileUpdater.Resolution.unsupported("project"))); List<String> output = new ArrayList<>();
        commands.handle(".profiles clone base 1.21.1 clone", output::add);
        assertEquals(1, store.list().size()); assertTrue(Files.exists(source.assetsDirectory().resolve("mod.jar"))); assertTrue(output.stream().anyMatch(text -> text.contains("Confirmation required")));
        commands.handle(".profiles clone base 1.21.1 clone --confirm-disabling", output::add);
        assertEquals(2, store.list().size()); assertTrue(Files.exists(store.get("clone").assetsDirectory().resolve("mod.jar.disabled")));
    }
    @Test void recoversPreparedInterruptedTransactionBeforeApplying() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false); Path original = jar(profile.assetsDirectory().resolve("original.jar"), "original");
        ProfileUpdater updater = updater(store, (p, path) -> ProfileUpdater.Resolution.unsupported("project")); ProfileUpdater.Plan plan = updater.check(profile);
        Path transaction = profile.directoryPath.resolve(".autoplug-transactions/update-interrupted"); Files.createDirectories(transaction); Files.copy(original, transaction.resolve("0.backup"));
        Path destination = original.resolveSibling("original.jar.disabled"); Files.move(original, destination);
        Properties journal = new Properties(); journal.setProperty("state", "PREPARED"); journal.setProperty("count", "1"); journal.setProperty("0.original", "mods/original.jar"); journal.setProperty("0.destination", "mods/original.jar.disabled");
        try (OutputStream output = Files.newOutputStream(transaction.resolve("journal.properties"))) { journal.store(output, "fixture"); }
        updater.apply(plan, true); assertTrue(Files.exists(destination)); assertFalse(Files.exists(transaction));
    }
    @Test void installsRequiredDependencyAndPreservesProviderIdentityAfterRename() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false);
        jar(profile.assetsDirectory().resolve("old.jar"), "old"); Path main = jar(directory.resolve("main.jar"), "main"), dependency = jar(directory.resolve("dependency.jar"), "dependency");
        ProfileUpdater.Resolver resolver = new ProfileUpdater.Resolver() {
            @Override public ProfileUpdater.Resolution resolve(Profile p, Path installed) throws Exception {
                ProfileUpdater.Resolution base = release("main", "2", "new.jar", main);
                return new ProfileUpdater.Resolution(true, base.projectId, base.versionId, base.version, base.fileName, base.url, base.sha1, base.sha512, Collections.singletonList(new ProfileUpdater.Dependency("dependency", "pinned")));
            }
            @Override public ProfileUpdater.Resolution dependency(Profile p, ProfileUpdater.Dependency requested) throws Exception { assertEquals("pinned", requested.versionId); return release("dependency", "pinned", "dependency.jar", dependency); }
        };
        ProfileUpdater updater = updater(store, resolver); ProfileUpdater.Plan plan = updater.check(profile);
        assertTrue(plan.errors.isEmpty()); assertEquals(2, plan.changes.size()); assertEquals(ProfileUpdater.Action.ADD, plan.changes.get(1).action);
        updater.apply(plan, false); assertTrue(Files.exists(profile.assetsDirectory().resolve("dependency.jar"))); assertFalse(Files.exists(profile.assetsDirectory().resolve("old.jar")));
        Properties identities = new Properties(); try (InputStream input = Files.newInputStream(profile.directoryPath.resolve("asset-sources.properties"))) { identities.load(input); }
        assertEquals("main", identities.getProperty("new.jar.modrinth")); assertEquals("dependency", identities.getProperty("dependency.jar.modrinth"));
    }
    @Test void rejectsMutuallyIncompatibleProjectsBeforeAnyMutation() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false);
        Path one = jar(profile.assetsDirectory().resolve("one.jar"), "one"), two = jar(profile.assetsDirectory().resolve("two.jar"), "two");
        ProfileUpdater updater = updater(store, (p, installed) -> {
            ProfileUpdater.Resolution base = release(installed.equals(one) ? "one" : "two", "same", installed.getFileName().toString(), installed);
            return new ProfileUpdater.Resolution(true, base.projectId, base.versionId, base.version, base.fileName, base.url, base.sha1, base.sha512, null, Collections.emptyList(), installed.equals(one) ? Collections.singletonList("two") : Collections.emptyList());
        });
        ProfileUpdater.Plan plan = updater.check(profile); assertEquals(1, plan.errors.size()); assertThrows(IOException.class, () -> updater.apply(plan, true)); assertTrue(Files.exists(one)); assertTrue(Files.exists(two));
    }
    @Test void detectsCacheCorruptionAndRejectsMutationDuringStaging() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false);
        Path original = jar(profile.assetsDirectory().resolve("mod.jar"), "original"), replacement = jar(directory.resolve("replacement.jar"), "replacement");
        Path cached = store.getCache().store(original); Files.write(cached, Collections.singletonList("corrupt")); assertThrows(IOException.class, () -> store.getCache().link(cached, directory.resolve("link.jar")));
        ProfileUpdater updater = new ProfileUpdater(store.getCache(), (p, installed) -> release("project", "2", "mod.jar", replacement), (url, output) -> { Files.copy(replacement, output); jar(original, "owner edited while download active"); });
        ProfileUpdater.Plan plan = updater.check(profile); assertThrows(IOException.class, () -> updater.apply(plan, false));
        assertNotEquals(SharedArtifactCache.hash(replacement, "SHA-256"), SharedArtifactCache.hash(original, "SHA-256"));
    }
    @Test void recoveryRestoresOtherOriginalsEvenWhenOneDestinationCannotBeRemoved() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false);
        Path one = jar(profile.assetsDirectory().resolve("one.jar"), "one"), two = jar(profile.assetsDirectory().resolve("two.jar"), "two");
        String hash = SharedArtifactCache.hash(one, "SHA-256"); ProfileUpdater updater = updater(store, (p, path) -> ProfileUpdater.Resolution.unsupported(path.getFileName().toString())); ProfileUpdater.Plan plan = updater.check(profile);
        Path transaction = profile.directoryPath.resolve(".autoplug-transactions/update-interrupted"); Files.createDirectories(transaction); Files.copy(one, transaction.resolve("0.backup")); Files.copy(two, transaction.resolve("1.backup")); Files.move(one, one.resolveSibling("one.jar.disabled"));
        Path blocked = two.resolveSibling("two.jar.disabled"); Files.createDirectory(blocked); Files.write(blocked.resolve("block-removal"), Collections.singletonList("fixture"));
        Properties journal = new Properties(); journal.setProperty("state", "PREPARED"); journal.setProperty("count", "2");
        journal.setProperty("0.original", "mods/one.jar"); journal.setProperty("0.destination", "mods/one.jar.disabled"); journal.setProperty("1.original", "mods/two.jar"); journal.setProperty("1.destination", "mods/two.jar.disabled");
        try (OutputStream output = Files.newOutputStream(transaction.resolve("journal.properties"))) { journal.store(output, "fixture"); }
        assertThrows(IOException.class, () -> updater.apply(plan, true)); assertEquals(hash, SharedArtifactCache.hash(one, "SHA-256")); assertTrue(Files.exists(two)); assertTrue(Files.exists(transaction.resolve("0.backup")));
    }
    @Test void newlyAppearedDestinationDuringDownloadIsNotOverwritten() throws Exception {
        ProfileStore store = new ProfileStore(directory.resolve("autoplug")); Profile profile = store.create("mods", Profile.Type.MODS, "1.20.1", "fabric", false);
        Path original = jar(profile.assetsDirectory().resolve("old.jar"), "old"), release = jar(directory.resolve("release.jar"), "release"); String originalHash = SharedArtifactCache.hash(original, "SHA-256");
        Path destination = profile.assetsDirectory().resolve("new.jar");
        ProfileUpdater updater = new ProfileUpdater(store.getCache(), (p, path) -> release("project", "2", "new.jar", release), (url, download) -> { Files.copy(release, download); jar(destination, "owner-created"); });
        ProfileUpdater.Plan plan = updater.check(profile); assertThrows(IOException.class, () -> updater.apply(plan, false)); assertEquals(originalHash, SharedArtifactCache.hash(original, "SHA-256")); assertNotEquals(SharedArtifactCache.hash(release, "SHA-256"), SharedArtifactCache.hash(destination, "SHA-256"));
    }
}
