package com.osiris.autoplug.client.profiles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.ui.LauncherActions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Real persisted settings/profile state, with no game, authentication or provider network access. */
class LauncherPreferencesTest {
    @TempDir Path temporary;

    @Test void lastChoicesAreTargetSpecificPersistAcrossRestartAndDoNotChangeGlobalDefault() throws Exception {
        String vanilla, fabric, selectedDefault;
        try (LauncherServices services = open()) {
            selectedDefault = services.settings().defaultProfile;
            vanilla = services.createProfile("Personal vanilla", "1.21.1", "VANILLA", "MODS", false).id;
            fabric = services.getProfiles().ensureFabricDefault("1.21.1").id;
            services.rememberProfile("server:play.example.test:25565", vanilla);
            services.rememberProfile("world:local-world-1", vanilla);
            services.rememberProfile("server:play.example.test:25565", fabric);
            services.rememberProfile("server:play.example.test:25566", vanilla);
            assertEquals(fabric, services.preferredProfile("server:play.example.test:25565"));
            assertEquals(vanilla, services.preferredProfile("world:local-world-1"));
            assertEquals(vanilla, services.preferredProfile("server:play.example.test:25566"));
            assertEquals("", services.preferredProfile("world:unvisited"));
            assertEquals(selectedDefault, services.settings().defaultProfile);
        }
        try (LauncherServices reopened = open()) {
            assertEquals(fabric, reopened.preferredProfile("server:play.example.test:25565"));
            assertEquals(vanilla, reopened.preferredProfile("world:local-world-1"));
            assertEquals(vanilla, reopened.preferredProfile("server:play.example.test:25566"));
            assertEquals(selectedDefault, reopened.settings().defaultProfile);
        }
    }

    @Test void deletedTemplatesAndMigratingChoicesAreNotOfferedForAutomaticLaunch() throws Exception {
        String deleted, template, migrating;
        try (LauncherServices services = open()) {
            deleted = services.createProfile("Delete this", "1.21.1", "VANILLA", "MODS", false).id;
            template = services.createProfile("Make template", "1.21.1", "VANILLA", "MODS", false).id;
            migrating = services.createProfile("Migrate later", "1.21.1", "FABRIC", "MODS", false).id;
            services.rememberProfile("server:deleted", deleted);
            services.rememberProfile("world:template", template);
            services.rememberProfile("world:migrating", migrating);
            services.deleteProfile(deleted);
            services.setTemplate(template, true);
            Profile changing = services.getProfiles().get(migrating);
            changing.migrationPending = true; services.getProfiles().save(changing);
            assertEquals("", services.preferredProfile("server:deleted"));
            assertEquals("", services.preferredProfile("world:template"));
            assertEquals("", services.preferredProfile("world:migrating"));
        }
        try (LauncherServices reopened = open()) {
            assertEquals("", reopened.preferredProfile("server:deleted"));
            assertEquals("", reopened.preferredProfile("world:template"));
            assertEquals("", reopened.preferredProfile("world:migrating"));
            assertTrue(reopened.getProfiles().get(template).template);
            assertTrue(reopened.getProfiles().get(migrating).migrationPending);
        }
    }

    @Test void invalidChoicesCannotReplacePreviouslySavedPlayableChoice() throws Exception {
        try (LauncherServices services = open()) {
            String playable = services.settings().defaultProfile;
            String server = services.getProfiles().ensureDefault("1.21.1", ProfileType.MODS_SERVER).id;
            String template = services.createProfile("Template", "1.21.1", "FABRIC", "MODS", true).id;
            services.rememberProfile("server:example", playable);
            byte[] before = Files.readAllBytes(temporary.resolve("autoplug/settings.json"));
            assertThrows(IOException.class, () -> services.rememberProfile("server:example", server));
            assertThrows(IOException.class, () -> services.rememberProfile("server:example", template));
            assertThrows(IOException.class, () -> services.rememberProfile("server:example", "deleted-profile"));
            assertThrows(IOException.class, () -> services.rememberProfile("invalid-target", playable));
            assertThrows(IOException.class, () -> services.rememberProfile(null, playable));
            assertEquals(playable, services.preferredProfile("server:example"));
            assertArrayEquals(before, Files.readAllBytes(temporary.resolve("autoplug/settings.json")));
        }
    }

    @Test void fullscreenDefaultsOnAndExplicitWindowedChoiceSurvivesRestarts() throws Exception {
        try (LauncherServices services = open()) {
            assertTrue(services.settings().fullscreen);
            LauncherActions.SettingsInfo settings = services.settings();
            settings.fullscreen = false; settings.port = 25570;
            services.saveSettings(settings);
            assertFalse(services.settings().fullscreen);
            LauncherActions.SettingsInfo detached = services.settings();
            detached.fullscreen = true; detached.javaPaths.put(16, "not-saved");
            assertFalse(services.settings().fullscreen);
            assertFalse(services.settings().javaPaths.containsKey(16));
        }
        try (LauncherServices reopened = open()) {
            assertFalse(reopened.settings().fullscreen);
            assertEquals(25570, reopened.settings().port);
            LauncherActions.SettingsInfo settings = reopened.settings(); settings.fullscreen = true; reopened.saveSettings(settings);
        }
        try (LauncherServices reopened = open()) { assertTrue(reopened.settings().fullscreen); }
    }

    @Test void olderSettingsWithoutNewFieldsMigrateToFullscreenAndEmptyRememberedChoices() throws Exception {
        String selected;
        try (LauncherServices services = open()) {
            selected = services.settings().defaultProfile;
            services.rememberProfile("world:old-choice", selected);
        }
        Path settings = temporary.resolve("autoplug/settings.json");
        JsonObject old = JsonParser.parseString(new String(Files.readAllBytes(settings), StandardCharsets.UTF_8)).getAsJsonObject();
        old.remove("launchChoices"); old.getAsJsonObject("settings").remove("fullscreen");
        Files.write(settings, old.toString().getBytes(StandardCharsets.UTF_8));
        try (LauncherServices reopened = open()) {
            assertTrue(reopened.settings().fullscreen);
            assertEquals(selected, reopened.settings().defaultProfile);
            assertEquals("", reopened.preferredProfile("world:old-choice"));
            reopened.rememberProfile("world:new-choice", selected);
        }
        try (LauncherServices reopened = open()) { assertEquals(selected, reopened.preferredProfile("world:new-choice")); }
    }

    private LauncherServices open() throws Exception {
        return new LauncherServices(temporary.resolve("autoplug"), temporary.resolve("minecraft"), null);
    }
}
