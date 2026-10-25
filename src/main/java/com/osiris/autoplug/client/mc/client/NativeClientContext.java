package com.osiris.autoplug.client.mc.client;

import com.osiris.autoplug.client.mc.launch.*;
import com.osiris.autoplug.client.mc.profiles.*;
import com.osiris.autoplug.client.mc.sharing.UpnpPortMapper;
import com.osiris.autoplug.client.mc.update.*;
import com.osiris.autoplug.client.mc.worlds.*;
import com.osiris.autoplug.client.utils.MineStat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One shared context for desktop and CLI. Construction performs no network requests. */
public final class NativeClientContext implements AutoCloseable {
    /** Injectable process boundary; tests need not start Java or Minecraft. */
    public interface ClientLaunch extends AutoCloseable {
        Process start() throws IOException;
        @Override void close() throws IOException;
    }
    public interface LaunchPreparation {
        ClientLaunch prepare(MinecraftLauncher launcher, String version, Path directory, MinecraftSession session,
                             String host, int port, Cancellation cancellation) throws IOException;
    }
    public interface WorldSessionsFactory {
        WorldSessionManager create(WorldRepository repository);
    }
    private static final LaunchPreparation DEFAULT_PREPARATION = (launcher, version, directory, session, host, port, cancellation) -> {
        PreparedLaunch prepared = launcher.prepare(version, directory, session, host, port, cancellation);
        return new ClientLaunch() {
            public Process start() throws IOException { return prepared.start(); }
            public void close() throws IOException { prepared.close(); }
        };
    };
    public static final class ServerStatus {
        private final boolean online;
        private final String version, motd;
        private final int players, maximum;
        private final long latency;
        ServerStatus(MineStat status) {
            online = status.isServerUp(); version = status.getVersion(); motd = status.getStrippedMotd();
            players = status.getCurrentPlayers(); maximum = status.getMaximumPlayers(); latency = status.getLatency();
        }
        public boolean isOnline() { return online; }
        public String getVersion() { return version; }
        public String getMotd() { return motd; }
        @Override public String toString() { return online ? version + " | " + players + "/" + maximum + " | " + latency + "ms | " + motd : "Offline / status unavailable"; }
    }

    private final Path dataRoot, workingDirectory;
    private final ClientSettings settings;
    private final ProfileRepository profiles;
    private final SavedServerRepository servers;
    private final WorldRepository worlds;
    private final ProfileUpdateService updater;
    private final LaunchTransport transport;
    private final ArtifactCache artifacts;
    private final MojangMetadata metadata;
    private final WorldSessionManager worldSessions;
    private final ConcurrentHashMap<String, ClientLaunch> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Cancellation> operations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Cancellation> worldOperations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IOException> clientFailures = new ConcurrentHashMap<>();
    private final LaunchPreparation preparation;
    private final Object lifecycle = new Object();
    private final Object refreshLock = new Object();
    private volatile boolean closed;
    private volatile MicrosoftDeviceCodeAuth.AuthResult authentication;

    public NativeClientContext(Path dataRoot, Path workingDirectory) throws IOException {
        this(dataRoot, workingDirectory, new HttpLaunchTransport(), new LegacyProfileUpdateEngine());
    }

    /** Injectable transport/update boundary supports offline previews and deterministic tests. */
    public NativeClientContext(Path dataRoot, Path workingDirectory, LaunchTransport transport,
                               ProfileUpdateService.Engine engine) throws IOException {
        this(dataRoot, workingDirectory, transport, engine, DEFAULT_PREPARATION);
    }

    public NativeClientContext(Path dataRoot, Path workingDirectory, LaunchTransport transport,
                               ProfileUpdateService.Engine engine, LaunchPreparation preparation) throws IOException {
        this(dataRoot, workingDirectory, transport, engine, preparation, repository -> new WorldSessionManager(repository, new UpnpPortMapper()));
    }

