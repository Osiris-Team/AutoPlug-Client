/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.clientapp.NativeClientService;
import com.osiris.autoplug.client.launcher.*;
import com.osiris.autoplug.client.profiles.*;
import com.osiris.autoplug.client.utils.MineStat;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Owns both child processes and optional router mapping from startup until the client exits. */
public final class VirtualWorldService implements AutoCloseable {
    public interface ServerPreparer { ProcessBuilder prepare(WorldDefinition world, int port, boolean sharing) throws Exception; }
    public interface ClientLauncher { Process launch(String profileId, ServerAddress address) throws Exception; }
    public interface Sharing { Mapping map(int localPort) throws Exception; }
    public interface Mapping extends AutoCloseable { String joinAddress(); @Override void close() throws Exception; }
    public static final class RunningWorld implements AutoCloseable {
        public final WorldDefinition world;
        public final ServerAddress address;
        public final boolean sharingEnabled;
        private final VirtualWorldService owner;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CountDownLatch startupFinished = new CountDownLatch(1);
        private final Deque<String> output = new ArrayDeque<>();
        private volatile Process server, client;
        private volatile Mapping mapping;
        private volatile String shareFailure, cleanupFailure;
        private volatile boolean launching = true, closed;
        private volatile Thread launchThread;
        private FileChannel lockChannel;
        private FileLock lock;
        private RunningWorld(VirtualWorldService owner, WorldDefinition world, int port, boolean sharing) {
            this.owner = owner; this.world = world; address = ServerAddress.parse("127.0.0.1:" + port); sharingEnabled = sharing;
        }
        public Process serverProcess() { return server; }
        public Process clientProcess() { return client; }
        public String publicAddress() { Mapping current = mapping; return current == null ? null : current.joinAddress(); }
        public String shareFailure() { return shareFailure; }
        public String cleanupFailure() { return cleanupFailure; }
        public boolean isClosed() { return closed; }
        public List<String> recentOutput() { synchronized (output) { return Collections.unmodifiableList(new ArrayList<>(output)); } }
        private void addOutput(String line) { synchronized (output) { while (output.size() >= 80) output.removeFirst(); output.addLast(line); } }
        @Override public void close() { owner.stop(world.id); }
    }
    private final WorldStore store;
    private final ServerPreparer preparer;
    private final ClientLauncher launcher;
    private final Sharing sharing;
    private final IntSupplier preferredPort;
    private final BooleanSupplier upnpAllowed;
    private final long readinessNanos, stopMillis;
    private final ConcurrentMap<String, RunningWorld> running = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread shutdownHook;
    public VirtualWorldService(WorldStore store, NativeClientService client, Sharing sharing) throws IOException {
        this(store, productionPreparer(store, client), client::launch, sharing, client.settings::worldPort, client.settings::upnpAllowed, Duration.ofMinutes(3), Duration.ofSeconds(20));
    }
    public VirtualWorldService(WorldStore store, ServerPreparer preparer, ClientLauncher launcher, Sharing sharing, Duration readinessTimeout, Duration stopTimeout) {
        this(store, preparer, launcher, sharing, () -> 0, () -> true, readinessTimeout, stopTimeout);
    }
    public VirtualWorldService(WorldStore store, ServerPreparer preparer, ClientLauncher launcher, Sharing sharing, IntSupplier preferredPort, BooleanSupplier upnpAllowed, Duration readinessTimeout, Duration stopTimeout) {
        this.store = Objects.requireNonNull(store); this.preparer = Objects.requireNonNull(preparer); this.launcher = Objects.requireNonNull(launcher); this.sharing = sharing;
        this.preferredPort = Objects.requireNonNull(preferredPort); this.upnpAllowed = Objects.requireNonNull(upnpAllowed);
        if (readinessTimeout.isZero() || readinessTimeout.isNegative() || readinessTimeout.compareTo(Duration.ofMinutes(10)) > 0 || stopTimeout.isNegative() || stopTimeout.compareTo(Duration.ofSeconds(60)) > 0) throw new IllegalArgumentException("Invalid world lifecycle timeout");
        readinessNanos = readinessTimeout.toNanos(); stopMillis = stopTimeout.toMillis();
        shutdownHook = new Thread(() -> { closed.set(true); stopAll(); }, "AutoPlug-world-shutdown"); Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
    private static WorldServerPreparer productionPreparer(WorldStore store, NativeClientService client) throws IOException {
        ManagedJavaRuntime runtimes = new ManagedJavaRuntime(store.getRoot().resolve("cache/minecraft/runtime"));
        return new WorldServerPreparer(store.getRoot().resolve("cache/world-server"), major -> {
            Path override = client.settings.javaOverride(major);
            if (override != null) { ManagedJavaRuntime.verifyMajor(override, major); return override; }
            return runtimes.resolve(major);
        }, () -> client.settings.offlineMode);
    }
    /** Blocking preparation belongs on the existing CLI thread or a UI worker. A failed share still starts the local game. */
    public RunningWorld launch(String id, boolean share) throws Exception {
        if (closed.get()) throw new IOException("Virtual world service is closed");
        WorldDefinition world = store.get(id);
        if (!world.acceptedEula) throw new IOException("Accept the Minecraft EULA explicitly for world '" + id + "' before launching: https://aka.ms/MinecraftEULA");
        if (share && !upnpAllowed.getAsBoolean()) throw new IOException("UPnP sharing is disabled in Settings; explicitly enable it before playing and sharing a world");
        Profile attached;
        try { attached = store.getProfiles().get(world.profileId); }
        catch (IOException missing) { throw new IOException("Attached server profile '" + world.profileId + "' is unavailable; restore it before playing this world", missing); }
        WorldStore.requireServer(attached);
        if (!attached.gameVersion.equals(world.gameVersion) || !attached.loader.equals(world.loader) || attached.type != world.profileType) throw new IOException("The attached profile changed; create a new world from the current profile to preserve this save's version");
        int requestedPort = preferredPort.getAsInt();
        if (requestedPort < 0 || requestedPort > 65535) throw new IOException("Configured world port must be 0 (automatic) or between 1 and 65535");
        int port;
        try (ServerSocket available = new ServerSocket()) {
            available.bind(new InetSocketAddress(share ? "0.0.0.0" : "127.0.0.1", requestedPort)); port = available.getLocalPort();
        } catch (BindException occupied) { throw new IOException("Configured world port " + requestedPort + " is unavailable. Stop the process using it or change the world port in Settings", occupied); }
        String clientProfile = clientProfile(world);
        RunningWorld active = new RunningWorld(this, world, port, share); active.launchThread = Thread.currentThread();
        if (running.putIfAbsent(id, active) != null) throw new IOException("World is already starting or running: " + id);
        boolean success = false;
        try {
            if (closed.get()) active.cancelled.set(true);
            synchronized (active) {
                active.lockChannel = FileChannel.open(store.lockPath(world.id), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                try { active.lock = active.lockChannel.tryLock(); } catch (OverlappingFileLockException alreadyRunning) { throw new IOException("World is running in another dashboard/service", alreadyRunning); }
                if (active.lock == null) throw new IOException("World is running in another AutoPlug process");
                WorldDefinition current = store.get(id);
                if (!current.acceptedEula || !Files.isDirectory(world.directoryPath, LinkOption.NOFOLLOW_LINKS)
                        || !current.profileId.equals(world.profileId) || !current.gameVersion.equals(world.gameVersion) || !current.loader.equals(world.loader)
                        || current.profileType != world.profileType || !Objects.equals(current.clientProfileId, world.clientProfileId) || !Objects.equals(current.serverExecutable, world.serverExecutable))
                    throw new IOException("World settings changed while starting; review them and launch again");
            }
            checkCancelled(active);
            ProcessBuilder command = preparer.prepare(world, port, share);
            checkCancelled(active);
            synchronized (active) {
                checkCancelled(active); command.directory(world.directoryPath.toFile()).redirectErrorStream(true);
                active.server = command.start(); drain(active.server.getInputStream(), active, "server");
            }
            awaitReady(active);
            if (share) tryShare(active);
            checkCancelled(active);
            Process client = launcher.launch(clientProfile, active.address);
            if (client == null) throw new IOException("Client launch was cancelled");
            synchronized (active) { active.client = client; drain(client.getInputStream(), null, "client"); drain(client.getErrorStream(), null, "client-error"); }
            checkCancelled(active);
            if (!active.server.isAlive()) throw new IOException("World server exited while preparing the client");
            active.launching = false; active.launchThread = null; success = true;
            Thread monitor = new Thread(() -> monitor(active), "AutoPlug-world-monitor-" + id); monitor.setDaemon(true); monitor.start();
            return active;
        } finally {
            try { if (!success) { active.launching = false; active.launchThread = null; active.cancelled.set(true); cleanup(active); } }
            finally { active.startupFinished.countDown(); }
        }
    }
    private String clientProfile(WorldDefinition world) throws IOException {
        ProfileStore profiles = store.getProfiles(); String id = world.clientProfileId;
        if (id == null) {
            if (world.profileType == Profile.Type.MODS_SERVER) throw new IOException("Set a matching client MODS profile before launching this server mod world");
            id = automaticClientProfileId(world.id);
            Profile existing = null;
            for (Profile profile : profiles.list()) if (profile.id.equals(id)) { existing = profile; break; }
            if (existing == null) profiles.create(id, Profile.Type.MODS, world.gameVersion, "VANILLA", false);
        }
        Profile client = profiles.get(id);
        if (client.type != Profile.Type.MODS || !client.gameVersion.equals(world.gameVersion) || world.profileType == Profile.Type.MODS_SERVER && !client.loader.equals(world.loader)) throw new IOException("World client profile must match its Minecraft version and mod loader");
        return id;
    }
    static String automaticClientProfileId(String worldId) {
        if (worldId.length() <= 65) return "world-client-" + worldId;
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(worldId.getBytes(StandardCharsets.UTF_8));
            StringBuilder suffix = new StringBuilder(); for (int i = 0; i < 8; i++) suffix.append(String.format(Locale.ROOT, "%02x", bytes[i] & 255));
            return "world-client-" + worldId.substring(0, 48) + "-" + suffix;
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void awaitReady(RunningWorld active) throws Exception {
        long deadline = System.nanoTime() + readinessNanos;
        while (System.nanoTime() < deadline) {
            checkCancelled(active);
            if (!active.server.isAlive()) throw new IOException("World server exited before becoming ready. " + String.join("\n", active.recentOutput()));
            MineStat ping = new MineStat("127.0.0.1", active.address.port, 1, MineStat.Request.JSON);
            if (ping.isServerUp()) {
                ServerStatus status = new ServerStatus(new SavedServer(active.world.name, active.address, "VANILLA"), true, ping.getStrippedMotd(), ping.getVersion(), ping.getProtocol(), ping.getCurrentPlayers(), ping.getMaximumPlayers(), ping.getLatency(), null);
                if (!active.world.gameVersion.equals(status.gameVersion())) throw new IOException("Local server advertises a different Minecraft version: " + ping.getVersion());
                if (!active.server.isAlive()) throw new IOException("World server exited during its readiness ping"); return;
            }
            Thread.sleep(150);
        }
        throw new IOException("World server did not become ready before timeout. " + String.join("\n", active.recentOutput()));
    }
    private static void checkCancelled(RunningWorld active) throws InterruptedException {
        if (active.cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedException("World startup was cancelled");
    }
    public RunningWorld running(String id) { return running.get(id); }
    public boolean isRunning(String id) { RunningWorld active = running.get(id); return active != null && !active.closed; }
    public String share(String id) throws Exception {
        if (!upnpAllowed.getAsBoolean()) throw new IOException("UPnP sharing is disabled in Settings");
        RunningWorld active = running.get(id);
        if (active == null || active.server == null || !active.server.isAlive() || active.cancelled.get()) throw new IOException("Launch the world before sharing it");
        if (!active.sharingEnabled) throw new IOException("Restart this world with Share enabled so its server can accept forwarded connections");
        tryShare(active);
        if (active.publicAddress() == null) throw new IOException(active.shareFailure); return active.publicAddress();
    }
    private void tryShare(RunningWorld active) throws Exception {
        checkCancelled(active);
        synchronized (active) { if (active.mapping != null) return; }
        Mapping mapping = null;
        try {
            if (sharing == null) throw new IOException("UPnP sharing is unavailable. Forward the server port manually or use a trusted tunnel");
            if (!upnpAllowed.getAsBoolean()) throw new IOException("UPnP sharing was disabled in Settings while the world was starting");
            mapping = sharing.map(active.address.port);
            if (mapping == null || mapping.joinAddress() == null) throw new IOException("Router mapping did not return a public address");
            synchronized (active) {
                if (active.cancelled.get()) { mapping.close(); mapping = null; throw new InterruptedException("World sharing was cancelled"); }
                if (active.mapping != null) { mapping.close(); mapping = null; return; }
                active.mapping = mapping; active.shareFailure = null; mapping = null;
            }
        } catch (InterruptedException cancelled) { throw cancelled; }
        catch (Exception unavailable) { active.shareFailure = "Sharing failed: " + unavailable.getMessage() + ". Configure manual forwarding for TCP port " + active.address.port + " or use a trusted tunnel; CGNAT may prevent inbound connections"; }
        finally { if (mapping != null) try { mapping.close(); } catch (Exception failed) { active.cleanupFailure = "Port mapping cleanup failed: " + failed.getMessage(); } }
    }
    private void monitor(RunningWorld active) {
        try { while (!active.cancelled.get() && active.server.isAlive() && active.client.isAlive()) Thread.sleep(150); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finally { active.cancelled.set(true); cleanup(active); }
    }
    /** Also cancels preparation. No process may be installed into a cancelled lifecycle without immediate cleanup. */
    public void stop(String id) {
        RunningWorld active = running.get(id); if (active == null) return; active.cancelled.set(true);
        Thread launch = active.launchThread; if (launch != null && launch != Thread.currentThread()) launch.interrupt();
        if (!active.launching) cleanup(active); else synchronized (active) { closeMapping(active); terminate(active.client, false); terminate(active.server, true); }
    }
    public void stopAll() {
        List<RunningWorld> snapshot = new ArrayList<>(running.values());
        // Signal all preparations first, so another launch cannot advance while an earlier server saves.
        for (RunningWorld active : snapshot) { active.cancelled.set(true); Thread launch = active.launchThread; if (launch != null && launch != Thread.currentThread()) launch.interrupt(); }
        for (RunningWorld active : snapshot) stop(active.world.id);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (RunningWorld active : snapshot) {
            Thread launch = active.launchThread;
            if (active.startupFinished.getCount() == 0 || launch == Thread.currentThread()) continue;
            long remaining = deadline - System.nanoTime(); if (remaining <= 0) break;
            try { active.startupFinished.await(Math.max(1, remaining), TimeUnit.NANOSECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
    }
    private void cleanup(RunningWorld active) {
        boolean interrupted = Thread.interrupted();
        try {
            synchronized (active) {
                if (active.closed) return;
                terminate(active.client, false); terminate(active.server, true);
                closeMapping(active);
                if (active.lock != null) try { active.lock.release(); } catch (IOException failure) { active.cleanupFailure = failure.getMessage(); }
                if (active.lockChannel != null) try { active.lockChannel.close(); } catch (IOException failure) { active.cleanupFailure = failure.getMessage(); }
                active.closed = true; running.remove(active.world.id, active);
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
    /** Called under the lifecycle lock; clearing ownership first makes repeated cancellation/cleanup idempotent. */
    private static void closeMapping(RunningWorld active) {
        Mapping mapping = active.mapping; active.mapping = null;
        if (mapping != null) try { mapping.close(); } catch (Exception failure) { active.cleanupFailure = "Port mapping cleanup failed: " + failure.getMessage(); }
    }
    private void terminate(Process process, boolean graceful) {
        if (process == null) return;
        List<ProcessHandle> descendants = new ArrayList<>(); process.descendants().forEach(descendants::add);
        try {
            if (process.isAlive() && graceful) { try { process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8)); process.getOutputStream().flush(); } catch (IOException gone) { } }
            if (process.isAlive() && (!graceful || !process.waitFor(stopMillis, TimeUnit.MILLISECONDS))) process.destroy();
            if (process.isAlive() && !process.waitFor(2000, TimeUnit.MILLISECONDS)) process.destroyForcibly();
            if (process.isAlive()) process.waitFor(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
        finally {
            for (ProcessHandle child : descendants) if (child.isAlive()) child.destroyForcibly();
            if (process.isAlive()) process.destroyForcibly();
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
        }
    }
    private static void drain(InputStream input, RunningWorld active, String label) {
        Thread drain = new Thread(() -> {
            try (InputStream stream = input) {
                byte[] bytes = new byte[8192]; ByteArrayOutputStream line = new ByteArrayOutputStream(); int count;
                while ((count = stream.read(bytes)) != -1) {
                    if (active == null) continue; // Client output may contain account details; only drain it.
                    for (int i = 0; i < count; i++) {
                        if (bytes[i] == '\n') { active.addOutput(new String(line.toByteArray(), StandardCharsets.UTF_8)); line.reset(); }
                        else if (line.size() < 2048 && bytes[i] != '\r') line.write(bytes[i]);
                    }
                }
                if (active != null && line.size() > 0) active.addOutput(new String(line.toByteArray(), StandardCharsets.UTF_8));
            } catch (IOException closed) { }
        }, "AutoPlug-world-output-" + label); drain.setDaemon(true); drain.start();
    }
    @Override public void close() {
        closed.set(true); stopAll();
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); } catch (IllegalStateException shuttingDown) { }
    }
}
