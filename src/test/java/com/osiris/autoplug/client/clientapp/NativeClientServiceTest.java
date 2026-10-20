package com.osiris.autoplug.client.clientapp;

import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.launcher.*;
import com.osiris.autoplug.client.profiles.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeClientServiceTest {
    @TempDir Path temp;
    private Profile launched;
    private ServerAddress joined;
    private LaunchAccount account;
    private int launches, applied;
    private NativeClientService service(ProfileUpdater.Resolver resolver, boolean failLaunch) throws Exception {
        ProfileStore profiles = new ProfileStore(temp.resolve("autoplug")); ClientSettings settings = new ClientSettings(profiles.getRoot());
        ProfileUpdater updater = new ProfileUpdater(profiles.getCache(), resolver, (url, destination) -> { throw new IOException("No network in this fixture"); });
        return new NativeClientService(profiles, new ServerCatalog(profiles.getRoot()), settings, (profile, login, server) -> {
            if (failLaunch) throw new IOException("Fixture launch failure"); launched = profile; joined = server; account = login; launches++; return null;
        }, new NativeClientService.Migration() {
            public ProfileUpdater.Plan check(Profile profile) throws Exception { return updater.check(profile); }
            public void apply(ProfileUpdater.Plan plan, boolean allowDisabling) throws Exception { updater.apply(plan, allowDisabling); applied++; }
        });
    }
    private ServerStatus status(String version) { return new ServerStatus(new SavedServer("World", ServerAddress.parse("localhost:25566"), "FABRIC"), true, "Welcome", version, 765, 0, 20, 1, ""); }
    private static Path jar(Path path) throws Exception { try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) { zip.putNextEntry(new ZipEntry("fabric.mod.json")); zip.write("{\"id\":\"fixture\",\"version\":\"1.0\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry(); } return path; }
    @Test void exactMatchLaunchesSelectedClientWithServerAndNoMigrationOrPrompt() throws Exception {
        NativeClientService service = service((profile, file) -> { throw new AssertionError("Should not migrate"); }, false);
        service.profiles.create("plugins", Profile.Type.PLUGINS, "1.20.4", "paper", false);
        service.profiles.create("client", Profile.Type.MODS, "1.20.4", "fabric", false);
        service.autoPlay(status("Paper 1.20.4"), question -> { throw new AssertionError("Should not prompt"); });
        assertEquals(1, launches); assertEquals("client", launched.id); assertEquals(25566, joined.port); assertEquals("Player", account.getName()); assertEquals(0, applied);
    }
    @Test void mismatchClonesAndDisablesUnsupportedModOnlyAfterBothConfirmations() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        Profile template = service.profiles.create("base", Profile.Type.MODS, "1.21.1", "fabric", true); Path original = jar(template.assetsDirectory().resolve("fixture.jar"));
        AtomicInteger questions = new AtomicInteger();
        service.autoPlay(status("1.20.4"), question -> { int number = questions.incrementAndGet(); if (number == 2) assertTrue(question.contains("DISABLE fixture.jar")); return true; });
        assertEquals(2, questions.get()); assertEquals(1, applied); assertEquals(1, launches); assertFalse(launched.isTemplate); assertEquals("1.20.4", launched.gameVersion);
        assertTrue(Files.exists(launched.assetsDirectory().resolve("fixture.jar.disabled"))); assertTrue(Files.exists(original)); assertEquals(2, service.profiles.list().size());
    }
    @Test void decliningInitialCloneCreatesNothingAndNeverLaunches() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        service.profiles.create("base", Profile.Type.MODS, "1.21.1", "fabric", true);
        service.autoPlay(status("1.20.4"), question -> false);
        assertEquals(1, service.profiles.list().size()); assertEquals(0, launches); assertEquals(0, applied);
    }
    @Test void decliningDisabledSummaryRemovesNewCloneAndLeavesTemplateIntact() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        Profile template = service.profiles.create("base", Profile.Type.MODS, "1.21.1", "fabric", true); Path original = jar(template.assetsDirectory().resolve("fixture.jar"));
        AtomicInteger questions = new AtomicInteger(); service.autoPlay(status("1.20.4"), question -> questions.incrementAndGet() == 1);
        assertEquals(1, service.profiles.list().size()); assertTrue(Files.exists(original)); assertEquals(0, applied); assertEquals(0, launches);
    }
    @Test void providerFailureNeverDisablesOrLaunchesAndCleansUncommittedClone() throws Exception {
        NativeClientService service = service((profile, file) -> { throw new IOException("Fixture API unavailable"); }, false);
        Profile template = service.profiles.create("base", Profile.Type.MODS, "1.21.1", "fabric", true); Path original = jar(template.assetsDirectory().resolve("fixture.jar"));
        assertThrows(IOException.class, () -> service.autoPlay(status("1.20.4"), question -> true));
        assertTrue(Files.exists(original)); assertEquals(1, service.profiles.list().size()); assertEquals(0, applied); assertEquals(0, launches);
    }
    @Test void failedLaunchRemovesNewCloneAndReleasesPreparationGuardForRetry() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), true);
        service.profiles.create("base", Profile.Type.MODS, "1.21.1", "fabric", true);
        for (int i = 0; i < 2; i++) assertEquals("Fixture launch failure", assertThrows(IOException.class, () -> service.autoPlay(status("1.20.4"), question -> true)).getMessage());
        assertEquals(1, service.profiles.list().size());
    }
    @Test void templateLoaderMismatchAndPluginLaunchAreRejected() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        service.profiles.create("wrong", Profile.Type.MODS, "1.21.1", "forge", true); service.settings.templateId = "wrong";
        assertThrows(IOException.class, () -> service.autoPlay(status("1.20.4"), question -> true));
        service.profiles.create("hub", Profile.Type.PLUGINS, "1.20.4", "paper", false); assertThrows(IllegalArgumentException.class, () -> service.launch("hub", null)); assertEquals(0, launches);
    }
    @Test void cliLaunchSharesProfileAndExplicitServerWithoutTouchingGlobalServerState() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false); service.profiles.create("client", Profile.Type.MODS, "1.20.4", "fabric", false);
        List<String> output = new ArrayList<>(); NativeClientCommands commands = new NativeClientCommands(service);
        assertFalse(commands.handle(".stop", output::add)); assertTrue(commands.handle(".client launch client --server [::1]:25566", output::add));
        assertEquals("client", launched.id); assertEquals("::1", joined.host); assertEquals(25566, joined.port); assertEquals(1, launches);
        commands.handle(".client launch client --server https://invalid", output::add); assertEquals(1, launches); assertTrue(output.get(output.size() - 1).startsWith("Client command failed:"));
    }
    @Test void publicPreferencesAreAtomicAndDoNotSerializeAccountTokens() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        service.settings.apply("Fixture", false, "12345678-1234-1234-1234-123456789abc", "", Collections.singletonMap(17, "java"));
        byte[] before = Files.readAllBytes(service.profiles.getRoot().resolve("client-settings.properties"));
        assertThrows(IllegalArgumentException.class, () -> service.settings.apply("INVALID NAME", true, "", "", Collections.emptyMap()));
        assertEquals("Fixture", service.settings.offlineName); assertFalse(service.settings.offlineMode); assertArrayEquals(before, Files.readAllBytes(service.profiles.getRoot().resolve("client-settings.properties")));
        service.setAuthenticatedAccount(LaunchAccount.microsoft("Fixture", "12345678123412341234123456789abc", "DO_NOT_SAVE_ACCESS", "1", service.settings.microsoftClientId)); service.settings.save();
        assertFalse(new String(Files.readAllBytes(service.profiles.getRoot().resolve("client-settings.properties")), java.nio.charset.StandardCharsets.UTF_8).contains("DO_NOT_SAVE_ACCESS"));
        service.signOut(); assertFalse(service.isSignedIn());
        service.profiles.create("client", Profile.Type.MODS, "1.20.4", "fabric", false); assertThrows(IllegalStateException.class, () -> service.launch("client", null));
    }
    @Test void invalidCliOfflineAuthDoesNotChangePreviouslyValidMicrosoftModeOrSession() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        service.settings.apply("Fixture", false, "", "", Collections.emptyMap()); service.setAuthenticatedAccount(LaunchAccount.microsoft("Fixture", "12345678123412341234123456789abc", "FIXTURE_TOKEN", "1", "fixture-app"));
        List<String> output = new ArrayList<>(); new NativeClientCommands(service).handle(".client auth offline bad-name", output::add);
        assertFalse(service.settings.offlineMode); assertEquals("Fixture", service.settings.offlineName); assertTrue(service.isSignedIn()); assertTrue(output.get(0).startsWith("Client command failed:"));
    }
    @Test void signoutOrReplacementAttemptRejectsLateAuthenticationCompletion() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false); LaunchAccount account = LaunchAccount.microsoft("Fixture", "12345678123412341234123456789abc", "FIXTURE_TOKEN", "1", "fixture-app");
        long pending = service.beginAuthentication(); service.signOut(); assertFalse(service.completeAuthentication(pending, account, null)); assertFalse(service.isSignedIn());
        long previous = service.beginAuthentication(), current = service.beginAuthentication(); assertFalse(service.completeAuthentication(previous, account, null)); assertTrue(service.completeAuthentication(current, account, null)); assertTrue(service.isSignedIn());
        long cancelled = service.beginAuthentication(); service.cancelAuthentication(cancelled); assertFalse(service.completeAuthentication(cancelled, account, null));
    }
    @Test void activeModsAndPluginsDefaultsPersistAndOptionalUpdateFormsRemainIsolated() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        Profile mods = service.profiles.create("mods", Profile.Type.MODS, "1.20.4", "fabric", false), plugins = service.profiles.create("plugins", Profile.Type.PLUGINS, "1.20.4", "paper", false);
        ProfileUpdater updater = new ProfileUpdater(service.profiles.getCache(), (profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), (url, path) -> { throw new IOException("No network"); });
        ProfileCommands commands = new ProfileCommands(service.profiles, updater); List<String> output = new ArrayList<>();
        assertFalse(commands.handle(".check mods", output::add)); assertTrue(commands.handle(".update mods", output::add)); assertTrue(output.get(0).contains("Select a default"));
        commands.handle(".profiles activate mods", output::add); commands.handle(".profiles activate plugins", output::add);
        ProfileStore reloaded = new ProfileStore(service.profiles.getRoot()); assertEquals(mods.id, reloaded.getActive(Profile.Type.MODS).id); assertEquals(plugins.id, reloaded.getActive(Profile.Type.PLUGINS).id);
        jar(mods.assetsDirectory().resolve("mod.jar")); jar(plugins.assetsDirectory().resolve("plugin.jar"));
        commands.handle(".check mods", output::add); assertTrue(Files.exists(mods.assetsDirectory().resolve("mod.jar")));
        commands.handle(".update mods --confirm-disabling", output::add); assertTrue(Files.exists(mods.assetsDirectory().resolve("mod.jar.disabled"))); assertTrue(Files.exists(plugins.assetsDirectory().resolve("plugin.jar")));
        commands.handle(".update plugins --confirm-disabling", output::add); assertTrue(Files.exists(plugins.assetsDirectory().resolve("plugin.jar.disabled")));
        service.profiles.delete("mods"); assertNull(service.profiles.getActive(Profile.Type.MODS)); assertEquals("plugins", service.profiles.getActive(Profile.Type.PLUGINS).id);
    }
    @Test void lateAuthenticationCannotOverwriteNewOfflinePreferencesAndValidCompletionPersistsAtomically() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        String clientId = "12345678-1234-1234-1234-123456789abc";
        LaunchAccount account = LaunchAccount.microsoft("Fixture", "12345678123412341234123456789abc", "FIXTURE_TOKEN", "1", clientId);
        long cancelled = service.beginAuthentication(); assertTrue(service.authenticationCurrent(cancelled));
        service.useOfflineAccount("ChosenOffline");
        assertFalse(service.completeAuthenticationAndSave(cancelled, account, null, clientId));
        ClientSettings persisted = new ClientSettings(service.profiles.getRoot());
        assertTrue(service.settings.offlineMode); assertTrue(persisted.offlineMode); assertEquals("ChosenOffline", persisted.offlineName); assertFalse(service.isSignedIn());
        long current = service.beginAuthentication(); assertTrue(service.completeAuthenticationAndSave(current, account, null, clientId));
        persisted = new ClientSettings(service.profiles.getRoot()); assertFalse(persisted.offlineMode); assertEquals(clientId, persisted.microsoftClientId); assertTrue(service.isSignedIn());
        long invalid = service.beginAuthentication(); assertThrows(IllegalArgumentException.class, () -> service.completeAuthenticationAndSave(invalid, account, null, "invalid-id"));
        assertFalse(service.authenticationCurrent(invalid)); assertEquals(clientId, new ClientSettings(service.profiles.getRoot()).microsoftClientId);
    }
    @Test void startupCommandTransportPreservesVersionsAndHostnamesAndRecognizesOnlyClientScope() {
        List<String> commands = StartupCommands.parse(Arrays.asList(".profiles", "create", "base", "1.20.4", "fabric", ".client", "launch", "base", "--server", "play.example.com:25566"));
        assertEquals(Arrays.asList(".profiles create base 1.20.4 fabric", ".client launch base --server play.example.com:25566"), commands); assertTrue(StartupCommands.clientOnly(commands));
        assertFalse(StartupCommands.clientOnly(Collections.singletonList(".backup"))); assertFalse(StartupCommands.clientOnly(Collections.emptyList()));
    }
    @Test void worldNetworkPreferencesPersistAndInvalidCandidateDoesNotChangeAccountOrDisk() throws Exception {
        NativeClientService service = service((profile, file) -> ProfileUpdater.Resolution.unsupported("fixture"), false);
        service.applyPreferences("Fixture", false, "", "", Collections.emptyMap(), 25566, false);
        service.setAuthenticatedAccount(LaunchAccount.microsoft("Fixture", "12345678123412341234123456789abc", "FIXTURE_TOKEN", "1", "fixture-app"));
        byte[] before = Files.readAllBytes(service.profiles.getRoot().resolve("client-settings.properties"));
        assertThrows(IllegalArgumentException.class, () -> service.applyPreferences("Changed", true, "", "", Collections.emptyMap(), 80, true));
        assertTrue(service.isSignedIn()); assertFalse(service.settings.offlineMode); assertEquals("Fixture", service.settings.offlineName);
        assertArrayEquals(before, Files.readAllBytes(service.profiles.getRoot().resolve("client-settings.properties")));
        service.useOfflineAccount("FixtureLocal"); ClientSettings reloaded = new ClientSettings(service.profiles.getRoot());
        assertEquals(25566, reloaded.worldPort()); assertFalse(reloaded.upnpAllowed()); assertTrue(reloaded.offlineMode);
    }
}
