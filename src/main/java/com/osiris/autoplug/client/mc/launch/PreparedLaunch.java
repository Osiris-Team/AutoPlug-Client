package com.osiris.autoplug.client.mc.launch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A prepared command contains an access token: callers must never print/log it. */
public final class PreparedLaunch implements AutoCloseable {
    private final List<String> command;
    private final Path directory;
    private final Cancellation cancellation;
    private final Path nativesDirectory;
    private final ProcessStarter starter;
    private Process process;
    private boolean attempted;
    private boolean cleaned;

    PreparedLaunch(List<String> command, Path directory, Cancellation cancellation, Path nativesDirectory) {
        this(command, directory, cancellation, nativesDirectory, ProcessBuilder::start);
    }

    PreparedLaunch(List<String> command, Path directory, Cancellation cancellation, Path nativesDirectory, ProcessStarter starter) {
        this.command = Collections.unmodifiableList(new ArrayList<>(command));
        this.directory = directory;
        this.cancellation = cancellation;
        this.nativesDirectory = nativesDirectory;
        this.starter = starter;
    }

    /** For execution/testing only, not diagnostic logs. */
    public List<String> getCommand() { return command; }
    public Path getDirectory() { return directory; }

    public synchronized Process start() throws IOException {
        cancellation.check();
        if (attempted) throw new IOException("This prepared launch has already been started");
        attempted = true;
        try { process = starter.start(new ProcessBuilder(command).directory(directory.toFile()).inheritIO()); }
        catch (IOException error) {
            try { cleanup(); } catch (IOException failure) { error.addSuppressed(failure); }
            throw error;
        }
        AutoCloseable registration = cancellation.onCancel(() -> {
            process.destroy();
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
                } catch (InterruptedException error) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
            });
        });
        process.onExit().thenRun(() -> {
            try { registration.close(); } catch (Exception ignored) { }
            try { cleanup(); } catch (IOException ignored) { }
        });
        return process;
    }

    public void cancel() { cancellation.cancel(); }
    @Override public synchronized void close() throws IOException {
        cancellation.cancel();
        if (process != null && process.isAlive()) {
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("Minecraft process did not terminate");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt(); process.destroyForcibly();
                throw new IOException("Interrupted while stopping Minecraft", error);
            }
        }
        cleanup();
    }
    private synchronized void cleanup() throws IOException {
        if (!cleaned) { new LaunchFiles().removeTree(nativesDirectory); cleaned = true; }
    }
    interface ProcessStarter { Process start(ProcessBuilder builder) throws IOException; }
    @Override public String toString() { return "PreparedLaunch[command redacted]"; }
}
