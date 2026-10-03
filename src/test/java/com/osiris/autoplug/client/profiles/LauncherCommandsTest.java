package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.ui.LauncherActions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LauncherCommandsTest {
    @TempDir Path temp;
    @Test void consoleTokenizerPreservesVersionsAddressesAndWindowsPaths() throws Exception {
        LauncherCommands commands = new LauncherCommands(null, ignored -> { });
        assertEquals(Arrays.asList(".profiles", "create", "My Pack", "1.21.1", "FABRIC"), commands.tokenize(".profiles create \"My Pack\" 1.21.1 FABRIC"));
        assertEquals(Arrays.asList(".profiles", "add", "pack", "C:\\Game Files\\mod.jar"), commands.tokenize(".profiles add pack \"C:\\Game Files\\mod.jar\""));
        assertEquals("127.0.0.1:25570", commands.tokenize(".mc launch pack --server 127.0.0.1:25570").get(4));
        assertThrows(java.io.IOException.class, () -> commands.tokenize(".profiles create \"unterminated"));
    }
    @Test void commandsPersistProfilesAndSettingsAcrossInvocationsWithoutGlobalConfig() throws Exception {
        List<String> output = new ArrayList<>(); String id;
        try (LauncherServices services = new LauncherServices(temp, output::add)) {
            LauncherCommands commands = new LauncherCommands(services, output::add);
            commands.execute(commands.tokenize(".profiles create \"My Pack\" 1.21.1 FABRIC --template"));
            id = services.profiles().get(0).id;
            commands.execute(commands.tokenize(".mc account offline FixtureUser"));
            LauncherActions.SettingsInfo settings = services.settings(); settings.defaultProfile = id; settings.port = 25570; settings.upnp = false; services.saveSettings(settings);
            assertThrows(java.io.IOException.class, () -> services.launchProfile(id, null, 25565));
            assertFalse(Files.exists(temp.resolve("autoplug"))); // No legacy server initialization.
            assertFalse(Files.exists(temp.resolve("accounts.json"))); // Offline setting creates no credential store.
        }
        try (LauncherServices services = new LauncherServices(temp, output::add)) {
            assertEquals("FixtureUser (offline)", services.settings().account);
            assertEquals(25570, services.settings().port); assertFalse(services.settings().upnp);
            assertEquals(id, services.settings().defaultProfile);
            assertTrue(services.profiles().get(0).template);
            LauncherCommands commands = new LauncherCommands(services, output::add);
            commands.execute(commands.tokenize(".profiles clone " + id + " 1.20.1 --name Downgrade --yes"));
            assertEquals(2, services.profiles().size());
            assertTrue(services.profiles().stream().anyMatch(p -> p.name.equals("Downgrade") && p.launchable));
        }
    }
    @Test void updateNeedsReviewedPlanAndWorldProfileCompatibilityIsCheckedEarly() throws Exception {
        try (LauncherServices services = new LauncherServices(temp, ignored -> { })) {
            LauncherActions.ProfileInfo client = services.createProfile("Client", "1.21.1", "FABRIC", "MODS", false);
            LauncherActions.ProfileInfo server = services.createProfile("Server", "1.21.1", "FORGE", "MODS_SERVER", false);
            assertThrows(java.io.IOException.class, () -> services.updateProfile(client.id));
            assertThrows(java.io.IOException.class, () -> services.createWorld("Invalid", server.id, client.id));
            assertTrue(services.worlds().isEmpty());
            services.checkProfile(client.id); services.updateProfile(client.id);
        }
    }
    @Test void disablingRememberClearsAccountsEvenAfterSwitchingOffline() throws Exception {
        try (LauncherServices services = new LauncherServices(temp, ignored -> { })) {
            LauncherActions.SettingsInfo settings = services.settings(); settings.rememberAccount = true; services.saveSettings(settings);
            com.osiris.autoplug.client.launcher.AccountStore store = new com.osiris.autoplug.client.launcher.AccountStore(temp.resolve("accounts.json"));
            store.save(new com.osiris.autoplug.client.launcher.MinecraftAccount("FixtureOne", "123456781234123412341234567890ab", "test-access", "test-refresh", "test-client", "", false, java.time.Instant.now().plusSeconds(3600)));
            store.save(new com.osiris.autoplug.client.launcher.MinecraftAccount("FixtureTwo", "abcdefab1234123412341234567890ab", "test-access", "test-refresh", "test-client", "", false, java.time.Instant.now().plusSeconds(3600)));
            services.useOfflineAccount("Offline");
            settings = services.settings(); settings.rememberAccount = false; services.saveSettings(settings);
            assertTrue(store.list().isEmpty());
        }
    }
}
