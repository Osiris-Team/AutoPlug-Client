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
        try (LauncherServices services = new LauncherServices(temp, temp.resolve("minecraft"), output::add)) {
            LauncherCommands commands = new LauncherCommands(services, output::add);
            commands.execute(commands.tokenize(".profiles create \"My Pack\" 1.21.1 FABRIC --template"));
            id = services.profiles().stream().filter(p -> p.name.equals("My Pack")).findFirst().get().id;
            commands.execute(commands.tokenize(".mc account offline FixtureUser"));
            LauncherActions.SettingsInfo settings = services.settings(); settings.defaultProfile = id; settings.port = 25570; settings.upnp = false; services.saveSettings(settings);
            assertThrows(java.io.IOException.class, () -> services.launchProfile(id, null, 25565));
            assertFalse(Files.exists(temp.resolve("autoplug"))); // No legacy server initialization.
            assertFalse(Files.exists(temp.resolve("accounts.json"))); // Offline setting creates no credential store.
        }
        try (LauncherServices services = new LauncherServices(temp, temp.resolve("minecraft"), output::add)) {
            assertEquals("FixtureUser (offline)", services.settings().account);
            assertEquals(25570, services.settings().port); assertFalse(services.settings().upnp);
            assertNotEquals(id, services.settings().defaultProfile, "Templates must not remain the selected launch default");
            assertFalse(services.getProfiles().get(services.settings().defaultProfile).template);
            assertTrue(services.getProfiles().get(id).template);
            LauncherCommands commands = new LauncherCommands(services, output::add);
            commands.execute(commands.tokenize(".profiles clone " + id + " 1.20.1 --name Downgrade --yes"));
            assertEquals(5, services.profiles().size()); // Three version-matched defaults plus the user's source and clone.
            assertTrue(services.profiles().stream().anyMatch(p -> p.name.equals("Downgrade") && p.launchable));
        }
    }
    @Test void updateNeedsReviewedPlanAndWorldProfileCompatibilityIsCheckedEarly() throws Exception {
        try (LauncherServices services = new LauncherServices(temp, temp.resolve("minecraft"), ignored -> { })) {
            LauncherActions.ProfileInfo client = services.createProfile("Client", "1.21.1", "FABRIC", "MODS", false);
            LauncherActions.ProfileInfo server = services.createProfile("Server", "1.21.1", "FORGE", "MODS_SERVER", false);
            assertThrows(java.io.IOException.class, () -> services.updateProfile(client.id));
            assertThrows(java.io.IOException.class, () -> services.createWorld("Invalid", server.id, client.id));
            assertTrue(services.worlds().isEmpty());
            services.checkProfile(client.id); services.updateProfile(client.id);
        }
    }
    @Test void disablingRememberClearsAccountsEvenAfterSwitchingOffline() throws Exception {
        try (LauncherServices services = new LauncherServices(temp, temp.resolve("minecraft"), ignored -> { })) {
            LauncherActions.SettingsInfo settings = services.settings(); settings.rememberAccount = true; services.saveSettings(settings);
            com.osiris.autoplug.client.launcher.AccountStore store = new com.osiris.autoplug.client.launcher.AccountStore(temp.resolve("accounts.json"));
            store.save(new com.osiris.autoplug.client.launcher.MinecraftAccount("FixtureOne", "123456781234123412341234567890ab", "test-access", "test-refresh", "test-client", "", false, java.time.Instant.now().plusSeconds(3600)));
            store.save(new com.osiris.autoplug.client.launcher.MinecraftAccount("FixtureTwo", "abcdefab1234123412341234567890ab", "test-access", "test-refresh", "test-client", "", false, java.time.Instant.now().plusSeconds(3600)));
            services.useOfflineAccount("Offline");
            settings = services.settings(); settings.rememberAccount = false; services.saveSettings(settings);
            assertTrue(store.list().isEmpty());
        }
    }
    @Test void localWorldLaunchUsesRecordedVersionAndCannotStartOrShareAServer() throws Exception {
        List<String> output = new ArrayList<>(), launches = new ArrayList<>();
        try (LauncherServices services = new LauncherServices(temp, temp.resolve("minecraft"), output::add) {
            @Override public List<LauncherActions.WorldInfo> worlds() {
                return Arrays.asList(
                        new LauncherActions.WorldInfo("local:New World", "My save", "", "", "fixture", "", false, true, "1.21.1"),
                        new LauncherActions.WorldInfo("local:Legacy", "Legacy", "", "", "fixture", "", false, true, ""));
            }
            @Override public String launchLocalWorld(String id, String version) { launches.add(id + " | " + version); return "Select Singleplayer in Minecraft."; }
            @Override public void launchWorld(String id, boolean share) { fail("A local save must not start a dedicated server"); }
            @Override public void setWorldEulaAccepted(String id, boolean accepted) { fail("A local save must not write a server EULA"); }
        }) {
            LauncherCommands commands = new LauncherCommands(services, output::add);
            commands.execute(commands.tokenize(".mc worlds launch \"local:New World\""));
            assertEquals(Collections.singletonList("local:New World | 1.21.1"), launches);
            assertEquals("Select Singleplayer in Minecraft.", output.get(output.size() - 1));
            assertThrows(java.io.IOException.class, () -> commands.execute(commands.tokenize(".mc worlds launch local:Legacy")));
            commands.execute(commands.tokenize(".mc worlds launch local:Legacy --version 1.12.2"));
            assertEquals("local:Legacy | 1.12.2", launches.get(1));
            assertThrows(java.io.IOException.class, () -> commands.execute(commands.tokenize(".mc worlds launch \"local:New World\" --share")));
            assertThrows(java.io.IOException.class, () -> commands.execute(commands.tokenize(".mc worlds launch \"local:New World\" --accept-eula")));
            assertThrows(java.io.IOException.class, () -> commands.execute(commands.tokenize(".mc worlds share \"local:New World\"")));
            assertThrows(java.io.IOException.class, () -> commands.execute(commands.tokenize(".mc worlds stop \"local:New World\"")));
            assertEquals(2, launches.size());
            commands.execute(commands.tokenize(".mc worlds list"));
            assertTrue(output.stream().anyMatch(line -> line.contains("singleplayer | 1.21.1")));
        }
    }

    @Test void ownedLocalSaveRetainsCliAliasWithoutPermittingServerFlags() throws Exception {
        Path minecraft = temp.resolve("minecraft"), directory = Files.createDirectories(minecraft.resolve("saves/Already owned"));
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(Files.newOutputStream(directory.resolve("level.dat")))) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(10); out.writeUTF("Data");
            out.writeByte(8); out.writeUTF("LevelName"); out.writeUTF("Owned fixture");
            out.writeByte(10); out.writeUTF("Version"); out.writeByte(8); out.writeUTF("Name"); out.writeUTF("1.21.1");
            out.writeByte(0); out.writeByte(0); out.writeByte(0);
        }
        byte[] original = Files.readAllBytes(directory.resolve("level.dat"));
        com.osiris.autoplug.client.worlds.WorldStore store = new com.osiris.autoplug.client.worlds.WorldStore(temp.resolve("worlds"));
        String ownedId = store.registerLocal(new com.osiris.autoplug.client.worlds.LocalWorldStore(minecraft).get("local:Already owned")).id;
        List<String> launches = new ArrayList<>();
        try (LauncherServices services = new LauncherServices(temp, minecraft, null) {
            @Override public String launchLocalWorld(String id, String version) throws Exception {
                assertEquals(ownedId, store.getLocal(id, new com.osiris.autoplug.client.worlds.LocalWorldStore(minecraft)).id);
                launches.add(id + " | " + version); return "Started fixture without launching a process";
            }
            @Override public void launchWorld(String id, boolean share) { fail("Local alias must not start a server"); }
            @Override public void setWorldEulaAccepted(String id, boolean accepted) { fail("Local alias must not accept server EULA"); }
            @Override public String shareWorld(String id) { fail("Local alias must not expose the save"); return ""; }
        }) {
            assertEquals(1, services.worlds().size()); assertEquals(ownedId, services.worlds().get(0).id);
            LauncherCommands commands = new LauncherCommands(services, ignored -> { });
            commands.execute(commands.tokenize(".mc worlds launch \"local:Already owned\""));
            commands.execute(commands.tokenize(".mc worlds launch " + ownedId));
            assertEquals(Arrays.asList("local:Already owned | 1.21.1", ownedId + " | 1.21.1"), launches);
            for (String flags : Arrays.asList(" --share", " --accept-eula"))
                assertThrows(java.io.IOException.class, () -> commands.execute(commands.tokenize(".mc worlds launch \"local:Already owned\"" + flags)));
            for (String action : Arrays.asList("share", "stop"))
                assertThrows(java.io.IOException.class, () -> commands.execute(commands.tokenize(".mc worlds " + action + " \"local:Already owned\"")));
            assertThrows(java.io.IOException.class, () -> commands.execute(commands.tokenize(".mc worlds launch \"local:../Already owned\"")));
            assertEquals(2, launches.size());
        }
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("level.dat")));
        assertFalse(Files.exists(directory.resolve("eula.txt")));
    }
}
