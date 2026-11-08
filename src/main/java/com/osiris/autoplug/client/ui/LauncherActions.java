package com.osiris.autoplug.client.ui;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Application services used by the dashboard. Calls run outside Swing's event thread. */
public interface LauncherActions {
    /** Replaces the dashboard's activity listener; null releases it. Callbacks may arrive on any thread. */
    default void onProgress(Consumer<String> listener) {}
    /** Optional measured current-step progress (0–100); null or a negative value means indeterminate. */
    default void onProgressValue(Consumer<Integer> listener) {}
    /** Ensures compatible ready vanilla client/server defaults without replacing a valid user selection. */
    default ProfileInfo ensureDefaultProfiles(String gameVersion) throws Exception { throw unavailable(); }
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
    default void launchWorld(String id, boolean share, String profileId) throws Exception { launchWorld(id, share); }
    /** Starts an existing singleplayer save and returns the backend's launch status. */
    default String launchLocalWorld(String id, String version) throws Exception { throw unavailable(); }
    default String launchLocalWorld(String id, String version, String profileId) throws Exception { return launchLocalWorld(id, version); }
    default String preferredProfile(String target) throws Exception { return ""; }
    default void rememberProfile(String target, String profileId) throws Exception { }
    default String shareWorld(String id) throws Exception { throw unavailable(); }
    default String serverLog(String id) throws Exception { return "Start this managed world to view its AutoPlug console."; }
    default void serverCommand(String id, String command) throws Exception { throw unavailable(); }
    default void stopWorld(String id) throws Exception { throw unavailable(); }
    default void restartWorld(String id) throws Exception { throw unavailable(); }
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
        public final String id, name, serverProfileId, clientProfileId, directory, thumbnail, gameVersion;
        public final boolean running, local;
        public final boolean modded, sizeComplete, cheats, hardcore, quickPlayEligible, metadataAvailable;
        public final long lastPlayed, sizeBytes;
        public final List<String> dataPacks;
        public WorldInfo(String id, String name, String serverProfileId, String clientProfileId, String directory, String thumbnail, boolean running) {
            this(id, name, serverProfileId, clientProfileId, directory, thumbnail, running, false, "");
        }
        public WorldInfo(String id, String name, String serverProfileId, String clientProfileId, String directory, String thumbnail, boolean running, boolean local, String gameVersion) {
            this(id, name, serverProfileId, clientProfileId, directory, thumbnail, running, local, gameVersion,
                    false, -1, -1, false, Collections.emptyList(), false, false, false, false);
        }
        public WorldInfo(String id, String name, String serverProfileId, String clientProfileId, String directory, String thumbnail, boolean running, boolean local, String gameVersion,
                         boolean modded, long lastPlayed, long sizeBytes, boolean sizeComplete, List<String> dataPacks, boolean cheats, boolean hardcore, boolean quickPlayEligible) {
            this(id, name, serverProfileId, clientProfileId, directory, thumbnail, running, local, gameVersion,
                    modded, lastPlayed, sizeBytes, sizeComplete, dataPacks, cheats, hardcore, quickPlayEligible, true);
        }
        private WorldInfo(String id, String name, String serverProfileId, String clientProfileId, String directory, String thumbnail, boolean running, boolean local, String gameVersion,
                         boolean modded, long lastPlayed, long sizeBytes, boolean sizeComplete, List<String> dataPacks, boolean cheats, boolean hardcore, boolean quickPlayEligible, boolean metadataAvailable) {
            this.id = id; this.name = name; this.serverProfileId = serverProfileId; this.clientProfileId = clientProfileId; this.directory = directory;
            this.thumbnail = thumbnail; this.running = running; this.local = local; this.gameVersion = gameVersion == null ? "" : gameVersion;
            this.modded = modded; this.lastPlayed = lastPlayed; this.sizeBytes = sizeBytes; this.sizeComplete = sizeComplete;
            this.dataPacks = Collections.unmodifiableList(new java.util.ArrayList<>(dataPacks == null ? Collections.emptyList() : dataPacks));
            this.cheats = cheats; this.hardcore = hardcore; this.quickPlayEligible = quickPlayEligible; this.metadataAvailable = metadataAvailable;
        }
        @Override public String toString() { return name; }
    }

    final class SettingsInfo {
        public String java8 = "", java17 = "", java21 = "", defaultProfile = "", account = "Offline", microsoftClientId = "";
        public Map<Integer, String> javaPaths = new LinkedHashMap<>();
        public int port = 25565;
        public boolean upnp = true;
        public boolean rememberAccount = false;
        public boolean fullscreen = true;
    }
}
