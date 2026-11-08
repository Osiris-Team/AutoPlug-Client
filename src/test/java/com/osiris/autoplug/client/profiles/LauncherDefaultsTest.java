package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.ui.LauncherActions;
import com.osiris.autoplug.client.worlds.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LauncherDefaultsTest {
    @TempDir Path temporary;

    @Test void firstRunHasMatchingClientAndServerWithoutAcceptingEula() throws Exception {
        Path root = temporary.resolve("autoplug");
        try (LauncherServices service = new LauncherServices(root, temporary.resolve("minecraft"), null)) {
            assertEquals(3, service.profiles().size());
            Profile client = service.getProfiles().get(service.settings().defaultProfile);
            assertEquals("Default (VANILLA)", client.name);
            assertEquals("VANILLA", client.loader); assertEquals(ProfileType.MODS, client.type);
            assertEquals("1.21.1", client.gameVersion); assertFalse(client.template);
            Profile fabric = service.getProfiles().list().stream().filter(p -> "FABRIC".equals(p.loader)).findFirst().orElseThrow(() -> new AssertionError("Missing Fabric default"));
            assertEquals("Default (FABRIC)", fabric.name); assertEquals(client.gameVersion, fabric.gameVersion);
            assertEquals(FabricDefaultProfile.PRESET, fabric.builtinPreset); assertFalse(fabric.builtinPresetInstalled);
            assertTrue(Files.isDirectory(client.getDirectory().resolve("mods")));
            LauncherActions.WorldInfo world = service.createWorld("First world", "", "");
            Profile server = service.getProfiles().get(world.serverProfileId);
            assertEquals("Default", server.name); assertEquals(client.gameVersion, server.gameVersion);
            assertEquals(ProfileType.MODS_SERVER, server.type);
            assertFalse(new WorldStore(root.resolve("worlds")).get(world.id).eulaAccepted);
            assertFalse(Files.exists(Paths.get(world.directory).resolve("eula.txt")));
            assertFalse(Files.exists(temporary.resolve("minecraft")));
        }
    }

    @Test void requestedVersionCreatesIndependentDefaultsAndIsIdempotent() throws Exception {
        Path root = temporary.resolve("autoplug"); String original, requested;
        try (LauncherServices service = new LauncherServices(root, temporary.resolve("minecraft"), null)) {
            original = service.settings().defaultProfile;
            requested = service.ensureDefaultProfiles("1.20.1").id;
            assertNotEquals(original, requested);
            assertEquals(requested, service.ensureDefaultProfiles("1.20.1").id);
            assertEquals(original, service.settings().defaultProfile, "Choosing a server version must not overwrite the selected user default");
            assertEquals(6, service.profiles().size());
            LauncherActions.WorldInfo world = service.createWorld("Other version", null, requested);
            assertEquals("1.20.1", service.getProfiles().get(world.serverProfileId).gameVersion);
            assertThrows(java.io.IOException.class, () -> service.ensureDefaultProfiles("../escape"));
            assertEquals(6, service.profiles().size());
        }
        try (LauncherServices reopened = new LauncherServices(root, temporary.resolve("minecraft"), null)) {
            assertEquals(original, reopened.settings().defaultProfile);
            assertEquals(requested, reopened.ensureDefaultProfiles("1.20.1").id);
            assertEquals(6, reopened.profiles().size());
        }
    }

    @Test void existingSelectedUserProfileAndItsFilesArePreservedOnBootstrap() throws Exception {
        Path root = temporary.resolve("autoplug"); String selected; byte[] metadata;
        try (LauncherServices service = new LauncherServices(root, temporary.resolve("minecraft"), null)) {
            selected = service.createProfile("Personal pack", "1.19.4", "FABRIC", "MODS", false).id;
            LauncherActions.SettingsInfo settings = service.settings(); settings.defaultProfile = selected; service.saveSettings(settings);
            Path directory = service.getProfiles().get(selected).getDirectory();
            metadata = Files.readAllBytes(directory.resolve("profile.json"));
            Files.write(directory.resolve("options.txt"), Arrays.asList("keep-me"));
        }
        try (LauncherServices service = new LauncherServices(root, temporary.resolve("minecraft"), null)) {
            assertEquals(selected, service.settings().defaultProfile);
            Profile profile = service.getProfiles().get(selected);
            assertArrayEquals(metadata, Files.readAllBytes(profile.getDirectory().resolve("profile.json")));
            assertEquals(Collections.singletonList("keep-me"), Files.readAllLines(profile.getDirectory().resolve("options.txt")));
            assertEquals("1.19.4", service.ensureDefaultProfiles("").gameVersion);
            assertEquals(7, service.profiles().size());
        }
    }

    @Test void progressSubscriptionsReceiveSamePhaseAsLogAndCanDetach() throws Exception {
        Path minecraft = Files.createDirectories(temporary.resolve("minecraft"));
        Files.write(minecraft.resolve("saves"), new byte[]{1});
        List<String> log = new ArrayList<>(), ui = new ArrayList<>();
        try (LauncherServices service = new LauncherServices(temporary.resolve("autoplug"), minecraft, log::add)) {
            service.onProgress(ui::add); service.worlds();
            assertEquals(log, ui); assertEquals(1, ui.size());
            service.onProgress(null); service.worlds();
            assertEquals(1, ui.size()); assertEquals(2, log.size());
            service.onProgress(message -> { throw new IllegalStateException("Disposed UI"); });
            assertDoesNotThrow(service::worlds); assertEquals(3, log.size());
        }
    }

    @Test void unavailableSelectedDefaultsAreReplacedWithoutMutatingTheOriginalProfile() throws Exception {
        for (boolean template : new boolean[]{true, false}) {
            Path root = temporary.resolve(template ? "template" : "migration"); String unavailable; byte[] metadata;
            try (LauncherServices service = new LauncherServices(root, temporary.resolve("minecraft"), null)) {
                Profile profile = service.getProfiles().create("Not ready", "1.20.4", "FABRIC", ProfileType.MODS);
                profile.template = template; profile.migrationPending = !template; service.getProfiles().save(profile);
                unavailable = profile.id; metadata = Files.readAllBytes(profile.getDirectory().resolve("profile.json"));
                LauncherActions.SettingsInfo settings = service.settings(); settings.defaultProfile = unavailable; service.saveSettings(settings);
            }
            try (LauncherServices service = new LauncherServices(root, temporary.resolve("minecraft"), null)) {
                Profile selected = service.getProfiles().get(service.settings().defaultProfile);
                assertNotEquals(unavailable, selected.id); assertFalse(selected.template); assertFalse(selected.migrationPending);
                assertEquals("VANILLA", selected.loader); assertEquals("1.20.4", selected.gameVersion);
                assertEquals(selected.id, service.ensureDefaultProfiles("").id);
                assertEquals(7, service.profiles().size());
                assertArrayEquals(metadata, Files.readAllBytes(service.getProfiles().get(unavailable).getDirectory().resolve("profile.json")));
            }
        }
    }
}
