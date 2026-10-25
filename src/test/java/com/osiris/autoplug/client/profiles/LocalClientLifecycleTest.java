package com.osiris.autoplug.client.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalClientLifecycleTest {
    @TempDir Path temporary;

    @Test void closingLauncherLeavesLocalGameAliveAndRetainsLeaseUntilGameExits() throws Exception {
        Path minecraft = Files.createDirectories(temporary.resolve("minecraft"));
        LauncherServices services = new LauncherServices(temporary.resolve("autoplug"), minecraft, ignored -> { });
        FixtureProcess process = new FixtureProcess();
        ProfileLease lease = new ProfileLease(minecraft);
        services.trackLocalClient("local:Fixture", process, lease);
        try {
            services.close();
            assertFalse(process.destroyed, "Closing AutoPlug must not terminate the local integrated server");
            assertTrue(process.isAlive());
            assertThrows(IOException.class, () -> new ProfileLease(minecraft));
            process.exitNormally();
            try (ProfileLease released = new ProfileLease(minecraft)) { assertNotNull(released); }
        } finally { process.exitNormally(); services.close(); }
    }

    @Test void waitingForSessionsIncludesLocalGameUntilItsNormalExit() throws Exception {
        Path minecraft = Files.createDirectories(temporary.resolve("minecraft"));
        LauncherServices services = new LauncherServices(temporary.resolve("autoplug"), minecraft, ignored -> { });
        FixtureProcess process = new FixtureProcess();
        services.trackLocalClient("local:Fixture", process, new ProfileLease(minecraft));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        Future<?> waiting = worker.submit(() -> {
            started.countDown();
            try { services.waitForSessions(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> waiting.get(350, TimeUnit.MILLISECONDS));
            process.exitNormally();
            waiting.get(2, TimeUnit.SECONDS);
            assertFalse(process.destroyed);
        } finally { process.exitNormally(); worker.shutdownNow(); services.close(); }
    }

    /** No OS process or Minecraft world is launched by this lifecycle regression. */
    private static final class FixtureProcess extends Process {
        private final CompletableFuture<Process> exited = new CompletableFuture<>();
        private volatile boolean alive = true, destroyed;
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() throws InterruptedException {
            try { exited.get(); return 0; }
            catch (ExecutionException e) { throw new AssertionError(e); }
        }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        @Override public boolean isAlive() { return alive; }
        @Override public CompletableFuture<Process> onExit() { return exited; }
        @Override public void destroy() { destroyed = true; exitNormally(); }
        @Override public Process destroyForcibly() { destroy(); return this; }
        void exitNormally() { alive = false; exited.complete(this); }
    }
}
