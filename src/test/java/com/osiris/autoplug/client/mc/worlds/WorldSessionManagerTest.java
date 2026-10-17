package com.osiris.autoplug.client.mc.worlds;

import com.osiris.autoplug.client.mc.profiles.ProfileRepository;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import com.osiris.autoplug.client.mc.sharing.PortSharingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorldSessionManagerTest {
    @TempDir Path root;

    @Test void waitsForPingThenConnectsClientAndStopsServerAfterClientExit() throws Exception {
        Fixture f = fixture(); AtomicInteger probes = new AtomicInteger();
        try (WorldSessionManager manager = manager(f, (host, port) -> probes.incrementAndGet() >= 2, null)) {
            WorldSessionManager.Session session = manager.start(f.world, Arrays.asList("java", "-jar", "server.jar"), (host, port) -> {
                assertEquals(2, probes.get()); assertEquals("127.0.0.1", host); assertEquals(25577, port); return f.client;
            }, false, () -> false);
            assertEquals("127.0.0.1", WorldRepository.load(f.world.getDirectory().resolve("server.properties")).getProperty("server-ip"));
            assertEquals("world", WorldRepository.load(f.world.getDirectory().resolve("server.properties")).getProperty("level-name"));
            assertTrue(f.command.contains("nogui"));
            assertEquals(f.world.getDirectory(), f.startedDirectory);
            f.client.finish();
            await(() -> manager.getActive("island") == null);
            assertEquals("stop\n", f.server.commands.toString("UTF-8"));
            assertFalse(f.server.isAlive()); assertTrue(session.isClosed()); assertNull(session.getFailure());
        }
    }

    @Test void doesNotSpawnAnyProcessWhenEulaHasNotBeenAccepted() throws Exception {
        Fixture f = fixture(); java.nio.file.Files.delete(f.world.getDirectory().resolve("eula.txt"));
        try (WorldSessionManager manager = manager(f, (host, port) -> true, null)) {
            assertThrows(IOException.class, () -> manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false));
            assertNull(f.startedDirectory); assertNull(manager.getActive("island"));
        }
    }

    @Test void cancelledReadinessClosesServerAndNeverLaunchesClient() throws Exception {
        Fixture f = fixture(); AtomicBoolean cancelled = new AtomicBoolean(); AtomicInteger clients = new AtomicInteger();
        try (WorldSessionManager manager = manager(f, (host, port) -> { cancelled.set(true); return false; }, null)) {
            assertThrows(IOException.class, () -> manager.start(f.world, Arrays.asList("java"), (host, port) -> {
                clients.incrementAndGet(); return f.client;
            }, false, cancelled::get));
            assertEquals(0, clients.get()); assertFalse(f.server.isAlive()); assertNull(manager.getActive("island"));
        }
    }

    @Test void readinessTimeoutAndClientFailureBothStopTheServer() throws Exception {
        Fixture f = fixture();
        try (WorldSessionManager manager = manager(f, (host, port) -> false, null)) {
            assertThrows(IOException.class, () -> manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false));
            assertFalse(f.server.isAlive());
        }
        Fixture second = fixture();
        try (WorldSessionManager manager = manager(second, (host, port) -> true, null)) {
            assertThrows(IOException.class, () -> manager.start(second.world, Arrays.asList("java"), (host, port) -> {
                throw new IOException("fixture launch failure");
            }, false, () -> false));
            assertFalse(second.server.isAlive());
        }
    }

    @Test void killsAClientReturnedAfterConcurrentStartCancellation() throws Exception {
        Fixture f = fixture(); CountDownLatch inCallback = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (WorldSessionManager manager = manager(f, (host, port) -> true, null)) {
            Future<?> start = executor.submit(() -> {
                assertThrows(IOException.class, () -> manager.start(f.world, Arrays.asList("java"), (host, port) -> {
                    inCallback.countDown(); release.await(); return f.client;
                }, false, () -> false));
            });
            assertTrue(inCallback.await(1, TimeUnit.SECONDS));
            WorldSessionManager.Session starting = manager.getActive("island");
            AtomicInteger callbacks = new AtomicInteger(); starting.onClose(callbacks::incrementAndGet);
            manager.cancel("island"); assertFalse(f.server.isAlive());
            assertSame(starting, manager.getActive("island")); assertFalse(starting.isClosed());
            assertTrue(starting.isStopping()); assertEquals(0, callbacks.get()); release.countDown();
            start.get(1, TimeUnit.SECONDS); assertFalse(f.client.isAlive());
            assertNull(manager.getActive("island")); assertTrue(starting.isClosed()); assertEquals(1, callbacks.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void forcedShutdownIsUsedForAnUnresponsiveServerAndIsIdempotent() throws Exception {
        Fixture f = fixture(); f.server.stopCompletes = false; f.server.destroyCompletes = false;
        try (WorldSessionManager manager = manager(f, (host, port) -> true, null)) {
            WorldSessionManager.Session session = manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false);
            session.close(); session.close();
            assertFalse(f.server.isAlive()); assertEquals(1, f.server.forced.get()); assertFalse(f.client.isAlive());
        }
    }

    @Test void sharingIsOptInAndItsMappingIsDeletedOnClientExit() throws Exception {
        Fixture f = fixture(); AtomicInteger opened = new AtomicInteger(), removed = new AtomicInteger();
        PortSharingService sharing = port -> {
            opened.incrementAndGet(); assertEquals(25577, port);
            return new PortSharingService.Lease() {
                public String getJoinAddress() { return "fixture-public:25577"; }
                public void close() { removed.incrementAndGet(); }
            };
        };
        try (WorldSessionManager manager = manager(f, (host, port) -> true, sharing)) {
            WorldSessionManager.Session session = manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false);
            assertEquals(0, opened.get()); assertTrue(session.share().contains("Restart")); assertEquals(0, opened.get()); session.close();
        }
        Fixture second = fixture();
        try (WorldSessionManager manager = manager(second, (host, port) -> true, sharing)) {
            WorldSessionManager.Session session = manager.start(second.world, Arrays.asList("java"), (host, port) -> second.client, true, () -> false);
            assertEquals(1, opened.get()); assertNotNull(session.getJoinAddress());
            assertEquals("0.0.0.0", WorldRepository.load(second.world.getDirectory().resolve("server.properties")).getProperty("server-ip"));
            second.client.finish(); await(() -> removed.get() == 1); assertNull(session.getJoinAddress());
        }
    }

    @Test void failedForcedShutdownRetainsSessionAndLeasesUntilSuccessfulRetry() throws Exception {
        Fixture f = fixture(); f.server.stopCompletes = false; f.server.destroyCompletes = false;
        f.server.forceCompletes = false;
        AtomicInteger callbacks = new AtomicInteger();
        WorldSessionManager manager = manager(f, (host, port) -> true, null);
        try {
            WorldSessionManager.Session session = manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false);
            session.onClose(callbacks::incrementAndGet);
            assertThrows(IOException.class, session::close);
            assertTrue(f.server.isAlive()); assertFalse(session.isClosed()); assertTrue(session.isStopping());
            assertSame(session, manager.getActive("island")); assertNotNull(session.getFailure());
            assertEquals(0, callbacks.get());
            assertThrows(IOException.class, () -> manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false));
            f.server.forceCompletes = true;
            session.close();
            assertFalse(f.server.isAlive()); assertTrue(session.isClosed()); assertFalse(session.isStopping());
            assertNull(manager.getActive("island")); assertEquals(1, callbacks.get());
            session.close(); assertEquals(1, callbacks.get());
        } finally { f.server.finish(); manager.close(); }
    }

    @Test void mappingFailureIsVisibleAndLocalClientStillRuns() throws Exception {
        Fixture f = fixture();
        try (WorldSessionManager manager = manager(f, (host, port) -> true, port -> { throw new IOException("CGNAT fixture"); })) {
            WorldSessionManager.Session session = manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, true, () -> false);
            assertTrue(session.getShareMessage().contains("CGNAT")); assertTrue(session.getShareMessage().contains("LAN"));
            assertNull(session.getJoinAddress()); assertTrue(f.client.isAlive());
        }
    }

    @Test void sameWorldCannotStartTwiceAndClosedManagerCannotStart() throws Exception {
        Fixture f = fixture(); WorldSessionManager manager = manager(f, (host, port) -> true, null);
        manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false);
        assertThrows(IOException.class, () -> manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false));
        manager.close(); assertFalse(f.server.isAlive()); assertFalse(f.client.isAlive());
        assertThrows(IOException.class, () -> manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false));
    }

    @Test void serverCrashStopsTheClientAndRemovesTheMapping() throws Exception {
        Fixture f = fixture(); AtomicInteger removed = new AtomicInteger();
        PortSharingService sharing = port -> new PortSharingService.Lease() {
            public String getJoinAddress() { return "fixture:25577"; }
            public void close() { removed.incrementAndGet(); }
        };
        try (WorldSessionManager manager = manager(f, (host, port) -> true, sharing)) {
            manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, true, () -> false);
            f.server.finish(); await(() -> manager.getActive("island") == null);
            assertFalse(f.client.isAlive()); assertEquals(1, removed.get());
        }
    }

    @Test void mappingCleanupFailureIsVisibleAndDoesNotSkipProcessCleanup() throws Exception {
        Fixture f = fixture(); AtomicBoolean failCleanup = new AtomicBoolean(true); AtomicInteger callbacks = new AtomicInteger();
        PortSharingService sharing = port -> new PortSharingService.Lease() {
            public String getJoinAddress() { return "fixture:25577"; }
            public void close() throws IOException { if (failCleanup.get()) throw new IOException("fixture cleanup failed"); }
        };
        try (WorldSessionManager manager = manager(f, (host, port) -> true, sharing)) {
            WorldSessionManager.Session session = manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, true, () -> false);
            session.onClose(callbacks::incrementAndGet);
            assertThrows(IOException.class, session::close);
            assertNotNull(session.getFailure()); assertFalse(f.server.isAlive()); assertFalse(f.client.isAlive());
            assertSame(session, manager.getActive("island")); assertFalse(session.isClosed()); assertTrue(session.isStopping());
            assertEquals(0, callbacks.get());
            failCleanup.set(false); manager.cancel("island");
            assertNull(manager.getActive("island")); assertTrue(session.isClosed()); assertNull(session.getFailure());
            assertEquals(1, callbacks.get());
        } finally {
            failCleanup.set(false);
        }
    }

    @Test void onCloseRunsOnceAndLateRegistrationDoesNotLoseTheCleanupCallback() throws Exception {
        Fixture f = fixture(); AtomicInteger callbacks = new AtomicInteger();
        try (WorldSessionManager manager = manager(f, (host, port) -> true, null)) {
            WorldSessionManager.Session session = manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false);
            session.onClose(callbacks::incrementAndGet);
            session.close(); session.close(); assertEquals(1, callbacks.get());
            session.onClose(callbacks::incrementAndGet); assertEquals(2, callbacks.get());
        }
    }

    @Test void aFailingCloseCallbackDoesNotPreventOtherLeasesFromBeingReleased() throws Exception {
        Fixture f = fixture(); AtomicInteger callbacks = new AtomicInteger();
        try (WorldSessionManager manager = manager(f, (host, port) -> true, null)) {
            WorldSessionManager.Session session = manager.start(f.world, Arrays.asList("java"), (host, port) -> f.client, false, () -> false);
            session.onClose(() -> { throw new IllegalStateException("fixture callback"); });
            session.onClose(callbacks::incrementAndGet);
            assertThrows(IOException.class, session::close); assertEquals(1, callbacks.get());
            assertFalse(f.server.isAlive()); assertFalse(f.client.isAlive());
        }
    }

    private Fixture fixture() throws Exception {
        Path data = java.nio.file.Files.createTempDirectory(root, "fixture-");
        ProfileRepository profiles = new ProfileRepository(data);
        profiles.create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        Fixture f = new Fixture(); f.repository = new WorldRepository(data, profiles); f.world = f.repository.create("island", "server");
        WorldRepositoryTest.acceptFixtureEula(f.world); return f;
    }

    private WorldSessionManager manager(Fixture f, WorldSessionManager.ReadinessProbe readiness, PortSharingService sharing) {
        return new WorldSessionManager(f.repository, sharing, (command, directory) -> {
            f.command = command; f.startedDirectory = directory; return f.server;
        }, readiness, () -> 25577, new WorldSessionManager.Options(15, 2, 1));
    }

    private static void await(java.util.function.BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(done.getAsBoolean());
    }

    private static final class Fixture {
        WorldRepository repository; World world; Path startedDirectory; List<String> command;
        FakeProcess server = new FakeProcess(), client = new FakeProcess();
    }

    private static final class FakeProcess extends Process {
        final CountDownLatch exited = new CountDownLatch(1);
        final AtomicInteger forced = new AtomicInteger();
        volatile boolean stopCompletes = true, destroyCompletes = true, forceCompletes = true;
        final ByteArrayOutputStream commands = new ByteArrayOutputStream();
        public OutputStream getOutputStream() { return new OutputStream() {
            public void write(int value) { commands.write(value); }
            public void flush() { if (stopCompletes && commands.toString().contains("stop\n")) finish(); }
        }; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        public int waitFor() throws InterruptedException { exited.await(); return 0; }
        public boolean waitFor(long timeout, TimeUnit unit) { return !isAlive(); }
        public int exitValue() { if (isAlive()) throw new IllegalThreadStateException(); return 0; }
        public void destroy() { if (destroyCompletes) finish(); }
        public Process destroyForcibly() { forced.incrementAndGet(); if (forceCompletes) finish(); return this; }
        public boolean isAlive() { return exited.getCount() != 0; }
        void finish() { exited.countDown(); }
    }
}
