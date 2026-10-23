package com.osiris.autoplug.client.mc.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LaunchLifecycleTest {
    @TempDir Path temporaryDirectory;

    @Test void oneThrowingCancellationListenerCannotSkipOtherCleanup() {
        Cancellation cancellation = new Cancellation(); AtomicInteger calls = new AtomicInteger();
        cancellation.onCancel(() -> { throw new IllegalStateException("Synthetic cleanup failure"); });
        cancellation.onCancel(calls::incrementAndGet); cancellation.cancel(); cancellation.cancel();
        assertEquals(1, calls.get());
    }

    @Test void listenerRegistrationRacingWithCancellationFiresAtMostOnce() throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            Cancellation cancellation = new Cancellation(); AtomicInteger calls = new AtomicInteger(); CyclicBarrier start = new CyclicBarrier(2);
            Thread register = new Thread(() -> { await(start); cancellation.onCancel(calls::incrementAndGet); });
            Thread cancel = new Thread(() -> { await(start); cancellation.cancel(); });
            register.start(); cancel.start(); register.join(); cancel.join();
            assertEquals(1, calls.get());
        }
    }

    @Test void unregisteredListenerDoesNotRunAndLateRegistrationRunsExactlyOnce() throws Exception {
        Cancellation cancellation = new Cancellation(); AtomicInteger calls = new AtomicInteger();
        AutoCloseable registration = cancellation.onCancel(calls::incrementAndGet); registration.close(); cancellation.cancel();
        assertEquals(0, calls.get());
        cancellation.onCancel(calls::incrementAndGet); cancellation.cancel(); assertEquals(1, calls.get());
    }

    @Test void closeWaitsForTerminationAndRemovesOnlyOwnedNativesWithoutStartingARealProcess() throws Exception {
        Path natives = Files.createDirectory(temporaryDirectory.resolve("owned-natives")); Files.write(natives.resolve("fixture.so"), new byte[]{1});
        Path keep = temporaryDirectory.resolve("keep.txt"); Files.write(keep, new byte[]{7});
        FakeProcess process = new FakeProcess(true);
        PreparedLaunch prepared = new PreparedLaunch(Collections.singletonList("synthetic-java"), temporaryDirectory, new Cancellation(), natives, builder -> process);
        assertSame(process, prepared.start());
        prepared.close(); prepared.close();
        assertFalse(process.isAlive()); assertTrue(process.forced.get() > 0); assertFalse(Files.exists(natives)); assertTrue(Files.exists(keep));
    }

    @Test void failedProcessStartCleansNativesAndDoesNotAllowReusingTheCommand() throws Exception {
        Path natives = Files.createDirectory(temporaryDirectory.resolve("owned-natives"));
        PreparedLaunch prepared = new PreparedLaunch(Collections.singletonList("synthetic-java"), temporaryDirectory, new Cancellation(), natives,
                builder -> { throw new IOException("Synthetic process-start failure"); });
        assertThrows(IOException.class, prepared::start); assertFalse(Files.exists(natives));
        assertThrows(IOException.class, prepared::start); prepared.close();
    }

    private void await(CyclicBarrier barrier) {
        try { barrier.await(); } catch (Exception error) { throw new AssertionError(error); }
    }
    private final class FakeProcess extends Process {
        private volatile boolean alive = true;
        private final boolean ignoreDestroy;
        private final AtomicInteger forced = new AtomicInteger();
        private final CompletableFuture<Process> exit = new CompletableFuture<>();
        FakeProcess(boolean ignoreDestroy) { this.ignoreDestroy = ignoreDestroy; }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return !alive; }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        @Override public boolean isAlive() { return alive; }
        @Override public void destroy() { if (!ignoreDestroy) finish(); }
        @Override public Process destroyForcibly() { forced.incrementAndGet(); finish(); return this; }
        private void finish() { alive = false; exit.complete(this); }
        @Override public CompletableFuture<Process> onExit() { return exit; }
    }
}