    public NativeClientContext(Path dataRoot, Path workingDirectory, LaunchTransport transport,
                               ProfileUpdateService.Engine engine, LaunchPreparation preparation,
                               WorldSessionsFactory worldSessionsFactory) throws IOException {
        Files.createDirectories(dataRoot);
        this.dataRoot = dataRoot.toRealPath(); this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.preparation = java.util.Objects.requireNonNull(preparation, "preparation");
        settings = new ClientSettings(this.dataRoot);
        profiles = new ProfileRepository(this.dataRoot);
        servers = new SavedServerRepository(this.dataRoot);
        worlds = new WorldRepository(this.dataRoot, profiles);
        this.transport = transport;
        String configured = settings.get("cacheDirectory", "").trim();
        Path cacheRoot = configured.isEmpty() ? Paths.get(System.getProperty("user.home"), ".autoplug", "cache") : Paths.get(configured);
        artifacts = new ArtifactCache(cacheRoot.resolve("launcher"), transport);
        metadata = new MojangMetadata(transport, artifacts);
        updater = new ProfileUpdateService(profiles, new SharedArtifactCache(cacheRoot.resolve("profiles")), engine);
        worldSessions = java.util.Objects.requireNonNull(worldSessionsFactory.create(worlds), "worldSessions");
    }

    public Path getDataRoot() { return dataRoot; }
    public ClientSettings settings() { return settings; }
    public ProfileRepository profiles() { return profiles; }
    public SavedServerRepository servers() { return servers; }
    public WorldRepository worlds() { return worlds; }
    public ProfileUpdateService updater() { return updater; }
    public MojangMetadata metadata() { return metadata; }

    public ServerStatus ping(String address) {
        ServerAddress parsed = new ServerAddress(address);
        return new ServerStatus(new MineStat(parsed.getHost(), parsed.getPort(), 3, MineStat.Request.JSON));
    }

    public MicrosoftDeviceCodeAuth authenticator() {
        return new MicrosoftDeviceCodeAuth(settings.get("clientId", ""), transport);
    }
    public void signedIn(MicrosoftDeviceCodeAuth.AuthResult result) {
        synchronized (lifecycle) {
            if (closed) throw new IllegalStateException("Client context is closed");
            authentication = java.util.Objects.requireNonNull(result, "result");
        }
    }
    public void signOut() { synchronized (lifecycle) { authentication = null; } }
    public String getAccountLabel() {
        MicrosoftDeviceCodeAuth.AuthResult current = authentication;
        return current == null ? "Not signed in" : current.getSession().getUsername();
    }

