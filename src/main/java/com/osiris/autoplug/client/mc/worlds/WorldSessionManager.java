package com.osiris.autoplug.client.mc.worlds;

import com.osiris.autoplug.client.mc.sharing.PortSharingService;
import com.osiris.autoplug.client.utils.MineStat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Per-world processes and lifecycle; deliberately independent of GD and the global server. */
public final class WorldSessionManager implements AutoCloseable {
    public interface ClientLauncher {
        Process launch(String host, int port) throws IOException, InterruptedException;
    }
    public interface ProcessFactory {
        Process start(List<String> command, Path directory) throws IOException;
    }
    public interface ReadinessProbe {
        boolean ready(String host, int port) throws IOException;
    }
    public interface PortAllocator { int allocate() throws IOException; }

    public static final class Options {
        final long readinessMillis;
        final long stopMillis;
        final long pollMillis;
        public Options(long readinessMillis, long stopMillis, long pollMillis) {
            if (readinessMillis < 1 || stopMillis < 1 || pollMillis < 1) throw new IllegalArgumentException("Timeouts must be positive");
            this.readinessMillis = readinessMillis;
            this.stopMillis = stopMillis;
            this.pollMillis = pollMillis;
        }
    }

    private final WorldRepository repository;
    private final ProcessFactory processes;
    private final ReadinessProbe readiness;
    private final PortAllocator ports;
    private final PortSharingService sharing;
    private final Options options;
    private final ConcurrentHashMap<String, Session> active = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public WorldSessionManager(WorldRepository repository, PortSharingService sharing) {
        this(repository, sharing, (command, directory) -> new ProcessBuilder(command)
                        .directory(directory.toFile()).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(directory.resolve("server.log").toFile())).start(),
                (host, port) -> new MineStat(host, port, 1, MineStat.Request.JSON).isServerUp(),
                () -> {
                    try (ServerSocket socket = new ServerSocket()) {
                        socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
                        return socket.getLocalPort();
                    }
                }, new Options(120000, 10000, 250));
    }

    /** Injectable boundaries let fixtures exercise lifecycle without servers, routers or sockets. */
    public WorldSessionManager(WorldRepository repository, PortSharingService sharing, ProcessFactory processes,
                               ReadinessProbe readiness, PortAllocator ports, Options options) {
        this.repository = repository;
        this.sharing = sharing;
        this.processes = processes;
        this.readiness = readiness;
        this.ports = ports;
        this.options = options;
    }

    /** Run off the UI thread. serverCommand must identify a prepared, user-approved server runtime. */
    public Session start(World world, List<String> serverCommand, ClientLauncher launcher,
                         boolean share, BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (closed.get()) throw new IOException("World session manager is closed");
        if (serverCommand == null || serverCommand.isEmpty()) throw new IOException("A prepared server command is required");
        Session session = new Session(world, share);
        if (active.putIfAbsent(world.getId(), session) != null) throw new IOException("This world is already starting or running");
        try {
            session.checkCancelled(cancelled);
            repository.prepare(world);
            session.port = ports.allocate();
            if (session.port < 1 || session.port > 65535) throw new IOException("Invalid local port");
            configure(world, session.port, share);
            List<String> command = new ArrayList<>(serverCommand);
            if (!command.contains("nogui") && !command.contains("--nogui")) command.add("nogui");
            session.attachServer(processes.start(Collections.unmodifiableList(command), world.getDirectory()));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(options.readinessMillis);
            while (true) {
                session.checkCancelled(cancelled);
                if (!session.server.isAlive()) throw new IOException("World server exited before becoming ready; inspect its server.log");
                if (readiness.ready("127.0.0.1", session.port)) break;
                if (System.nanoTime() >= deadline) throw new IOException("World server did not become ready before the timeout");
                Thread.sleep(options.pollMillis);
            }
            session.checkCancelled(cancelled);
            if (share) session.openSharing();
            session.checkCancelled(cancelled);
            session.attachClient(launcher.launch("127.0.0.1", session.port));
            synchronized (session) {
                session.checkCancelled(cancelled);
                session.startFinished = true;
                session.monitor = new Thread(() -> {
                    try { session.client.waitFor(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    finally {
                        try { session.close(); }
                        catch (IOException ignored) { /* close retains the failure and permits a later Stop retry. */ }
                    }
                }, "AutoPlug-world-" + world.getId());
                session.monitor.setDaemon(true);
                session.monitor.start();
                session.serverMonitor = new Thread(() -> {
                    try { session.server.waitFor(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    finally {
                        try { session.close(); }
                        catch (IOException ignored) { /* close retains the failure and permits a later Stop retry. */ }
                    }
                }, "AutoPlug-world-server-" + world.getId());
                session.serverMonitor.setDaemon(true);
                session.serverMonitor.start();
            }
            return session;
        } catch (IOException | InterruptedException | RuntimeException error) {
            synchronized (session) { session.startFinished = true; }
            try { session.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public Session getActive(String worldId) { return active.get(worldId); }

    /** Also cancels an in-flight start, before the client callback has returned. */
    public void cancel(String worldId) throws IOException {
        Session session = active.get(worldId);
        if (session != null) session.close();
    }

    @Override public void close() throws IOException {
        closed.set(true);
        IOException failure = null;
        for (Session session : new ArrayList<>(active.values())) {
            try { session.close(); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }

    private void configure(World world, int port, boolean share) throws IOException {
        Path file = world.getDirectory().resolve("server.properties");
        Properties values = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? WorldRepository.load(file) : new Properties();
        values.setProperty("server-port", Integer.toString(port));
        // LAN binding is enabled only by the explicit --share option. A router cannot forward to 127.0.0.1.
        values.setProperty("server-ip", share ? "0.0.0.0" : "127.0.0.1");
        values.setProperty("enable-status", "true");
        values.setProperty("level-name", "world");
        WorldRepository.store(file, values);
    }

    public final class Session implements AutoCloseable {
        private final World world;
        private final boolean sharingEnabled;
        private final AtomicBoolean closing = new AtomicBoolean();
        private volatile Process server;
        private volatile Process client;
        private volatile Thread monitor;
        private volatile Thread serverMonitor;
        private volatile int port;
        private volatile PortSharingService.Lease mapping;
        private volatile String shareMessage = "Not shared; local loopback play only.";
        private volatile IOException failure;
        private volatile IOException mappingFailure;
        private final List<Runnable> closeListeners = new ArrayList<>();
        private final List<ProcessHandle> serverChildren = new ArrayList<>();
        private final List<ProcessHandle> clientChildren = new ArrayList<>();
        private boolean startFinished;
        private volatile boolean closeCompleted;

        private Session(World world, boolean sharingEnabled) {
            this.world = world;
            this.sharingEnabled = sharingEnabled;
        }

        public World getWorld() { return world; }
        public int getPort() { return port; }
        public boolean isClosed() { return closeCompleted; }
        public boolean isStopping() { return closing.get() && !closeCompleted; }
        public IOException getFailure() { return failure; }
        public String getShareMessage() {
            PortSharingService.Lease lease = mapping;
            return lease != null && lease.isExpired() && !closing.get()
                    ? "Router sharing lease expired. Choose Share again to request a new finite lease." : shareMessage;
        }
        public String getJoinAddress() {
            PortSharingService.Lease lease = mapping;
            return closing.get() || lease == null || lease.isExpired() ? null : lease.getJoinAddress();
        }

        /** Each registration runs once after cleanup, or immediately when cleanup already finished. */
        public void onClose(Runnable listener) {
            java.util.Objects.requireNonNull(listener, "listener");
            synchronized (this) {
                if (!closeCompleted) { closeListeners.add(listener); return; }
            }
            listener.run();
        }

        /** A loopback-only server must be restarted with explicit sharing consent. */
        public synchronized String share() throws IOException {
            if (closing.get()) throw new IOException("World session is closed");
            if (!sharingEnabled) return "Restart this world with Share enabled to allow LAN/router access. No port was opened.";
            if (mapping != null && mapping.isExpired()) cleanupMapping();
            if (mapping == null) openSharing();
            return shareMessage;
        }

        private void checkCancelled(BooleanSupplier cancelled) throws IOException, InterruptedException {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("World launch interrupted");
            if (closed.get() || closing.get() || (cancelled != null && cancelled.getAsBoolean())) throw new IOException("World launch cancelled");
        }

        private synchronized void attachServer(Process process) throws IOException {
            if (process == null) throw new IOException("Server factory returned no process");
            server = process;
            if (closing.get()) { terminate(process, true, serverChildren); throw new IOException("World launch cancelled"); }
        }

        private synchronized void attachClient(Process process) throws IOException {
            if (process == null) throw new IOException("Client launcher returned no process");
            client = process;
            if (closing.get()) { terminate(process, false, clientChildren); throw new IOException("World launch cancelled"); }
        }

        private synchronized void openSharing() {
            if (closing.get() || mapping != null) return;
            try {
                if (sharing == null) throw new IOException("UPnP is not configured");
                mapping = sharing.share(port);
                if (mapping == null) throw new IOException("UPnP returned no mapping");
                shareMessage = "Router mapping requested: " + mapping.getJoinAddress()
                        + ". The router lease lasts up to one hour. The server is also reachable on your LAN. Internet reachability is not verified.";
            } catch (IOException error) {
                shareMessage = "Sharing unavailable: " + error.getMessage()
                        + ". The server remains reachable on your LAN. Check router forwarding/CGNAT or use a trusted tunnel; no tunnel is started automatically.";
            }
        }

        @Override public synchronized void close() throws IOException {
            closing.set(true);
            IOException error = null;
            List<Runnable> listeners;
            synchronized (this) {
                if (closeCompleted) { cleanupMapping(); return; }
                boolean processCleanupFailed = false;
                try { if (client != null) terminate(client, false, clientChildren); }
                catch (IOException e) { processCleanupFailed = true; error = e; }
                try { if (server != null) terminate(server, true, serverChildren); }
                catch (IOException e) { processCleanupFailed = true; if (error == null) error = e; else error.addSuppressed(e); }
                try { cleanupMapping(); }
                catch (IOException e) { if (error == null) error = e; else error.addSuppressed(e); }
                failure = error;
                // A pending callback may still return an owned process. A failed shutdown may
                // also leave a process (or descendant) alive. Retain the session and leases
                // until a later close can verify that all owned processes actually exited.
                if (!startFinished || processCleanupFailed || mapping != null) {
                    if (error != null) throw error;
                    return;
                }
                closeCompleted = true;
                listeners = new ArrayList<>(closeListeners);
                closeListeners.clear();
            }
            active.remove(world.getId(), this);
            Thread thread = monitor;
            if (thread != null && thread != Thread.currentThread()) thread.interrupt();
            thread = serverMonitor;
            if (thread != null && thread != Thread.currentThread()) thread.interrupt();
            for (Runnable listener : listeners) {
                try { listener.run(); }
                catch (RuntimeException callbackError) {
                    IOException wrapped = new IOException("World close callback failed", callbackError);
                    if (error == null) error = wrapped; else error.addSuppressed(wrapped);
                    synchronized (this) {
                        if (failure == null) failure = wrapped;
                        else if (failure != error) failure.addSuppressed(wrapped);
                    }
                }
            }
            if (error != null) throw error;
        }

        private void cleanupMapping() throws IOException {
            if (mapping == null) return;
            try {
                mapping.close();
                mapping = null;
                if (failure == mappingFailure) failure = null;
                mappingFailure = null;
            } catch (IOException error) { mappingFailure = error; throw error; }
        }
    }

    /** Attempts graceful server stop, then destroy/force; interruption never skips cleanup. */
    private void terminate(Process process, boolean stopCommand, List<ProcessHandle> descendants) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            try { process.descendants().forEach(child -> {
                if (descendants.stream().noneMatch(known -> known.pid() == child.pid())) descendants.add(child);
            }); }
            catch (UnsupportedOperationException ignored) { /* Process test doubles need not expose OS handles. */ }
            if (process.isAlive() && stopCommand) {
                try {
                    process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
                    process.getOutputStream().flush();
                } catch (IOException ignored) { /* A closed stdin still needs destroy fallback. */ }
            } else if (process.isAlive()) process.destroy();
            try { if (process.isAlive()) process.waitFor(options.stopMillis, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { interrupted = true; }
            if (process.isAlive()) process.destroy();
            try { if (process.isAlive()) process.waitFor(options.stopMillis, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { interrupted = true; }
            if (process.isAlive()) process.destroyForcibly();
            try { if (process.isAlive()) process.waitFor(options.stopMillis, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { interrupted = true; }
            for (ProcessHandle child : descendants) if (child.isAlive()) child.destroy();
            for (ProcessHandle child : descendants) if (child.isAlive()) child.destroyForcibly();
            long childrenDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(options.stopMillis);
            while (descendants.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < childrenDeadline) {
                try { Thread.sleep(Math.min(options.stopMillis, 10)); }
                catch (InterruptedException e) { interrupted = true; }
            }
            if (process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                throw new IOException("Owned world process did not exit after forced shutdown");
            }
        } catch (RuntimeException error) {
            throw new IOException("Unable to terminate owned world process", error);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
