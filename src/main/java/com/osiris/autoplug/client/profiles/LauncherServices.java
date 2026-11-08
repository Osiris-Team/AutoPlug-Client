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
        public Map<String, String> launchChoices = new LinkedHashMap<>();
    }
    private final Path root;
    private final Consumer<String> progress;
    private volatile Consumer<String> progressListener = ignored -> { };
    private final ProfileStore profiles;
    private final ProfileUpdates updates;
    private final FabricDefaultProfile fabricDefaults;
    private final WorldStore worlds;
    private final LocalWorldStore localWorlds;
    private final WorldService worldService;
    private final ServerBrowserService servers;
    private final JavaRuntimeManager runtimes;
    private final MinecraftLauncher launcher;
    private final AccountStore accounts;
    private final MicrosoftAccountService microsoft = new MicrosoftAccountService();
    private final Map<String, Process> clients = new ConcurrentHashMap<>();
    private final Map<String, Process> localClients = new ConcurrentHashMap<>();
    private final Map<String, ProfileUpdates.Plan> plans = new ConcurrentHashMap<>();
    private final JsonFiles json = new JsonFiles();
    private State state;
    private volatile MinecraftAccount account;
    // World preparation and its client callback run on the requesting worker. Keep
    // its identity stable if a settings worker switches accounts during installation.
    private final ThreadLocal<MinecraftAccount> worldAccount = new ThreadLocal<>();

    public LauncherServices(Path root, Consumer<String> progress) throws Exception {
        this(root, LocalWorldStore.defaultMinecraftDirectory(), progress);
    }
    /** An explicit Minecraft directory keeps local-save discovery testable without touching user saves. */
    public LauncherServices(Path root, Path minecraftDirectory, Consumer<String> progress) throws Exception {
        this.root = root.toAbsolutePath().normalize();
        Consumer<String> log = progress == null ? ignored -> { } : progress;
        this.progress = message -> {
            try { log.accept(message); } catch (RuntimeException ignored) { }
            try { progressListener.accept(message); } catch (RuntimeException ignored) { }
        };
        Files.createDirectories(this.root);
        profiles = new ProfileStore(this.root.resolve("profiles")); updates = new ProfileUpdates(profiles);
        fabricDefaults = new FabricDefaultProfile(profiles, updates);
        worlds = new WorldStore(this.root.resolve("worlds"));
        localWorlds = new LocalWorldStore(minecraftDirectory, this.progress);
        runtimes = new JavaRuntimeManager(this.root.resolve("cache/runtimes"));
        launcher = new MinecraftLauncher(this.root.resolve("cache"), runtimes);
        accounts = new AccountStore(this.root.resolve("accounts.json"));
        state = Files.exists(this.root.resolve("settings.json")) ? json.read(this.root.resolve("settings.json"), State.class) : new State();
        if (state.settings == null) state.settings = new SettingsInfo();
        if (state.launchChoices == null) state.launchChoices = new LinkedHashMap<>();
        if (state.settings.javaPaths == null) state.settings.javaPaths = new LinkedHashMap<>();
        account = MinecraftAccount.offline(state.offlineName == null ? "Player" : state.offlineName);
        if (state.settings.rememberAccount) for (MinecraftAccount saved : accounts.list()) if (saved.uuid.equals(state.selectedAccount)) account = saved;
        if (!state.settings.rememberAccount) forgetStoredAccounts();
        applyRuntimeSettings(state.settings);
        servers = new ServerBrowserService(this.root.resolve("servers.json"), minecraftDirectory.resolve("servers.dat"));
        MinecraftServerInstaller installer = new MinecraftServerInstaller(profiles.getCache(),
                version -> runtimes.resolve(launcher.requiredJavaMajor(version, this.progress), this.progress), this.progress,
                (profile, directory) -> {
                    PreparedLaunch prepared = launcher.prepareServer(profile.gameVersion, profile.loader, profile.loaderVersion, directory, this.progress);
                    return new ServerLaunch(prepared.command(), prepared.gameDir);
                });
        worldService = new WorldService(profiles, worlds, installer, (profile, host, port) -> launchClient(profile.id, host, port));
        worldService.setPreferredPort(state.settings.port);
        worldService.setSharingEnabled(state.settings.upnp);
        ensureDefaultProfiles("");
    }
    public Path getRoot() { return root; }
    public ProfileStore getProfiles() { return profiles; }
    public WorldService getWorldService() { return worldService; }
    public ServerBrowserService getServers() { return servers; }
    @Override public void onProgress(Consumer<String> listener) { progressListener = listener == null ? ignored -> { } : listener; }
    @Override public synchronized ProfileInfo ensureDefaultProfiles(String version) throws Exception {
        Profile selected = selectedDefault();
        String target = version == null ? "" : version.trim();
        if (target.isEmpty()) {
            Profile configured = configuredDefault();
            if (configured != null) target = configured.gameVersion;
            else {
                for (Profile profile : profiles.list()) if (profile.type.isClient() && !profile.template && !profile.migrationPending) { target = profile.gameVersion; break; }
                if (target.isEmpty()) target = "1.21.1"; // Same initial version as the profile creation form; no network needed.
            }
        }
        Profile client = profiles.ensureDefault(target, ProfileType.MODS);
        profiles.ensureDefault(target, ProfileType.MODS_SERVER);
        profiles.ensureFabricDefault(target);
        if (selected == null) { state.settings.defaultProfile = client.id; saveState(); }
        return info(client);
    }
    private Profile selectedDefault() {
        Profile profile = configuredDefault();
        return profile != null && !profile.template && !profile.migrationPending ? profile : null;
    }
    private Profile configuredDefault() {
        String id = state.settings.defaultProfile;
        if (id == null || id.isEmpty()) return null;
        try { Profile profile = profiles.get(id); return profile.type.isClient() ? profile : null; }
        catch (IOException e) { return null; }
    }
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
        List<WorldInfo> result = new ArrayList<>();
        Map<String, Long> activity = new HashMap<>();
        try {
            for (LocalWorld world : localWorlds.list()) {
                LocalWorld owned = world;
                try { owned = worlds.ownedLocal(world, false); }
                catch (IOException e) { progress.accept("Could not load ownership for '" + world.name + "': " + e.getMessage()); }
                WorldInfo view = info(owned); result.add(view);
                activity.put(view.id, worldActivity(view, 0));
            }
        } catch (IOException | SecurityException e) {
            progress.accept("Could not list local Minecraft saves: " + e.getMessage());
        }
        for (VirtualWorld w : worlds.list()) {
            WorldInfo view = info(w); result.add(view);
            activity.put(view.id, worldActivity(view, w.createdAt));
        }
        result.sort(Comparator.<WorldInfo>comparingLong(world -> activity.getOrDefault(world.id, 0L)).reversed()
                .thenComparing(world -> world.name, String.CASE_INSENSITIVE_ORDER).thenComparing(world -> world.id));
        return result;
    }
    /** A bounded background metadata read: creation, modification and LastPlayed all count as activity. */
    static long worldActivity(WorldInfo world, long createdAt) {
        long latest = Math.max(Math.max(0, createdAt), world.lastPlayed);
        try {
            Path directory = Paths.get(world.directory);
            for (Path path : Arrays.asList(directory, directory.resolve("level.dat"), directory.resolve("world/level.dat"))) {
                try {
                    java.nio.file.attribute.BasicFileAttributes attributes = Files.readAttributes(path,
                            java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attributes.isSymbolicLink()) continue;
                    latest = Math.max(latest, Math.max(attributes.creationTime().toMillis(), attributes.lastModifiedTime().toMillis()));
                } catch (IOException | SecurityException ignored) { }
            }
        } catch (InvalidPathException | NullPointerException ignored) { }
        return latest;
    }
    @Override public String launchLocalWorld(String id, String version) throws Exception {
        return launchLocalWorld(id, version, "");
    }
    @Override public String launchLocalWorld(String id, String version, String profileId) throws Exception {
        LocalWorld world = worlds.getLocal(id, localWorlds);
        String selected = localWorlds.launchVersion(world, version);
        Profile chosen = profileId == null || profileId.isEmpty() ? profiles.ensureDefault(selected, ProfileType.MODS) : profiles.get(profileId);
        if (!chosen.type.isClient() || chosen.template || chosen.migrationPending || !"VANILLA".equals(chosen.loader) || !selected.equals(chosen.gameVersion))
            throw new IOException("Existing local saves require a ready Vanilla profile with exactly the save's selected version.");
        // All standard saves share one game directory and its settings/native files.
        // Retain one lease for that directory until the child exits, including preparation failures.
        ProfileLease lease = new ProfileLease(world.gameDirectory);
        try {
            MinecraftAccount current = currentAccount();
            PreparedLaunch prepared = launcher.prepare(new LaunchRequest(world.gameDirectory, selected, "VANILLA", null,
                    current, null, 0, world.saveName(), state.settings.fullscreen), progress);
            LocalWorld fresh = worlds.getLocal(id, localWorlds);
            if (!fresh.gameVersion.equals(world.gameVersion) || fresh.modded != world.modded)
                throw new IOException("The save's version metadata changed while preparing Minecraft. Refresh and select it again.");
            localWorlds.launchVersion(fresh, selected);
            LocalWorld owned = worlds.registerLocal(fresh);
            Process process = launcher.launch(prepared); trackLocalClient(owned.id, process, lease);
            try { rememberProfile("world:" + id, chosen.id); }
            catch (IOException e) { progress.accept("Minecraft started, but the launch choice could not be saved: " + e.getMessage()); }
            if (prepared.arguments.contains("--quickPlaySingleplayer"))
                return "Started Minecraft " + selected + " with Quick Play for '" + world.name + "'.";
            return "Started Minecraft " + selected + " with automatic entry for '" + world.name + "'.";
        } catch (Exception e) { lease.close(); throw e; }
    }
    /** Existing saves must be closed from Minecraft so its integrated server can save normally. */
    void trackLocalClient(String id, Process process, ProfileLease lease) {
        localClients.put(id, process);
        process.onExit().thenRun(() -> {
            localClients.remove(id, process);
            try { lease.close(); }
            catch (IOException e) { progress.accept("Could not release local Minecraft lock: " + e.getMessage()); }
        });
    }
    @Override public WorldInfo createWorld(String name, String serverProfileId, String clientProfileId) throws Exception {
        if (clientProfileId == null || clientProfileId.trim().isEmpty()) {
            String version = serverProfileId == null || serverProfileId.trim().isEmpty() ? "" : profiles.get(serverProfileId).gameVersion;
            clientProfileId = ensureDefaultProfiles(version).id;
        }
        if (serverProfileId == null || serverProfileId.trim().isEmpty())
            serverProfileId = profiles.ensureDefault(profiles.get(clientProfileId).gameVersion, ProfileType.MODS_SERVER).id;
        Profile server = profiles.get(serverProfileId), client = profiles.get(clientProfileId);
        if (server.type.isClient() || !client.type.isClient()) throw new IOException("Choose a server profile and a client profile");
        if (!server.gameVersion.equals(client.gameVersion)) throw new IOException("World server and client must use the same game version");
        if (client.template) throw new IOException("Clone the client template before attaching it to a world");
        if (server.type == ProfileType.MODS_SERVER && !"VANILLA".equals(server.loader) && !server.loader.equals(client.loader)) throw new IOException("Modded world and client loaders must match");
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
    @Override public synchronized void launchWorld(String id, boolean share, String profileId) throws Exception {
        VirtualWorld world = worlds.get(id);
        Profile client = profiles.get(profileId), server = profiles.get(world.serverProfileId);
        if (!client.type.isClient() || client.template || client.migrationPending || !client.gameVersion.equals(server.gameVersion)
                || (server.type == ProfileType.MODS_SERVER && !"VANILLA".equals(server.loader) && !server.loader.equals(client.loader)))
            throw new IOException("Choose a ready client matching this world's Minecraft version and mod loader.");
        if (!world.clientProfileId.equals(profileId) && !worldService.activeSessions().isEmpty()) {
            for (WorldSession session : worldService.activeSessions()) if (session.getWorld().id.equals(id))
                throw new IOException("Stop this world before changing its client profile.");
        }
        world.clientProfileId = profileId; worlds.save(world);
        launchWorld(id, share);
        try { rememberProfile("world:" + id, profileId); }
        catch (IOException e) { progress.accept("World started, but the launch choice could not be saved: " + e.getMessage()); }
    }
    @Override public synchronized String preferredProfile(String target) {
        String id = state.launchChoices.getOrDefault(target, "");
        if (id.isEmpty()) return "";
        try { Profile p = profiles.get(id); return p.type.isClient() && !p.template && !p.migrationPending ? id : ""; }
        catch (IOException e) { return ""; }
    }
    @Override public synchronized void rememberProfile(String target, String profileId) throws IOException {
        if (target == null || target.length() > 1024 || !(target.startsWith("server:") || target.startsWith("world:"))) throw new IOException("Invalid launch target");
        Profile profile = profiles.get(profileId);
        if (!profile.type.isClient() || profile.template || profile.migrationPending) throw new IOException("Choose a ready client profile");
        state.launchChoices.put(target, profileId); saveState();
    }
    @Override public String shareWorld(String id) {
        ShareResult result = worldService.shareWorld(id); return result.isShared() ? result.address + "\n" + result.message : result.message;
    }
    @Override public String serverLog(String id) throws Exception { return worldService.serverLog(id); }
    @Override public void serverCommand(String id, String command) throws Exception { worldService.serverCommand(id, command); }
    @Override public void stopWorld(String id) { worldService.stopWorld(id); }
    @Override public void restartWorld(String id) throws Exception { worldService.restartWorld(id); }
    @Override public void launchProfile(String id, String host, int port) throws Exception { launchClient(id, host, port); }
    private Process launchClient(String id, String host, int port) throws Exception {
        if (id == null || id.trim().isEmpty()) {
            synchronized (this) { Profile selected = selectedDefault(); id = selected == null ? ensureDefaultProfiles("").id : selected.id; }
        }
        Profile profile = profiles.get(id);
        if (!profile.type.isClient()) throw new IOException("Choose a MODS client profile");
        if (profile.template) throw new IOException("Clone the template before launching it");
        if (profile.migrationPending) throw new IOException("Review and apply profile migration before launching");
        ProfileLease lease = new ProfileLease(profile.getDirectory());
        try {
            fabricDefaults.prepare(profile, progress);
            MinecraftAccount current = worldAccount.get();
            if (current == null) current = currentAccount();
            else if (current.needsRefresh()) current = microsoft.refresh(current);
            PreparedLaunch prepared = launcher.prepare(new LaunchRequest(profile.getDirectory(), profile.gameVersion, profile.loader, profile.loaderVersion, current, host, port, state.settings.fullscreen), progress);
            Process process = launcher.launch(prepared); clients.put(profile.id, process);
            process.onExit().thenRun(() -> { clients.remove(profile.id, process); try { lease.close(); } catch (IOException e) { progress.accept("Could not release profile lock: " + e.getMessage()); } });
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
        copy.fullscreen = state.settings.fullscreen; copy.rememberAccount = state.settings.rememberAccount; copy.account = account.username + (account.offline ? " (offline)" : " (Microsoft)"); return copy;
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
        Path directory = worlds.getDirectory(w.id);
        if (Files.isRegularFile(directory.resolve("world/level.dat"), LinkOption.NOFOLLOW_LINKS)) {
            try {
                LocalWorld metadata = LocalWorldStore.inspect(w.id, directory.resolve("world"), directory);
                return new WorldInfo(w.id, w.name, w.serverProfileId, w.clientProfileId, directory.toString(), metadata.icon == null ? w.thumbnail : metadata.icon.toString(), running,
                        false, metadata.gameVersion, metadata.modded, metadata.lastPlayed, metadata.sizeBytes, metadata.sizeComplete,
                        metadata.dataPacks, metadata.cheats, metadata.hardcore, false);
            } catch (IOException e) { progress.accept("Could not read world details for '" + w.name + "': " + e.getMessage()); }
        }
        return new WorldInfo(w.id, w.name, w.serverProfileId, w.clientProfileId, directory.toString(), w.thumbnail, running, false, profiles.get(w.serverProfileId).gameVersion);
    }
    private WorldInfo info(LocalWorld world) {
        return new WorldInfo(world.id, world.name, "", "", world.directory.toString(), world.icon == null ? null : world.icon.toString(),
                localClients.containsKey(world.id), true, world.gameVersion, world.modded, world.lastPlayed, world.sizeBytes, world.sizeComplete,
                world.dataPacks, world.cheats, world.hardcore, world.quickPlayEligible);
    }
    public void waitForSessions() throws InterruptedException {
        while (!clients.isEmpty() || !localClients.isEmpty() || !worldService.activeSessions().isEmpty()) Thread.sleep(250);
    }
    @Override public void close() {
        worldService.close();
        for (Process process : clients.values()) if (process.isAlive()) process.destroy();
        // Never destroy localClients: abrupt termination can corrupt an original user's save.
        // Their exit callbacks retain/release the lease while this launcher JVM remains alive.
    }
}
