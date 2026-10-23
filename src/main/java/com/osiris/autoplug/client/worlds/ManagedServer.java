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
    private final String stopCommand, restartCommand;
    private final AtomicBoolean closed = new AtomicBoolean();
    public ManagedServer(Process process, long stopTimeoutMillis) {
        this(process, stopTimeoutMillis, "stop", null);
    }
    public ManagedServer(Process process, long stopTimeoutMillis, String stopCommand, String restartCommand) {
        this.process = process;
        this.stopTimeoutMillis = stopTimeoutMillis;
        this.stopCommand = stopCommand; this.restartCommand = restartCommand;
    }
    public Process process() { return process; }
    public boolean isAlive() { return process.isAlive(); }
    public synchronized void command(String command) throws IOException {
        if (closed.get() || !isAlive()) throw new IOException("This world's server is not running");
        if (command == null || command.trim().isEmpty() || command.length() > 8192 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0)
            throw new IOException("Enter one nonempty console command");
        process.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8)); process.getOutputStream().flush();
    }
    public void restart() throws IOException {
        if (restartCommand == null) throw new IOException("This process does not support in-place AutoPlug restart");
        command(restartCommand);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List<ProcessHandle> descendants = process.descendants().collect(Collectors.toList());
        try {
            if (process.isAlive()) {
                // Serialize the final line with any console command already in flight.
                // Do not hold this lock while waiting for the child to shut down.
                synchronized (this) {
                    process.getOutputStream().write((stopCommand + "\n").getBytes(StandardCharsets.UTF_8));
                    process.getOutputStream().flush();
                }
                if (process.waitFor(stopTimeoutMillis, TimeUnit.MILLISECONDS)) return;
            }
        } catch (IOException ignored) {
            // A broken stdin must not leave an owned server running.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (process.isAlive()) {
                // A wrapper restart may have created a new child while graceful shutdown was waiting.
                process.descendants().forEach(handle -> { if (!descendants.contains(handle)) descendants.add(handle); });
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
