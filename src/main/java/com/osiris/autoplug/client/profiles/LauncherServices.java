package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.browser.ServerBrowserService;
import com.osiris.autoplug.client.launcher.*;
import com.osiris.autoplug.client.ui.LauncherActions;
import com.osiris.autoplug.client.worlds.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Application boundary shared by the dashboard and CLI. */
public class LauncherServices implements LauncherActions, AutoCloseable {
    public class State {
        public SettingsInfo settings = new SettingsInfo();
        public String offlineName = "Player", selectedAccount = "";
    }
    private final Path root;
    private final Consumer<String> progress;
    private final ProfileStore profiles;
    private final ProfileUpdates updates;
    private final WorldStore worlds;
    private final WorldService worldService;
    private final ServerBrowserService servers;
    private final JavaRuntimeManager runtimes;
    private final MinecraftLauncher launcher;
    private final AccountStore accounts;
    private final MicrosoftAccountService microsoft = new MicrosoftAccountService();
    private final Map<String, Process> clients = new ConcurrentHashMap<>();
    private final Map<String, ProfileUpdates.Plan> plans = new ConcurrentHashMap<>();
    private final JsonFiles json = new JsonFiles();
    private State state;
    private volatile MinecraftAccount account;
    // World preparation and its client callback run on the requesting worker. Keep
    // its identity stable if a settings worker switches accounts during installation.
    private final ThreadLocal<MinecraftAccount> worldAccount = new ThreadLocal<>();

