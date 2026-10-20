package com.osiris.autoplug.client;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ServerProcessObservationTest {
    @Test void staleExitCannotInspectOrStopTheReplacementProcess() throws Exception {
        Field field = Server.class.getDeclaredField("process"); field.setAccessible(true);
        Object original = field.get(null);
        StubProcess stopped = new StubProcess(false, 7), replacement = new StubProcess(true, 0);
        try {
            field.set(null, stopped);
            assertEquals(Integer.valueOf(7), Server.observedExitCode(stopped));
            // The checker captured stopped, then .restart replaced the global process before exit handling.
            field.set(null, replacement);
            assertNull(Server.observedExitCode(stopped));
            assertNull(Server.observedExitCode(replacement));
            assertFalse(Server.requestAutomaticStop(stopped));
            assertFalse(Server.requestAutomaticStop(replacement));
            assertEquals(0, replacement.exitReads, "A live replacement has no exit value to inspect");
            assertFalse(replacement.destroyed);
        } finally { field.set(null, original); }
    }

    @Test void automaticExitReservationBlocksNewStartAfterLifecycleLockIsReleased() throws Exception {
        Field field = Server.class.getDeclaredField("process"); field.setAccessible(true);
        Field stopping = Server.class.getDeclaredField("autoStopRequested"); stopping.setAccessible(true);
        Object original = field.get(null); boolean originalStopping = stopping.getBoolean(null);
        StubProcess stopped = new StubProcess(false, 0);
        try {
            field.set(null, stopped); stopping.setBoolean(null, false);
            assertTrue(Server.requestAutomaticStop(stopped));
            assertFalse(Thread.holdsLock(Server.class));
            Server.start(); Server.restart();
            assertSame(stopped, field.get(null), "Releasing the monitor before System.exit must not allow a replacement server");
            assertFalse(Server.requestAutomaticStop(stopped), "The exit reservation is idempotent");
            assertFalse(stopped.destroyed);
        } finally { field.set(null, original); stopping.setBoolean(null, originalStopping); }
    }

    @Test void intentionalRestartSuppressesStoppedGenerationUntilNewProcessStarts() throws Exception {
        Field field = Server.class.getDeclaredField("process"); field.setAccessible(true);
        Field restartingField = Server.class.getDeclaredField("isRestarting"); restartingField.setAccessible(true);
        AtomicBoolean restarting = (AtomicBoolean) restartingField.get(null);
        Object original = field.get(null); boolean originallyRestarting = restarting.get();
        StubProcess stopped = new StubProcess(false, 0);
        try {
            field.set(null, stopped); restarting.set(true);
            assertNull(Server.observedExitCode(stopped)); assertEquals(0, stopped.exitReads);
            restarting.set(false);
            assertEquals(Integer.valueOf(0), Server.observedExitCode(stopped));
        } finally { field.set(null, original); restarting.set(originallyRestarting); }
    }

    private static class StubProcess extends Process {
        final boolean alive; final int code; int exitReads; boolean destroyed;
        StubProcess(boolean alive, int code) { this.alive = alive; this.code = code; }
        @Override public boolean isAlive() { return alive; }
        @Override public int exitValue() { exitReads++; if (alive) throw new IllegalThreadStateException("Still running"); return code; }
        @Override public int waitFor() { return code; }
        @Override public void destroy() { destroyed = true; }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
    }
}
