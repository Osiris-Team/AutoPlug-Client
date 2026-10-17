package com.osiris.autoplug.client.mc.update;

import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileRepository;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProfileUpdateServiceTest {
    @TempDir Path temporary;
    ProfileRepository repository;
    SharedArtifactCache cache;
    Profile profile;
    Path oldJar, newJar;

    @BeforeEach void setup() throws Exception {
        repository = new ProfileRepository(temporary.resolve("data"));
        cache = new SharedArtifactCache(temporary.resolve("cache"));
        profile = repository.create("client", ProfileType.MODS, "1.21", "fabric", false);
        oldJar = jar(profile.getAssetsDirectory().resolve("example.jar"), "old");
        newJar = jar(temporary.resolve("compatible.jar"), "older-compatible-release");
    }

    @Test void checkIsReadOnlyAndPassesTheExactDowngradeTarget() throws Exception {
        String metadata = read(profile.getDirectory().resolve("profile.properties"));
        String hash = SharedArtifactCache.sha256(oldJar);
        ProfileUpdateService service = service(new AtomicInteger());
        ProfileUpdateService.Plan plan = service.check("client", "1.20.1");
        assertEquals("1.20.1", plan.getTargetVersion());
        assertEquals(ProfileUpdateService.Action.REPLACE, plan.getChanges().get(0).getAction());
        assertEquals(metadata, read(profile.getDirectory().resolve("profile.properties")));
        assertEquals(hash, SharedArtifactCache.sha256(oldJar));
        try (java.util.stream.Stream<Path> entries = Files.list(cache.getDirectory())) { assertEquals(0, entries.count()); }
    }

    @Test void updateCanDowngradeAndCachesImmutableArtifactsAcrossProfiles() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        Profile second = repository.cloneProfile("client", "second");
        ProfileUpdateService service = service(downloads);
        Profile firstUpdated = service.apply(service.check("client", "1.20.1"), false);
        Profile secondUpdated = service.apply(service.check("second", "1.20.1"), false);
        assertEquals("1.20.1", firstUpdated.getGameVersion());
        assertEquals("1.20.1", secondUpdated.getGameVersion());
        assertEquals(1, downloads.get(), "Identical verified artifacts should not be downloaded twice");
        assertEquals(SharedArtifactCache.sha256(newJar), SharedArtifactCache.sha256(firstUpdated.getAssetsDirectory().resolve("example.jar")));
        assertEquals(SharedArtifactCache.sha256(newJar), SharedArtifactCache.sha256(second.getAssetsDirectory().resolve("example.jar")));
        assertTrue(Files.isSameFile(firstUpdated.getAssetsDirectory().resolve("example.jar"),
                cache.getDirectory().resolve(SharedArtifactCache.sha256(newJar) + ".jar")), "This local filesystem supports hardlinks");
    }

    @Test void incompatibilityNeedsApprovalAndRenamesWithoutDestroyingTheJar() throws Exception {
        String hash = SharedArtifactCache.sha256(oldJar);
        ProfileUpdateService service = serviceWith(ProfileUpdateService.Decision.incompatible("No target release"));
        ProfileUpdateService.Plan plan = service.check("client", "1.20.1");
        assertEquals(1, plan.getDisabledAssets().size());
        assertTrue(plan.getSummary().contains("example.jar"));
        assertThrows(IOException.class, () -> service.apply(plan, false));
        assertTrue(Files.exists(oldJar));
        assertEquals("1.21", repository.require("client").getGameVersion());
        service.apply(plan, true);
        assertFalse(Files.exists(oldJar));
        assertEquals(hash, SharedArtifactCache.sha256(oldJar.resolveSibling("example.jar.disabled")));
    }

    @Test void providerErrorsCannotDisableOrChangeVersion() throws Exception {
        ProfileUpdateService service = serviceWith(ProfileUpdateService.Decision.error("Temporary provider outage"));
        ProfileUpdateService.Plan plan = service.check("client", "1.20.1");
        assertFalse(plan.canApply());
        assertTrue(plan.getDisabledAssets().isEmpty());
        assertThrows(IOException.class, () -> service.apply(plan, true));
        assertTrue(Files.exists(oldJar));
        assertEquals("1.21", repository.require("client").getGameVersion());
    }

    @Test void staleApprovalRejectsChangedConfigurationBeforeDownloading() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        ProfileUpdateService service = service(downloads);
        ProfileUpdateService.Plan plan = service.check("client", "1.20.1");
        Files.write(profile.getConfigDirectory().resolve("settings.txt"), "changed".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> service.apply(plan, false));
        assertEquals(0, downloads.get());
        assertEquals("1.21", repository.require("client").getGameVersion());
    }

    @Test void failedDownloadLeavesAllOriginalAssetsAndMetadataUntouched() throws Exception {
        ProfileUpdateService service = new ProfileUpdateService(repository, cache, new ProfileUpdateService.Engine() {
            public ProfileUpdateService.Decision resolve(Profile p, Path path, String version) {
                return ProfileUpdateService.Decision.replace("https://example.invalid/fixture.jar", null, "fixture");
            }
            public void download(ProfileUpdateService.Decision d, Path target) throws IOException {
                Files.write(target, new byte[]{1, 2}); throw new IOException("Synthetic connection failure");
            }
        });
        String hash = SharedArtifactCache.sha256(oldJar);
        assertThrows(IOException.class, () -> service.apply(service.check("client", "1.20.1"), false));
        assertEquals(hash, SharedArtifactCache.sha256(oldJar));
        assertEquals("1.21", repository.require("client").getGameVersion());
    }

    @Test void templateCloneIsOnlyCreatedAtApplyAndCopiesMutableConfiguration() throws Exception {
        repository.updateTemplate("client", true);
        Path config = profile.getConfigDirectory().resolve("options.txt");
        Files.write(config, "source-config".getBytes(StandardCharsets.UTF_8));
        ProfileUpdateService service = service(new AtomicInteger());
        ProfileUpdateService.Plan plan = service.planClone("client", "migrated", "1.20.1");
        assertFalse(repository.find("migrated").isPresent());
        Profile clone = service.apply(plan, false);
        assertFalse(clone.isTemplate());
        assertEquals("1.20.1", clone.getGameVersion());
        assertEquals("1.21", repository.require("client").getGameVersion());
        assertFalse(Files.isSameFile(config, clone.getConfigDirectory().resolve("options.txt")));
        Files.write(clone.getConfigDirectory().resolve("options.txt"), "clone-config".getBytes(StandardCharsets.UTF_8));
        assertEquals("source-config", read(config));
        assertEquals(SharedArtifactCache.sha256(jar(temporary.resolve("expected-old.jar"), "old")), SharedArtifactCache.sha256(oldJar));
    }

    @Test void compatibleDisabledJarCanBeEnabledAgain() throws Exception {
        Files.move(oldJar, oldJar.resolveSibling("example.jar.disabled"));
        ProfileUpdateService service = service(new AtomicInteger());
        service.apply(service.check("client", "1.20.1"), false);
        assertTrue(Files.exists(oldJar));
        assertFalse(Files.exists(oldJar.resolveSibling("example.jar.disabled")));
    }

    @Test void explicitlyKeptDisabledJarStaysDisabled() throws Exception {
        Files.move(oldJar, oldJar.resolveSibling("example.jar.disabled"));
        ProfileUpdateService service = serviceWith(ProfileUpdateService.Decision.keep("excluded"));
        service.apply(service.check("client", "1.21"), false);
        assertFalse(Files.exists(oldJar));
        assertTrue(Files.exists(oldJar.resolveSibling("example.jar.disabled")));
    }

    @Test void errorDuringMetadataCommitRollsBackAssetsAndRemovesFailedClones() throws Exception {
        ProfileRepository failing = new ProfileRepository(temporary.resolve("data")) {
            @Override public synchronized Profile recordMigratedVersion(String id, String version) throws IOException {
                throw new IOException("Synthetic metadata failure");
            }
        };
        String oldHash = SharedArtifactCache.sha256(oldJar);
        ProfileUpdateService service = new ProfileUpdateService(failing, cache, fixtureEngine(new AtomicInteger()));
        assertThrows(IOException.class, () -> service.apply(service.check("client", "1.20.1"), false));
        assertEquals(oldHash, SharedArtifactCache.sha256(oldJar));
        assertEquals("1.21", repository.require("client").getGameVersion());
        assertThrows(IOException.class, () -> service.apply(service.planClone("client", "failed-clone", "1.20.1"), false));
        assertFalse(repository.find("failed-clone").isPresent());
    }

    @Test void pluginAndModsServerAssetsUseTheirOwnProfileDirectories() throws Exception {
        for (ProfileType type : new ProfileType[]{ProfileType.PLUGINS, ProfileType.MODS_SERVER}) {
            Profile p = repository.create(type.name().toLowerCase(), type, "1.21", type == ProfileType.PLUGINS ? "paper" : "forge", false);
            jar(p.getAssetsDirectory().resolve("sample.jar"), "old");
            ProfileUpdateService service = service(new AtomicInteger());
            Profile updated = service.apply(service.check(p.getId(), "1.20.1"), false);
            assertEquals("1.20.1", updated.getGameVersion());
            assertEquals(SharedArtifactCache.sha256(newJar), SharedArtifactCache.sha256(updated.getAssetsDirectory().resolve("sample.jar")));
        }
        assertEquals("1.21", repository.require("client").getGameVersion());
    }

    @Test void enabledDisabledCollisionRejectsTheWholePlan() throws Exception {
        jar(oldJar.resolveSibling("example.jar.disabled"), "disabled");
        ProfileUpdateService service = service(new AtomicInteger());
        assertThrows(IOException.class, () -> service.apply(service.check("client", "1.20.1"), false));
        assertTrue(Files.exists(oldJar));
        assertTrue(Files.exists(oldJar.resolveSibling("example.jar.disabled")));
        assertEquals("1.21", repository.require("client").getGameVersion());
    }

    @Test void profileSymlinkCannotReadOrOverwriteAnExternalFile() throws Exception {
        Path outside = jar(temporary.resolve("outside.jar"), "outside");
        Files.delete(oldJar);
        Files.createSymbolicLink(oldJar, outside);
        ProfileUpdateService service = service(new AtomicInteger());
        assertThrows(IOException.class, () -> service.check("client", "1.20.1"));
        assertEquals(SharedArtifactCache.sha256(jar(temporary.resolve("outside-copy.jar"), "outside")), SharedArtifactCache.sha256(outside));
    }

    @Test void activeProfileCannotBeUpdatedAndTheSamePlanCanBeAppliedAfterExit() throws Exception {
        ProfileUpdateService service = service(new AtomicInteger());
        ProfileUpdateService.Plan plan = service.check("client", "1.20.1");
        try (ProfileRepository.UseLease lease = repository.acquireUse("client")) {
            assertThrows(IOException.class, () -> service.apply(plan, false));
        }
        assertEquals("1.20.1", service.apply(plan, false).getGameVersion());
    }

    @Test void launchCannotAcquireAUseLeaseHalfwayThroughAnUpdate() throws Exception {
        java.util.concurrent.CountDownLatch downloading = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finishDownload = new java.util.concurrent.CountDownLatch(1);
        ProfileUpdateService service = new ProfileUpdateService(repository, cache, new ProfileUpdateService.Engine() {
            public ProfileUpdateService.Decision resolve(Profile p, Path path, String version) throws IOException {
                return ProfileUpdateService.Decision.replace("https://example.invalid/fixture.jar", SharedArtifactCache.sha256(newJar), "fixture");
            }
            public void download(ProfileUpdateService.Decision d, Path target) throws IOException {
                downloading.countDown();
                try {
                    if (!finishDownload.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("Fixture timed out");
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                Files.copy(newJar, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        });
        ProfileUpdateService.Plan plan = service.check("client", "1.20.1");
        java.util.concurrent.ExecutorService threads = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<Profile> updating = threads.submit(() -> service.apply(plan, false));
            assertTrue(downloading.await(5, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.Future<ProfileRepository.UseLease> launching = threads.submit(() -> repository.acquireUse("client"));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> launching.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
            finishDownload.countDown();
            assertEquals("1.20.1", updating.get(5, java.util.concurrent.TimeUnit.SECONDS).getGameVersion());
            launching.get(5, java.util.concurrent.TimeUnit.SECONDS).close();
        } finally { finishDownload.countDown(); threads.shutdownNow(); }
    }

    @Test void cancellationBeforeCheckOrApplyNeverStartsDownloadsOrMutatesProfiles() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        ProfileUpdateService service = service(downloads);
        assertThrows(java.io.InterruptedIOException.class, () -> service.check("client", "1.20.1", () -> true));
        assertThrows(java.io.InterruptedIOException.class, () -> service.planClone("client", "copy", "1.20.1", () -> true));
        ProfileUpdateService.Plan plan = service.check("client", "1.20.1");
        assertThrows(java.io.InterruptedIOException.class, () -> service.apply(plan, false, () -> true));
        assertEquals(0, downloads.get());
        assertEquals("1.21", repository.require("client").getGameVersion());
        assertFalse(repository.find("copy").isPresent());
    }

    @Test void cancellationDuringAProviderCheckIsNotReturnedAsACompatiblePlan() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        ProfileUpdateService service = new ProfileUpdateService(repository, cache, new ProfileUpdateService.Engine() {
            public ProfileUpdateService.Decision resolve(Profile p, Path jar, String version) {
                cancelled.set(true); return ProfileUpdateService.Decision.keep("fixture");
            }
            public void download(ProfileUpdateService.Decision d, Path path) { fail("No download"); }
        });
        assertThrows(java.io.InterruptedIOException.class, () -> service.check("client", "1.20.1", cancelled::get));
        assertEquals("1.21", repository.require("client").getGameVersion());
    }

    @Test void cancellationDuringDownloadRollsBackBeforeTheFirstProfileMutation() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        String oldHash = SharedArtifactCache.sha256(oldJar);
        ProfileUpdateService service = new ProfileUpdateService(repository, cache, new ProfileUpdateService.Engine() {
            public ProfileUpdateService.Decision resolve(Profile p, Path path, String version) throws IOException {
                return ProfileUpdateService.Decision.replace("https://example.invalid/fixture.jar", SharedArtifactCache.sha256(newJar), "fixture");
            }
            public void download(ProfileUpdateService.Decision d, Path destination) throws IOException {
                Files.copy(newJar, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING); cancelled.set(true);
            }
        });
        ProfileUpdateService.Plan plan = service.planClone("client", "copy", "1.20.1");
        assertThrows(java.io.InterruptedIOException.class, () -> service.apply(plan, false, cancelled::get));
        assertFalse(repository.find("copy").isPresent());
        assertEquals(oldHash, SharedArtifactCache.sha256(oldJar));
        assertEquals("1.21", repository.require("client").getGameVersion());
    }

    @Test void cancellationAfterCreatingTheCloneRemovesOnlyTheUncommittedClone() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        ProfileRepository cancelling = new ProfileRepository(temporary.resolve("data")) {
            @Override public synchronized Profile cloneProfile(String from, String to) throws IOException {
                Profile copied = super.cloneProfile(from, to); cancelled.set(true); return copied;
            }
        };
        ProfileUpdateService service = new ProfileUpdateService(cancelling, cache, fixtureEngine(new AtomicInteger()));
        ProfileUpdateService.Plan plan = service.planClone("client", "copy", "1.20.1");
        assertThrows(java.io.InterruptedIOException.class, () -> service.apply(plan, false, cancelled::get));
        assertFalse(repository.find("copy").isPresent());
        assertEquals("1.21", repository.require("client").getGameVersion());
    }

    @Test void cancellationAfterSuccessfulMetadataCommitReturnsTheActualSuccess() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        ProfileRepository cancelling = new ProfileRepository(temporary.resolve("data")) {
            @Override public synchronized Profile recordMigratedVersion(String id, String version) throws IOException {
                Profile committed = super.recordMigratedVersion(id, version); cancelled.set(true); return committed;
            }
        };
        ProfileUpdateService service = new ProfileUpdateService(cancelling, cache, fixtureEngine(new AtomicInteger()));
        Profile committed = service.apply(service.check("client", "1.20.1"), false, cancelled::get);
        assertTrue(cancelled.get());
        assertEquals("1.20.1", committed.getGameVersion());
        assertEquals("1.20.1", repository.require("client").getGameVersion());
        assertEquals(SharedArtifactCache.sha256(newJar), SharedArtifactCache.sha256(oldJar));
    }

    private ProfileUpdateService service(AtomicInteger count) { return new ProfileUpdateService(repository, cache, fixtureEngine(count)); }
    private ProfileUpdateService.Engine fixtureEngine(AtomicInteger count) {
        return new ProfileUpdateService.Engine() {
            public ProfileUpdateService.Decision resolve(Profile p, Path path, String version) throws IOException {
                assertEquals("1.20.1", version);
                return ProfileUpdateService.Decision.replace("https://example.invalid/fixture.jar", SharedArtifactCache.sha256(newJar), "compatible fixture");
            }
            public void download(ProfileUpdateService.Decision d, Path destination) throws IOException {
                count.incrementAndGet(); Files.copy(newJar, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        };
    }
    private ProfileUpdateService serviceWith(ProfileUpdateService.Decision decision) {
        return new ProfileUpdateService(repository, cache, new ProfileUpdateService.Engine() {
            public ProfileUpdateService.Decision resolve(Profile p, Path path, String target) { return decision; }
            public void download(ProfileUpdateService.Decision d, Path path) { fail("No download expected"); }
        });
    }
    static String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    static Path jar(Path path, String content) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            ZipEntry entry = new ZipEntry("fixture.txt"); entry.setTime(0);
            output.putNextEntry(entry); output.write(content.getBytes(StandardCharsets.UTF_8)); output.closeEntry();
        }
        return path;
    }
}