    public MinecraftLauncher launcher() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String platform = os.contains("win") ? "windows" : os.contains("mac") ? "osx" : "linux";
        return new MinecraftLauncher(artifacts, metadata,
                new LaunchPlatform(platform, System.getProperty("os.arch"), System.getProperty("os.version"), Collections.emptyMap()),
                new JavaRuntimeSelector(settings.javaHomes(workingDirectory), platform.equals("windows")));
    }

    public MinecraftSession session(Cancellation cancellation) throws IOException {
        ensureOpen(); cancellation.check();
        if (Boolean.parseBoolean(settings.get("offlineFixtureMode", "false"))) {
            return launcher().offlineSession(settings.get("username", "Player"));
        }
        synchronized (refreshLock) {
            MicrosoftDeviceCodeAuth.AuthResult result = authentication;
            if (result == null) throw new IOException("Sign in with Microsoft first, or explicitly select offline fixture mode for local tests");
            if (!java.time.Instant.now().isBefore(result.getExpiresAt().minusSeconds(30))) {
                MicrosoftDeviceCodeAuth.AuthResult refreshed = authenticator().refresh(result.getRefreshToken(), cancellation);
                synchronized (lifecycle) {
                    ensureOpen(); cancellation.check();
                    if (authentication != result) throw new IOException("Account changed during refresh; sign in again");
                    authentication = refreshed; result = refreshed;
                }
            }
            return result.getSession();
        }
    }

    public Process launch(String profileId, String serverAddress, Cancellation cancellation) throws IOException {
        return launchWithLease(profileId, serverAddress, cancellation, null);
    }

    private Process launchWithLease(String profileId, String serverAddress, Cancellation cancellation,
                                    ProfileRepository.UseLease suppliedLease) throws IOException {
        try { beginOperation(operations, profileId, cancellation); }
        catch (IOException | RuntimeException error) {
            if (suppliedLease != null) suppliedLease.close();
            throw error;
        }
        ProfileRepository.UseLease lease = suppliedLease;
        ClientLaunch prepared = null;
        Process process = null;
        try {
            if (lease == null) lease = profiles.acquireUse(profileId);
            Profile profile = profiles.require(profileId); // Read metadata only after acquiring its lease.
            if (profile.getType() != ProfileType.MODS || profile.isTemplate()) throw new IOException("Select a non-template client-mod profile");
            ServerAddress server = serverAddress == null || serverAddress.isEmpty() ? null : new ServerAddress(serverAddress);
            prepared = preparation.prepare(launcher(), launchVersion(profile), profile.getDirectory(), session(cancellation),
                    server == null ? null : server.getHost(), server == null ? 25565 : server.getPort(), cancellation);
            if (prepared == null) throw new IOException("Launch preparation returned no handle");
            synchronized (lifecycle) {
                ensureOpen(); cancellation.check();
                if (running.putIfAbsent(profileId, prepared) != null) throw new IOException("This client profile is already starting or running");
            }
            process = prepared.start();
            if (process == null) throw new IOException("Launcher returned no process");
            ensureOpen(); cancellation.check();
            monitorClient(profileId, cancellation, prepared, lease, process);
            return process;
        } catch (IOException | RuntimeException error) {
            if (prepared != null) {
                try { prepared.close(); } catch (IOException | RuntimeException cleanup) {
                    error.addSuppressed(cleanup);
                    clientFailures.put(profileId, cleanup instanceof IOException ? (IOException) cleanup : new IOException("Client cleanup failed", cleanup));
                }
            }
            if (process != null && process.isAlive() && prepared != null && lease != null) {
                // Cancellation after start can fail to kill an owned process. Preserve its
                // handle and profile lease so Stop can retry while the exit monitor waits.
                monitorClient(profileId, cancellation, prepared, lease, process);
                throw error;
            }
            if (prepared != null) running.remove(profileId, prepared);
            operations.remove(profileId, cancellation);
            if (lease != null) lease.close(); throw error;
        }
    }

    private void monitorClient(String profileId, Cancellation cancellation, ClientLaunch active,
                               ProfileRepository.UseLease activeLease, Process process) {
        Thread monitor = new Thread(() -> {
            boolean interrupted = false;
            while (process.isAlive()) {
                try { process.waitFor(); }
                catch (InterruptedException error) {
                    interrupted = true;
                    try { active.close(); }
                    catch (IOException cleanup) { clientFailures.put(profileId, cleanup); }
                    catch (RuntimeException cleanup) { clientFailures.put(profileId, new IOException("Client cleanup failed", cleanup)); }
                }
            }
            try { active.close(); }
            catch (IOException cleanup) { clientFailures.put(profileId, cleanup); }
            catch (RuntimeException cleanup) { clientFailures.put(profileId, new IOException("Client cleanup failed", cleanup)); }
            finally {
                running.remove(profileId, active); operations.remove(profileId, cancellation); activeLease.close();
                if (interrupted) Thread.currentThread().interrupt();
            }
        }, "AutoPlug-client-" + profileId);
        monitor.setDaemon(true); monitor.start();
    }

    private String launchVersion(Profile profile) throws IOException {
        String loader = profile.getLoader().toUpperCase(Locale.ROOT);
        if (loader.equals("VANILLA")) return profile.getGameVersion();
        PropertiesLoaderVersion installed = new PropertiesLoaderVersion(profile.getDirectory());
        String version = installed.read(profile.getGameVersion(), loader);
        if (version == null) throw new IOException("Install/import the official " + loader + " loader for this profile first");
        return version;
    }

    public void recordLoader(String profileId, String versionId) throws IOException {
        synchronized (lifecycle) {
            ensureOpen();
            synchronized (profiles) {
                profiles.requireNotInUse(profileId);
                Profile profile = profiles.require(profileId);
                new PropertiesLoaderVersion(profile.getDirectory()).write(profile.getGameVersion(), profile.getLoader(), versionId);
            }
        }
    }

    /** One lease covers vendor metadata/installer work and publishing its loader marker. */
    public String installLoader(String profileId, String loaderVersion, Cancellation cancellation) throws IOException {
        return loaderOperation(profileId, loaderVersion, cancellation, false);
    }

    /** Reuses only a matching marker and complete locally imported loader metadata. */
    public String ensureLoader(String profileId, Cancellation cancellation) throws IOException {
        return loaderOperation(profileId, null, cancellation, true);
    }

    private String loaderOperation(String profileId, String loaderVersion, Cancellation cancellation, boolean reuse) throws IOException {
        beginOperation(operations, profileId, cancellation);
        try (ProfileRepository.UseLease lease = profiles.acquireUse(profileId)) {
            Profile profile = profiles.require(profileId);
            if (profile.getType() != ProfileType.MODS || profile.isTemplate()) throw new IOException("Select a non-template client profile");
            String loader = profile.getLoader().toUpperCase(Locale.ROOT);
            cancellation.check();
            if (reuse && !loader.equals("VANILLA")) {
                String installed = new PropertiesLoaderVersion(profile.getDirectory()).read(profile.getGameVersion(), loader);
                if (installed != null && cachedLoaderMatches(installed, profile.getGameVersion())) return installed;
            }
            String installed;
            if (loader.equals("VANILLA")) installed = profile.getGameVersion();
            else if (loader.equals("FABRIC") || loader.equals("QUILT")) {
                String selected = loaderVersion == null || loaderVersion.isEmpty()
                        ? latestLightweightLoader(loader, profile.getGameVersion(), cancellation) : loaderVersion;
                installed = loader.equals("FABRIC") ? metadata.installFabric(profile.getGameVersion(), selected, cancellation)
                        : metadata.installQuilt(profile.getGameVersion(), selected, cancellation);
            } else if (loader.equals("FORGE") || loader.equals("NEOFORGE")) {
                installed = loaderInstaller().install(loader, profile.getGameVersion(), loaderVersion, profile.getDirectory(), cancellation);
            } else throw new IOException("Unsupported client loader: " + loader);
            synchronized (lifecycle) {
                ensureOpen(); cancellation.check();
                new PropertiesLoaderVersion(profile.getDirectory()).write(profile.getGameVersion(), loader, installed);
            }
            return installed;
        } finally { operations.remove(profileId, cancellation); }
    }

    private boolean cachedLoaderMatches(String installed, String game) throws IOException {
        if (!installed.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,159}")) return false;
        Path file = artifacts.resolve("versions/" + installed + "/" + installed + ".json");
        Path marker = file.resolveSibling("autoplug-loader-profile");
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(marker, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > 8 * 1024 * 1024) return false;
        if (!file.toRealPath().equals(file.toAbsolutePath().normalize())
                || !marker.toRealPath().equals(marker.toAbsolutePath().normalize())) throw new IOException("Linked loader metadata is not supported");
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return installed.equals(json.get("id").getAsString()) && game.equals(json.get("inheritsFrom").getAsString())
                    && !json.get("mainClass").getAsString().trim().isEmpty() && json.get("libraries").isJsonArray();
        } catch (RuntimeException invalid) { return false; }
    }

    public OfficialLoaderInstaller loaderInstaller() {
        return new OfficialLoaderInstaller(artifacts, metadata, transport,
                new JavaRuntimeSelector(settings.javaHomes(workingDirectory), System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")));
    }

    private String latestLightweightLoader(String loader, String game, Cancellation cancellation) throws IOException {
        String prefix = loader.equals("FABRIC") ? "https://meta.fabricmc.net/v2/versions/loader/" : "https://meta.quiltmc.org/v3/versions/loader/";
        Path temporary = Files.createTempFile(dataRoot, ".loader-list-", ".json");
        try {
            transport.download(java.net.URI.create(prefix + game), temporary, cancellation);
            cancellation.check();
            if (Files.size(temporary) > 2 * 1024 * 1024) throw new IOException("Loader version list exceeds limit");
            com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(new String(Files.readAllBytes(temporary), java.nio.charset.StandardCharsets.UTF_8));
            if (!json.isJsonArray()) throw new IOException("Invalid official loader version list");
            for (com.google.gson.JsonElement item : json.getAsJsonArray()) {
                com.google.gson.JsonObject details = item.getAsJsonObject().getAsJsonObject("loader");
                String version = details.get("version").getAsString();
                boolean stable = details.has("stable") ? details.get("stable").getAsBoolean()
                        : !version.toLowerCase(Locale.ROOT).matches(".*(?:alpha|beta|snapshot|rc).*?");
                if (stable && version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,199}")) return version;
            }
            throw new IOException("No stable official loader version found for this Minecraft version");
        } catch (RuntimeException error) { throw new IOException("Invalid official loader version list", error); }
        finally { Files.deleteIfExists(temporary); }
    }

    public void stopClient(String profileId) throws IOException {
        IOException failure = cancel(operations.get(profileId), null);
        ClientLaunch prepared = running.get(profileId);
        try { if (prepared != null) prepared.close(); }
        catch (IOException error) { failure = combine(failure, error); }
        if (failure != null) throw failure;
    }

    public WorldSessionManager.Session playWorld(String worldId, String clientProfileId, Path serverJar,
                                                boolean share, Cancellation cancellation) throws IOException, InterruptedException {
        beginOperation(worldOperations, worldId, cancellation);
        ProfileRepository.UseLease serverLease = null, clientLease = null;
        boolean worldStartInvoked = false;
        java.util.concurrent.atomic.AtomicBoolean clientHandedOff = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            World world = worlds.find(worldId).orElseThrow(() -> new IOException("Unknown world: " + worldId));
            WorldRepository.requireEulaAccepted(world);
            serverLease = profiles.acquireUse(world.getProfileId());
            clientLease = profiles.acquireUse(clientProfileId);
            Profile serverProfile = profiles.require(world.getProfileId());
            Profile clientProfile = profiles.require(clientProfileId);
            validateWorldProfiles(serverProfile, clientProfile);
            Path jar = validatedServerJar(serverJar);
            com.google.gson.JsonObject version = metadata.version(serverProfile.getGameVersion(), cancellation);
            int javaMajor = version.has("javaVersion") ? version.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
            Path java = new JavaRuntimeSelector(settings.javaHomes(workingDirectory), System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")).select(javaMajor);
            List<String> command = new ArrayList<>(); command.add(java.toString()); command.add("-jar"); command.add(jar.toString());
            final ProfileRepository.UseLease clientUse = clientLease, serverUse = serverLease;
            ensureOpen(); cancellation.check();
            worldStartInvoked = true;
            WorldSessionManager.Session started = worldSessions.start(world, command,
                    (host, port) -> {
                        // From this call onward launchWithLease owns release, including its
                        // retained exit monitor when cancellation cannot terminate a client.
                        clientHandedOff.set(true);
                        Process process = launchWithLease(clientProfileId, host + ":" + port, cancellation, clientUse);
                        return process;
                    }, share, cancellation::isCancelled);
            started.onClose(() -> { serverUse.close(); worldOperations.remove(worldId, cancellation); });
            return started;
        } catch (IOException | InterruptedException | RuntimeException error) {
            WorldSessionManager.Session pending = worldStartInvoked ? worldSessions.getActive(worldId) : null;
            if (pending != null && serverLease != null) {
                final ProfileRepository.UseLease retained = serverLease;
                pending.onClose(() -> { retained.close(); worldOperations.remove(worldId, cancellation); });
            } else {
                if (serverLease != null) serverLease.close();
                worldOperations.remove(worldId, cancellation);
            }
            if (clientLease != null && !clientHandedOff.get()) clientLease.close();
            throw error;
        }
    }

    public static final class WorldLaunchConfig {
        private final String clientProfileId;
        private final Path serverJar;
        WorldLaunchConfig(String clientProfileId, Path serverJar) { this.clientProfileId = clientProfileId; this.serverJar = serverJar; }
        public String getClientProfileId() { return clientProfileId; }
        public Path getServerJar() { return serverJar; }
    }

    public void configureWorld(String worldId, String clientProfileId, Path serverJar) throws IOException {
        synchronized (lifecycle) {
            ensureOpen();
            if (worldOperations.containsKey(worldId) || worldSessions.getActive(worldId) != null) throw new IOException("Stop this world before changing launch settings");
            // WorldRepository itself takes its monitor before consulting profiles.
            // Do not invert that lock order by holding profiles while finding a world.
            World world = worlds.find(worldId).orElseThrow(() -> new IOException("Unknown world: " + worldId));
            synchronized (profiles) {
                validateWorldProfiles(profiles.require(world.getProfileId()), profiles.require(clientProfileId));
                java.util.Properties values = new java.util.Properties();
                values.setProperty("formatVersion", "1"); values.setProperty("clientProfileId", clientProfileId);
                values.setProperty("serverJar", validatedServerJar(serverJar).toString());
                new ClientStorage().write(world.getDirectory().resolve("world-launch.properties"), values);
            }
        }
    }

    public WorldLaunchConfig worldConfig(String worldId) throws IOException {
        ensureOpen();
        World world = worlds.find(worldId).orElseThrow(() -> new IOException("Unknown world: " + worldId));
        java.util.Properties values = new ClientStorage().read(world.getDirectory().resolve("world-launch.properties"));
        if (!"1".equals(values.getProperty("formatVersion"))) throw new IOException("Configure this world's client profile and installed server jar first");
        String client = values.getProperty("clientProfileId");
        try {
            validateWorldProfiles(profiles.require(world.getProfileId()), profiles.require(client));
            return new WorldLaunchConfig(client, validatedServerJar(Paths.get(values.getProperty("serverJar"))));
        } catch (IllegalArgumentException | NullPointerException error) { throw new IOException("Invalid world launch settings", error); }
    }

    public WorldSessionManager.Session playWorld(String worldId, boolean share, Cancellation cancellation) throws IOException, InterruptedException {
        WorldLaunchConfig config = worldConfig(worldId);
        return playWorld(worldId, config.getClientProfileId(), config.getServerJar(), share, cancellation);
    }

    private void validateWorldProfiles(Profile server, Profile client) throws IOException {
        if (client.getType() != ProfileType.MODS || client.isTemplate()) throw new IOException("Select a non-template client profile for this world");
        if (!server.getGameVersion().equals(client.getGameVersion())) throw new IOException("Client and server profiles must use the same Minecraft version");
        if (server.getType() == ProfileType.MODS_SERVER && !server.getLoader().equalsIgnoreCase(client.getLoader())) {
            throw new IOException("Client and modded-server profiles must use the same loader");
        }
    }

    private Path validatedServerJar(Path jar) throws IOException {
        if (jar == null || !Files.isRegularFile(jar, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("Select the official installed server jar for this world");
        return jar.toRealPath();
    }

    public WorldSessionManager.Session activeWorld(String id) { return worldSessions.getActive(id); }
    public void stopWorld(String id) throws IOException {
        IOException failure = cancel(worldOperations.get(id), null);
        try { worldSessions.cancel(id); } catch (IOException error) { failure = combine(failure, error); }
        if (failure != null) throw failure;
    }

    public IOException lastClientFailure(String profileId) { return clientFailures.get(profileId); }

    @Override public void close() throws IOException {
        synchronized (lifecycle) { closed = true; authentication = null; }
        IOException failure = null;
        for (Cancellation operation : operations.values()) failure = cancel(operation, failure);
        for (Cancellation operation : worldOperations.values()) failure = cancel(operation, failure);
        try { worldSessions.close(); } catch (IOException error) { failure = combine(failure, error); }
        for (ClientLaunch launch : running.values()) {
            try { launch.close(); } catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            catch (RuntimeException error) { failure = combine(failure, new IOException("Client cleanup failed", error)); }
        }
        if (failure != null) throw failure;
    }

    private void ensureOpen() throws IOException { if (closed) throw new IOException("Client context is closed"); }

    private void beginOperation(ConcurrentHashMap<String, Cancellation> registry, String id, Cancellation cancellation) throws IOException {
        java.util.Objects.requireNonNull(cancellation, "cancellation");
        synchronized (lifecycle) {
            ensureOpen(); cancellation.check();
            if (id == null || registry.putIfAbsent(id, cancellation) != null) throw new IOException("This profile/world already has an active operation");
            clientFailures.remove(id);
        }
    }

    private IOException cancel(Cancellation cancellation, IOException failure) {
        try { if (cancellation != null) cancellation.cancel(); }
        catch (RuntimeException error) { failure = combine(failure, new IOException("Cancellation callback failed", error)); }
        return failure;
    }

    private IOException combine(IOException first, IOException next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    private final class PropertiesLoaderVersion {
        private final Path file;
        PropertiesLoaderVersion(Path profile) { file = profile.resolve("loader.properties"); }
        String read(String game, String loader) throws IOException {
            java.util.Properties properties = new ClientStorage().read(file);
            return game.equals(properties.getProperty("game")) && loader.equalsIgnoreCase(properties.getProperty("loader")) ? properties.getProperty("versionId") : null;
        }
        void write(String game, String loader, String versionId) throws IOException {
            if (versionId == null || !versionId.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,199}")) throw new IOException("Invalid installed loader version ID");
            java.util.Properties properties = new java.util.Properties(); properties.setProperty("game", game); properties.setProperty("loader", loader); properties.setProperty("versionId", versionId);
            new ClientStorage().write(file, properties);
        }
    }
}
