package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** No game or gateway: delayed collaborators deliberately ignore interruption and return late. */
class WorldStartupCancellationTest {
    @TempDir Path temp;

    @Test void stopDuringInstallerCannotStartLateServerOrClientAndReleasesLease() throws Exception {
        Fixture f = new Fixture(temp); Gate gate = new Gate(); AtomicInteger starts = new AtomicInteger();
        try (WorldService service = f.service((p, d) -> { gate.block(); return new ServerLaunch(Collections.singletonList("fixture"), d); },
                (h, p) -> true, launch -> { starts.incrementAndGet(); return new MemoryProcess(); },
                (p, h, n) -> { starts.incrementAndGet(); return new MemoryProcess(); })) {
            ExecutorService worker = Executors.newSingleThreadExecutor(); Future<WorldSession> result = worker.submit(() -> service.launch(f.world.id, false));
            try {
                gate.entered(); service.stopWorld(f.world.id); gate.release();
                assertCancelled(result); assertEquals(0, starts.get(), "Stop must invalidate an installer that returns late");
                assertTrue(service.activeSessions().isEmpty());
                try (ProfileLease ignored = new ProfileLease(f.worlds.getDirectory(f.world.id))) { }
            } finally { gate.release(); finish(worker, result); }
        }
    }

    @Test void stopDuringReadinessClosesServerBeforeLateProbeReturns() throws Exception {
        Fixture f = new Fixture(temp); Gate gate = new Gate(); MemoryProcess server = new MemoryProcess(); AtomicInteger clients = new AtomicInteger();
        try (WorldService service = f.service((p, d) -> new ServerLaunch(Collections.singletonList("fixture"), d),
                (h, p) -> { gate.block(); return true; }, launch -> server,
                (p, h, n) -> { clients.incrementAndGet(); return new MemoryProcess(); })) {
            ExecutorService worker = Executors.newSingleThreadExecutor(); Future<WorldSession> result = worker.submit(() -> service.launch(f.world.id, false));
            try {
                gate.entered(); service.stopWorld(f.world.id);
                assertFalse(server.isAlive(), "Stop owns the server even before a client/session exists");
                gate.release(); assertCancelled(result); assertEquals(0, clients.get());
            } finally { gate.release(); finish(worker, result); }
        }
    }

    @Test void stopDuringClientPreparationDestroysLateClientAndRetainsProfileOwnershipUntilCleanup() throws Exception {
        Fixture f = new Fixture(temp); Gate gate = new Gate(); MemoryProcess server = new MemoryProcess(), client = new MemoryProcess();
        try (WorldService service = f.service((p, d) -> new ServerLaunch(Collections.singletonList("fixture"), d),
                (h, p) -> true, launch -> server, (p, h, n) -> { gate.block(); return client; })) {
            ExecutorService worker = Executors.newSingleThreadExecutor(); Future<WorldSession> result = worker.submit(() -> service.launch(f.world.id, false));
            try {
                gate.entered(); service.stopWorld(f.world.id);
                assertFalse(server.isAlive());
                assertTrue(service.isProfileInUse(f.client.id), "A cancelled but unfinished preparation still owns its profile");
                gate.release(); assertCancelled(result); assertFalse(client.isAlive()); assertTrue(service.activeSessions().isEmpty());
                assertFalse(service.isProfileInUse(f.client.id));
            } finally { gate.release(); finish(worker, result); }
        }
    }

    @Test void closeCancelsBlockedInstallerWithoutWaitingForItsServiceMonitor() throws Exception {
        Fixture f = new Fixture(temp); Gate gate = new Gate(); AtomicInteger starts = new AtomicInteger();
        WorldService service = f.service((p, d) -> { gate.block(); return new ServerLaunch(Collections.singletonList("fixture"), d); },
                (h, p) -> true, launch -> { starts.incrementAndGet(); return new MemoryProcess(); }, (p, h, n) -> new MemoryProcess());
        ExecutorService workers = Executors.newFixedThreadPool(2); Future<WorldSession> result = workers.submit(() -> service.launch(f.world.id, false));
        Future<?> closing = null;
        try {
            gate.entered(); closing = workers.submit(service::close); closing.get(2, TimeUnit.SECONDS);
            gate.release(); assertCancelled(result); assertEquals(0, starts.get());
            assertThrows(IllegalStateException.class, () -> service.launch(f.world.id, false));
        } finally { gate.release(); finish(workers, result); if (closing != null) closing.get(5, TimeUnit.SECONDS); service.close(); }
    }

