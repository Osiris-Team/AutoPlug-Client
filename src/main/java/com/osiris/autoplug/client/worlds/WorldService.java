package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.Profile;
import com.osiris.autoplug.client.profiles.ProfileStore;
import com.osiris.autoplug.client.profiles.ProfileType;
import com.osiris.autoplug.client.profiles.ProfileLease;
import com.osiris.autoplug.client.utils.MineStat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Local world orchestration independent of AutoPlug's legacy singleton server. */
public final class WorldService implements AutoCloseable {
    @FunctionalInterface public interface Probe { boolean isReady(String host, int port) throws Exception; }
    @FunctionalInterface public interface ProcessFactory { Process start(ServerLaunch launch) throws IOException; }
    @FunctionalInterface interface LaunchWrapper { ServerLaunch wrap(Profile profile, ServerLaunch launch) throws Exception; }

    private final ProfileStore profiles;
    private final WorldStore worlds;
    private final ServerInstaller installer;
    private final ClientLauncher launcher;
    private final SharingService sharing;
    private final Probe probe;
    private final ProcessFactory processes;
    private final Duration readyTimeout;
    private final Map<String, WorldSession> sessions = new ConcurrentHashMap<>();
    private final Thread shutdownHook;
    private boolean closed;
    private int preferredPort = 25565;
    private boolean sharingEnabled = true;
    private LaunchWrapper wrapper = (profile, launch) -> launch;