    public LauncherServices(Path root, Consumer<String> progress) throws Exception {
        this.root = root.toAbsolutePath().normalize(); this.progress = progress == null ? ignored -> { } : progress;
        Files.createDirectories(this.root);
        profiles = new ProfileStore(this.root.resolve("profiles")); updates = new ProfileUpdates(profiles);
        worlds = new WorldStore(this.root.resolve("worlds"));
        runtimes = new JavaRuntimeManager(this.root.resolve("cache/runtimes"));
        launcher = new MinecraftLauncher(this.root.resolve("cache"), runtimes);
        accounts = new AccountStore(this.root.resolve("accounts.json"));
        state = Files.exists(this.root.resolve("settings.json")) ? json.read(this.root.resolve("settings.json"), State.class) : new State();
        if (state.settings == null) state.settings = new SettingsInfo();
        if (state.settings.javaPaths == null) state.settings.javaPaths = new LinkedHashMap<>();
        account = MinecraftAccount.offline(state.offlineName == null ? "Player" : state.offlineName);
        if (state.settings.rememberAccount) for (MinecraftAccount saved : accounts.list()) if (saved.uuid.equals(state.selectedAccount)) account = saved;
        if (!state.settings.rememberAccount) forgetStoredAccounts();
        applyRuntimeSettings(state.settings);
        servers = new ServerBrowserService(this.root.resolve("servers.json"), ServerBrowserService.defaults().vanillaFile());
        MinecraftServerInstaller installer = new MinecraftServerInstaller(profiles.getCache(),
                version -> runtimes.resolve(launcher.requiredJavaMajor(version, this.progress), this.progress), this.progress,
                (profile, directory) -> {
                    PreparedLaunch prepared = launcher.prepareServer(profile.gameVersion, profile.loader, profile.loaderVersion, directory, this.progress);
                    return new ServerLaunch(prepared.command(), prepared.gameDir);
                });
        worldService = new WorldService(profiles, worlds, installer, (profile, host, port) -> launchClient(profile.id, host, port));
        worldService.setPreferredPort(state.settings.port);
        worldService.setSharingEnabled(state.settings.upnp);
    }
    public Path getRoot() { return root; }
    public ProfileStore getProfiles() { return profiles; }
    public WorldService getWorldService() { return worldService; }
    public ServerBrowserService getServers() { return servers; }
    @Override public List<ProfileInfo> profiles() throws Exception {
        List<ProfileInfo> result = new ArrayList<>(); for (Profile p : profiles.list()) result.add(info(p)); return result;
    }
    @Override public ProfileInfo createProfile(String name, String version, String loader, String type, boolean template) throws Exception {
        Profile p = profiles.create(name, version, loader, ProfileType.valueOf(type.toUpperCase(Locale.ROOT).replace('-', '_')));
        p.template = template; profiles.save(p); return info(p);
    }
    @Override public ProfileInfo cloneProfile(String sourceId, String name, String targetVersion, String loader) throws Exception {
        requireIdle(sourceId);
        Profile p = profiles.cloneProfile(sourceId, name, targetVersion, loader);
        if (p.migrationPending) { p.migrationSummary = checkProfile(p.id); profiles.save(p); }
        return info(p);
    }
    @Override public void deleteProfile(String id) throws Exception {
        requireIdle(id);
        for (VirtualWorld w : worlds.list()) if (id.equals(w.serverProfileId) || id.equals(w.clientProfileId))
            throw new IOException("Profile is referenced by world '" + w.name + "'. Change or remove that world first.");
        profiles.delete(id); plans.remove(id);
    }
    @Override public String checkProfile(String id) throws Exception {
        requireIdle(id); ProfileUpdates.Plan plan = updates.check(id); plans.put(id, plan); return plan.summary();
    }
    @Override public String updateProfile(String id) throws Exception {
        requireIdle(id); ProfileUpdates.Plan plan = plans.remove(id);
        if (plan == null) throw new IOException("Check this profile and review its update plan before applying");
        return updates.apply(plan, true);
    }
    @Override public void setTemplate(String id, boolean template) throws Exception {
        Profile p = profiles.get(id);
        try (ProfileLease ignored = new ProfileLease(p.getDirectory())) { p.template = template; profiles.save(p); }
    }
    public void addArtifact(String id, Path source, String modrinthId) throws Exception {
        requireIdle(id); Profile p = profiles.get(id);
        if (!source.getFileName().toString().endsWith(".jar")) throw new IOException("Select a JAR artifact");
        try (ZipFile ignored = new ZipFile(source.toFile()); ProfileLease lease = new ProfileLease(p.getDirectory())) {
            Path destination = new ProfileCollection().resolve(p, source.getFileName().toString());
            if (Files.exists(destination)) throw new IOException("That artifact filename already exists in this profile");
            profiles.getCache().link(profiles.getCache().store(source), destination);
            ProfileCollection collection = new ProfileCollection().scan(p);
            for (CollectionEntry entry : collection.entries) if (entry.file.equals(source.getFileName().toString()) && modrinthId != null) entry.modrinthId = modrinthId;
            collection.save(p); plans.remove(id);
        }
    }
    @Override public void addArtifact(String id, String source, String modrinthId) throws Exception {
        addArtifact(id, Paths.get(source), modrinthId == null || modrinthId.trim().isEmpty() ? null : modrinthId.trim());
    }
    @Override public List<WorldInfo> worlds() throws Exception {
        List<WorldInfo> result = new ArrayList<>(); for (VirtualWorld w : worlds.list()) result.add(info(w)); return result;
    }
    @Override public WorldInfo createWorld(String name, String serverProfileId, String clientProfileId) throws Exception {
        Profile server = profiles.get(serverProfileId), client = profiles.get(clientProfileId);
        if (server.type.isClient() || !client.type.isClient()) throw new IOException("Choose a server profile and a client profile");
        if (!server.gameVersion.equals(client.gameVersion)) throw new IOException("World server and client must use the same game version");
        if (client.template) throw new IOException("Clone the client template before attaching it to a world");
        if (server.type == ProfileType.MODS_SERVER && !server.loader.equals(client.loader)) throw new IOException("Modded world and client loaders must match");
        if (server.type == ProfileType.MODS_SERVER && server.loaderVersion != null && client.loaderVersion != null && !server.loaderVersion.equals(client.loaderVersion))
            throw new IOException("Pinned world and client loader versions must match");
        return info(worlds.create(name, serverProfileId, clientProfileId));
    }
    @Override public void setWorldEulaAccepted(String id, boolean accepted) throws Exception { worlds.setEulaAccepted(id, accepted); }
    @Override public void launchWorld(String id, boolean share) throws Exception {
        MinecraftAccount current = currentAccount();
        worldAccount.set(current);
        try {
            WorldSession session = worldService.launch(id, share, !current.offline);
            progress.accept("World ready on localhost:" + session.getPort());
            if (share) progress.accept(session.getShareResult().getMessage());
        } finally { worldAccount.remove(); }
    }
    @Override public String shareWorld(String id) {
        ShareResult result = worldService.shareWorld(id); return result.isShared() ? result.address + "\n" + result.message : result.message;
    }
    @Override public void launchProfile(String id, String host, int port) throws Exception { launchClient(id, host, port); }
    private Process launchClient(String id, String host, int port) throws Exception {
        Profile profile = profiles.get(id);
        if (!profile.type.isClient()) throw new IOException("Choose a MODS client profile");
        if (profile.template) throw new IOException("Clone the template before launching it");
        if (profile.migrationPending) throw new IOException("Review and apply profile migration before launching");
        ProfileLease lease = new ProfileLease(profile.getDirectory());
        try {
            MinecraftAccount current = worldAccount.get();
            if (current == null) current = currentAccount();
            else if (current.needsRefresh()) current = microsoft.refresh(current);
            PreparedLaunch prepared = launcher.prepare(new LaunchRequest(profile.getDirectory(), profile.gameVersion, profile.loader, profile.loaderVersion, current, host, port), progress);
            Process process = launcher.launch(prepared); clients.put(id, process);
            process.onExit().thenRun(() -> { clients.remove(id, process); try { lease.close(); } catch (IOException e) { progress.accept("Could not release profile lock: " + e.getMessage()); } });
            return process;
        } catch (Exception e) { lease.close(); throw e; }
    }
    private synchronized MinecraftAccount currentAccount() throws Exception {
        if (account.needsRefresh()) {
            account = microsoft.refresh(account);
            if (state.settings.rememberAccount) accounts.save(account);
        }
        return account;
    }
    @Override public synchronized SettingsInfo settings() {
        SettingsInfo copy = new SettingsInfo();
        copy.java8 = state.settings.java8; copy.java17 = state.settings.java17; copy.java21 = state.settings.java21;
        copy.javaPaths = new LinkedHashMap<>(state.settings.javaPaths); copy.defaultProfile = state.settings.defaultProfile;
        copy.port = state.settings.port; copy.upnp = state.settings.upnp; copy.microsoftClientId = state.settings.microsoftClientId;
        copy.rememberAccount = state.settings.rememberAccount; copy.account = account.username + (account.offline ? " (offline)" : " (Microsoft)"); return copy;
    }
    @Override public synchronized void saveSettings(SettingsInfo settings) throws Exception {
        if (settings.port < 1 || settings.port > 65535) throw new IOException("Port must be between 1 and 65535");
        if (settings.defaultProfile != null && !settings.defaultProfile.isEmpty()) profiles.get(settings.defaultProfile);
        for (Integer major : state.settings.javaPaths.keySet()) runtimes.setRuntime(major, null);
        applyRuntimeSettings(settings);
        worldService.setPreferredPort(settings.port);
        worldService.setSharingEnabled(settings.upnp);
        if (!settings.rememberAccount) forgetStoredAccounts();
        state.settings = settings; saveState();
    }
    private void applyRuntimeSettings(SettingsInfo settings) {
        if (settings.javaPaths == null) settings.javaPaths = new LinkedHashMap<>();
        Map<Integer, String> values = new LinkedHashMap<>(settings.javaPaths);
        values.put(8, settings.java8); values.put(17, settings.java17); values.put(21, settings.java21);
        for (Map.Entry<Integer, String> value : values.entrySet()) {
            if (value.getKey() < 8) throw new IllegalArgumentException("Java major must be at least 8");
            String path = value.getValue(); runtimes.setRuntime(value.getKey(), path == null || path.trim().isEmpty() ? null : Paths.get(path));
        }
    }
    @Override public void signInMicrosoft(Consumer<String> instructions) throws Exception {
        String clientId; synchronized (this) { clientId = state.settings.microsoftClientId; }
        MicrosoftAccountService.DeviceLogin device = microsoft.beginLogin(clientId); instructions.accept(device.message);
        MinecraftAccount signedIn = microsoft.completeLogin(device);
        synchronized (this) {
            account = signedIn; state.selectedAccount = account.uuid;
            if (state.settings.rememberAccount) accounts.save(account);
            saveState();
        }
        instructions.accept("Signed in as " + signedIn.username);
    }
    @Override public synchronized void useOfflineAccount(String name) throws Exception {
        account = MinecraftAccount.offline(name); state.offlineName = name; state.selectedAccount = ""; saveState();
    }
    private void saveState() throws IOException { json.write(root.resolve("settings.json"), state); }
    private void forgetStoredAccounts() throws IOException { for (MinecraftAccount saved : accounts.list()) accounts.remove(saved.uuid); }
    private void requireIdle(String id) throws IOException {
        if (clients.containsKey(id) || worldService.isProfileInUse(id)) throw new IOException("Stop the running client/world before changing this profile");
    }
    private ProfileInfo info(Profile p) { return new ProfileInfo(p.id, p.name, p.gameVersion, p.loader, p.type.name(), p.getDirectory().toString(), p.template, p.migrationSummary == null ? "" : p.migrationSummary, !p.migrationPending); }
    private WorldInfo info(VirtualWorld w) throws IOException {
        boolean running = worldService.activeSessions().stream().anyMatch(s -> s.getWorld().id.equals(w.id));
        return new WorldInfo(w.id, w.name, w.serverProfileId, w.clientProfileId, worlds.getDirectory(w.id).toString(), w.thumbnail, running);
    }
    public void waitForSessions() throws InterruptedException {
        while (!clients.isEmpty() || !worldService.activeSessions().isEmpty()) Thread.sleep(250);
    }
    @Override public void close() {
        worldService.close();
        for (Process process : clients.values()) if (process.isAlive()) process.destroy();
    }
}
