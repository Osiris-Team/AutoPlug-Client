package com.osiris.autoplug.client.ui;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Application services used by the dashboard. Calls run outside Swing's event thread. */
public interface LauncherActions {
    default List<ProfileInfo> profiles() throws Exception { return Collections.emptyList(); }
    default ProfileInfo createProfile(String name, String version, String loader, String type, boolean template) throws Exception { throw unavailable(); }
    default ProfileInfo cloneProfile(String sourceId, String name, String targetVersion, String loader) throws Exception { throw unavailable(); }
    default void deleteProfile(String id) throws Exception { throw unavailable(); }
    default String checkProfile(String id) throws Exception { throw unavailable(); }
    default String updateProfile(String id) throws Exception { throw unavailable(); }
    default void setTemplate(String id, boolean template) throws Exception { throw unavailable(); }
    default void addArtifact(String profileId, String jarPath, String modrinthId) throws Exception { throw unavailable(); }
    default List<WorldInfo> worlds() throws Exception { return Collections.emptyList(); }
    default WorldInfo createWorld(String name, String serverProfileId, String clientProfileId) throws Exception { throw unavailable(); }
    default void setWorldEulaAccepted(String id, boolean accepted) throws Exception { throw unavailable(); }
    default void launchWorld(String id, boolean share) throws Exception { throw unavailable(); }
    default String shareWorld(String id) throws Exception { throw unavailable(); }
    default void launchProfile(String profileId, String host, int port) throws Exception { throw unavailable(); }
    default SettingsInfo settings() throws Exception { return new SettingsInfo(); }
    default void saveSettings(SettingsInfo settings) throws Exception { throw unavailable(); }
    /** Deliver device sign-in instructions before waiting for the account provider. Never log tokens. */
    default void signInMicrosoft(Consumer<String> instructions) throws Exception { throw unavailable(); }
    default void useOfflineAccount(String name) throws Exception { throw unavailable(); }

    static UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException("The launcher service is not available yet.");
    }

    final class ProfileInfo {
        public final String id, name, gameVersion, loader, type, directory, migrationSummary;
        public final boolean template, launchable;
        public ProfileInfo(String id, String name, String gameVersion, String loader, String type, String directory, boolean template) {
            this(id, name, gameVersion, loader, type, directory, template, "", true);
        }
        public ProfileInfo(String id, String name, String gameVersion, String loader, String type, String directory, boolean template, String migrationSummary, boolean launchable) {
            this.id = id; this.name = name; this.gameVersion = gameVersion; this.loader = loader;
            this.type = type; this.directory = directory; this.template = template;
            this.migrationSummary = migrationSummary; this.launchable = launchable;
        }
        @Override public String toString() { return name + " · " + gameVersion + " / " + loader; }
    }

    final class WorldInfo {
        public final String id, name, serverProfileId, clientProfileId, directory, thumbnail;
        public final boolean running;
        public WorldInfo(String id, String name, String serverProfileId, String clientProfileId, String directory, String thumbnail, boolean running) {
            this.id = id; this.name = name; this.serverProfileId = serverProfileId; this.clientProfileId = clientProfileId; this.directory = directory;
            this.thumbnail = thumbnail; this.running = running;
        }
        @Override public String toString() { return name; }
    }

    final class SettingsInfo {
        public String java8 = "", java17 = "", java21 = "", defaultProfile = "", account = "Offline", microsoftClientId = "";
        public Map<Integer, String> javaPaths = new LinkedHashMap<>();
        public int port = 25565;
        public boolean upnp = true;
        public boolean rememberAccount = false;
    }
}