    public WorldService(ProfileStore profiles, WorldStore worlds, ServerInstaller installer, ClientLauncher launcher) {
        this(profiles, worlds, installer, launcher, new UpnpSharingService(),
                (host, port) -> new MineStat(host, port, 1, MineStat.Request.JSON).isServerUp(),
                launch -> new ProcessBuilder(launch.command).directory(launch.directory.toFile())
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(
                                launch.directory.resolve("autoplug-server.log").toFile())).start(), Duration.ofMinutes(3));
        wrapper = new AutoPlugWorldBootstrap()::wrap;
    }

    public WorldService(ProfileStore profiles, WorldStore worlds, ServerInstaller installer, ClientLauncher launcher,
                        SharingService sharing, Probe probe, ProcessFactory processes, Duration readyTimeout) {
        this.profiles = Objects.requireNonNull(profiles);
        this.worlds = Objects.requireNonNull(worlds);
        this.installer = Objects.requireNonNull(installer);
        this.launcher = Objects.requireNonNull(launcher);
        this.sharing = Objects.requireNonNull(sharing);
        this.probe = Objects.requireNonNull(probe);
        this.processes = Objects.requireNonNull(processes);
        this.readyTimeout = Objects.requireNonNull(readyTimeout);
        if (readyTimeout.isNegative() || readyTimeout.isZero()) throw new IllegalArgumentException("Readiness timeout must be positive");
        shutdownHook = new Thread(this::close, "AutoPlug-world-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public synchronized WorldSession launch(String worldId, boolean share) throws Exception {
        return launch(worldId, share, true);
    }

    public synchronized WorldSession launch(String worldId, boolean share, boolean authenticatedAccount) throws Exception {
        if (closed) throw new IllegalStateException("World service is closed");
        WorldSession existing = sessions.get(worldId);
        if (existing != null && !existing.isClosed()) {
            if (share) existing.share();
            return existing;
        }
        VirtualWorld world = worlds.get(worldId);
        if (!world.eulaAccepted) throw new IllegalStateException("Accept the Minecraft EULA for this world before launching: https://aka.ms/MinecraftEULA");
        Profile serverProfile = profiles.get(world.serverProfileId);
        Profile clientProfile = profiles.get(world.clientProfileId);
        validateProfiles(serverProfile, clientProfile);
        // The same client profile cannot be mutated or launched twice at the same time.
        for (WorldSession session : sessions.values()) {
            if (!session.isClosed() && world.clientProfileId.equals(session.getWorld().clientProfileId))
                throw new IllegalStateException("This client profile is already running another world");
        }
        Path directory = worlds.getDirectory(world.id);
        Files.createDirectories(directory);
        ProfileLease worldLease = new ProfileLease(directory);
        try { return startWorld(world, serverProfile, clientProfile, directory, worldLease, share, authenticatedAccount); }
        catch (Exception e) { try { worldLease.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); } throw e; }
    }

    private WorldSession startWorld(VirtualWorld world, Profile serverProfile, Profile clientProfile, Path directory,
                                    ProfileLease worldLease, boolean share, boolean authenticatedAccount) throws Exception {
        int port = availablePort(preferredPort);
        ServerLaunch launch = installer.prepare(serverProfile, directory);
        if (!directory.toAbsolutePath().normalize().equals(launch.directory)) throw new IOException("Installer returned a different world directory");
        configureLocalServer(directory, port, authenticatedAccount);
        launch = wrapper.wrap(serverProfile, launch);
        if (!directory.toAbsolutePath().normalize().equals(launch.directory)) throw new IOException("Wrapper returned a different world directory");
        ManagedServer server = new ManagedServer(processes.start(launch), 30000, launch.stopCommand, launch.restartCommand);
        Process client = null;
        try {
            long deadline = System.nanoTime() + readyTimeout.toNanos();
            boolean ready = false;
            while (System.nanoTime() < deadline) {
                if (!server.isAlive()) throw new IOException("Server exited before becoming ready; see " + directory.resolve("autoplug-server.log"));
                if (probe.isReady("127.0.0.1", port)) { ready = true; break; }
                Thread.sleep(200);
            }
            if (!ready) throw new IOException("Server readiness timed out; see " + directory.resolve("autoplug-server.log"));
            client = launcher.launch(clientProfile, "127.0.0.1", port);
            if (client == null) throw new IOException("Client launcher did not return its process");
            Process ownedClient = client;
            WorldSession session = new WorldSession(world, server, client, port, sharing, worldLease, authenticatedAccount, sharingEnabled,
                    () -> sessions.computeIfPresent(world.id, (id, value) -> value.client() == ownedClient ? null : value));
            sessions.put(world.id, session);
            session.monitor();
            if (share) session.share();
            return session;
        } catch (Exception e) {
            if (client != null) client.destroyForcibly();
            server.close();
            throw e;
        }
    }

    private static void validateProfiles(Profile server, Profile client) {
        if (server.getType() == ProfileType.MODS) throw new IllegalArgumentException("A world requires a plugins or mods-server profile");
        if (server.migrationPending || client.migrationPending) throw new IllegalArgumentException("Review the profile migration summary before launching");
        if (client.getType() != ProfileType.MODS || client.isTemplate()) throw new IllegalArgumentException("Select a playable client profile, not a template");
        if (!Objects.equals(server.getGameVersion(), client.getGameVersion())) throw new IllegalArgumentException("Server and client game versions must match");
        if (server.getType() == ProfileType.MODS_SERVER && !"VANILLA".equals(server.getLoader())
                && !Objects.equals(server.getLoader(), client.getLoader()))
            throw new IllegalArgumentException("Server and client mod loaders must match");
    }

    private static void configureLocalServer(Path directory, int port, boolean authenticatedAccount) throws IOException {
        Path file = directory.resolve("server.properties");
        Properties properties = new Properties();
        if (Files.isSymbolicLink(file)) throw new IOException("server.properties must be owned by this world");
        if (Files.exists(file)) try (InputStream in = Files.newInputStream(file)) { properties.load(in); }
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("server-port", Integer.toString(port));
        properties.setProperty("enable-rcon", "false");
        properties.setProperty("enable-query", "false");
        properties.setProperty("online-mode", Boolean.toString(authenticatedAccount));
        // Level paths must remain in this world's own directory.
        properties.setProperty("level-name", "world");
        try (OutputStream out = Files.newOutputStream(file)) { properties.store(out, "AutoPlug local virtual world"); }
        Path eula = directory.resolve("eula.txt");
        if (Files.isSymbolicLink(eula)) throw new IOException("eula.txt must be owned by this world");
        Files.write(eula, "eula=true\n".getBytes(StandardCharsets.UTF_8));
    }

    public ShareResult shareWorld(String worldId) {
        WorldSession session = sessions.get(worldId);
        return session == null ? new ShareResult(null, "Launch this world before sharing it.") : session.share();
    }
    /** Applies to subsequent launches; running worlds keep their chosen port. */
    public synchronized void setPreferredPort(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Preferred port must be between 1 and 65535");
        preferredPort = port;
    }
    /** Disabling UPnP also releases any mappings owned by already-running worlds. */
    public synchronized void setSharingEnabled(boolean enabled) {
        sharingEnabled = enabled;
        for (WorldSession session : sessions.values()) session.setSharingEnabled(enabled);
    }
    private static int availablePort(int preferred) throws IOException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket socket = new ServerSocket(preferred, 1, loopback)) { return socket.getLocalPort(); }
        catch (BindException busy) {
            try (ServerSocket socket = new ServerSocket(0, 1, loopback)) { return socket.getLocalPort(); }
        }
    }
    public void stopWorld(String worldId) { WorldSession session = sessions.get(worldId); if (session != null) session.close(); }
    public void serverCommand(String worldId, String command) throws IOException { running(worldId).server().command(command); }
    public void restartWorld(String worldId) throws IOException { running(worldId).server().restart(); }
    private WorldSession running(String worldId) throws IOException {
        WorldSession session = sessions.get(worldId);
        if (session == null || session.isClosed()) throw new IOException("Launch this world before using its console");
        return session;
    }
    /** A bounded tail of this world's own AutoPlug and Minecraft output. */
    public String serverLog(String worldId) throws IOException {
        worlds.get(worldId); Path file = worlds.getDirectory(worldId).resolve("autoplug-server.log");
        if (Files.isSymbolicLink(file)) throw new IOException("World console log must not be linked");
        if (!Files.exists(file)) return "No console output yet. Launch this world first.";
        try (java.io.RandomAccessFile log = new java.io.RandomAccessFile(file.toFile(), "r")) {
            long length = log.length(), start = Math.max(0, length - 65536); log.seek(start);
            byte[] bytes = new byte[(int) (length - start)]; log.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0 && text.indexOf('\n') >= 0) text = text.substring(text.indexOf('\n') + 1);
            return text;
        }
    }
    public Collection<WorldSession> activeSessions() { return Collections.unmodifiableList(new ArrayList<>(sessions.values())); }
    public boolean isProfileInUse(String profileId) {
        return sessions.values().stream().anyMatch(s -> !s.isClosed() && (profileId.equals(s.getWorld().clientProfileId) || profileId.equals(s.getWorld().serverProfileId)));
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        for (WorldSession session : new ArrayList<>(sessions.values())) session.close();
        if (Thread.currentThread() != shutdownHook) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException ignored) { /* JVM is already shutting down. */ }
        }
    }
}
