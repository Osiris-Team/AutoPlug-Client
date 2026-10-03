package com.osiris.autoplug.client.worlds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.stream.Collectors;

/** Owns exactly one server process and performs bounded, graceful shutdown. */
public final class ManagedServer implements AutoCloseable {
    private final Process process;
    private final long stopTimeoutMillis;
    private final AtomicBoolean closed = new AtomicBoolean();
    public ManagedServer(Process process, long stopTimeoutMillis) {
        this.process = process;
        this.stopTimeoutMillis = stopTimeoutMillis;
    }
    public Process process() { return process; }
    public boolean isAlive() { return process.isAlive(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List<ProcessHandle> descendants = process.descendants().collect(Collectors.toList());
        try {
            if (process.isAlive()) {
                process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                if (process.waitFor(stopTimeoutMillis, TimeUnit.MILLISECONDS)) return;
            }
        } catch (IOException ignored) {
            // A broken stdin must not leave an owned server running.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (process.isAlive()) {
                descendants.forEach(ProcessHandle::destroy);
                process.destroy();
                try { process.waitFor(1000, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                descendants.forEach(ProcessHandle::destroyForcibly);
                if (process.isAlive()) process.destroyForcibly();
            }
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
    }
}