    @Test void processCreatedWhileStopRacesWithFactoryIsOwnedAndClosedWhenFactoryReturns() throws Exception {
        Fixture f = new Fixture(temp); Gate gate = new Gate(); MemoryProcess server = new MemoryProcess(); AtomicInteger clients = new AtomicInteger();
        try (WorldService service = f.service((p, d) -> new ServerLaunch(Collections.singletonList("fixture"), d),
                (h, p) -> true, launch -> { gate.block(); return server; }, (p, h, n) -> { clients.incrementAndGet(); return new MemoryProcess(); })) {
            ExecutorService worker = Executors.newSingleThreadExecutor(); Future<WorldSession> result = worker.submit(() -> service.launch(f.world.id, false));
            try {
                gate.entered(); service.stopWorld(f.world.id); gate.release();
                assertCancelled(result); assertFalse(server.isAlive()); assertEquals(0, clients.get());
            } finally { gate.release(); finish(worker, result); }
        }
    }

    @Test void cancelledAttemptCannotRemoveRelaunchedWorldsSession() throws Exception {
        Fixture f = new Fixture(temp); Gate gate = new Gate(); AtomicInteger calls = new AtomicInteger();
        try (WorldService service = f.service((p, d) -> { if (calls.getAndIncrement() == 0) gate.block(); return new ServerLaunch(Collections.singletonList("fixture"), d); },
                (h, p) -> true, launch -> new MemoryProcess(), (p, h, n) -> new MemoryProcess())) {
            ExecutorService worker = Executors.newSingleThreadExecutor(); Future<WorldSession> result = worker.submit(() -> service.launch(f.world.id, false));
            try {
                gate.entered(); service.stopWorld(f.world.id); service.stopWorld(f.world.id); gate.release(); assertCancelled(result);
                WorldSession replacement = service.launch(f.world.id, false);
                assertTrue(replacement.client().isAlive()); assertTrue(service.activeSessions().contains(replacement));
                service.stopWorld(f.world.id); assertFalse(replacement.client().isAlive()); assertTrue(service.activeSessions().isEmpty());
            } finally { gate.release(); finish(worker, result); }
        }
    }

    private static void assertCancelled(Future<?> result) throws Exception {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof InterruptedException, "Expected cancellation, got " + failure.getCause());
    }
    private static void finish(ExecutorService worker, Future<WorldSession> result) throws Exception {
        try { WorldSession session = result.get(5, TimeUnit.SECONDS); session.close(); } catch (ExecutionException ignored) { }
        finally { worker.shutdownNow(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    private static final class Gate {
        private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        void block() { entered.countDown(); while (release.getCount() != 0) try { release.await(); } catch (InterruptedException ignored) { } }
        void entered() throws Exception { assertTrue(entered.await(5, TimeUnit.SECONDS), "Preparation never reached latch"); }
        void release() { release.countDown(); }
    }
    private static final class Fixture {
        final ProfileStore profiles; final WorldStore worlds; final Profile client; final VirtualWorld world;
        Fixture(Path temp) throws Exception {
            profiles = new ProfileStore(temp.resolve("profiles"));
            Profile server = profiles.create("Server", "1.20.4", "PAPER", ProfileType.PLUGINS);
            client = profiles.create("Client", "1.20.4", "VANILLA", ProfileType.MODS);
            worlds = new WorldStore(temp.resolve("worlds"));
            // Synthetic fixture state only. No Minecraft server or EULA acceptance action is executed.
            world = worlds.create("Fixture", server.id, client.id, true);
        }
        WorldService service(ServerInstaller installer, WorldService.Probe probe, WorldService.ProcessFactory processes, ClientLauncher client) {
            return new WorldService(profiles, worlds, installer, client, port -> { throw new AssertionError("No gateway"); }, probe, processes, Duration.ofSeconds(5));
        }
    }
    private static final class MemoryProcess extends Process {
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private final CompletableFuture<Process> exit = new CompletableFuture<>();
        public OutputStream getOutputStream() { return new ByteArrayOutputStream() { public void flush() { destroy(); } }; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        public InputStream getErrorStream() { return getInputStream(); }
        public int waitFor() throws InterruptedException { while (alive.get()) Thread.sleep(5); return 0; }
        public boolean waitFor(long timeout, TimeUnit unit) { return !alive.get(); }
        public int exitValue() { if (alive.get()) throw new IllegalThreadStateException(); return 0; }
        public boolean isAlive() { return alive.get(); }
        public void destroy() { alive.set(false); exit.complete(this); }
        public Process destroyForcibly() { destroy(); return this; }
        public CompletableFuture<Process> onExit() { return exit; }
        public Stream<ProcessHandle> descendants() { return Stream.empty(); }
    }
}
