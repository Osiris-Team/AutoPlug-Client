package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.utils.UtilsCrypto;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Shared, verified cache downloads using the application's existing HTTP and hash libraries. */
final class LauncherFiles {
    static final OkHttpClient HTTP = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).followRedirects(true).build();
    private static final Object[] DOWNLOAD_LOCKS = new Object[64];
    static { for (int i = 0; i < DOWNLOAD_LOCKS.length; i++) DOWNLOAD_LOCKS[i] = new Object(); }

    private LauncherFiles() { }
    static JsonObject json(String url) throws IOException {
        try { return JsonParser.parseString(text(url)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Invalid JSON metadata from " + url, e); }
    }
    static String text(String url) throws IOException {
        try (Response response = HTTP.newCall(new Request.Builder().url(url).header("User-Agent", "AutoPlug/10 native-launcher").build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code() + " fetching " + url);
            if (response.body().contentLength() > 32 * 1024 * 1024) throw new IOException("Metadata too large: " + url);
            return response.body().string();
        }
    }
    static Path child(Path root, String relative) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        Path result = absolute.resolve(relative).normalize();
        if (!result.startsWith(absolute) || result.equals(absolute)) throw new IOException("Invalid cache path: " + relative);
        return result;
    }
    static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    static void writeJson(Path path, JsonObject json) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".metadata-", ".tmp");
        try { Files.write(temporary, json.toString().getBytes(StandardCharsets.UTF_8)); move(temporary, path); }
        finally { Files.deleteIfExists(temporary); }
    }
    static JsonObject readJson(Path path) throws IOException {
        try { return JsonParser.parseString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Invalid cached metadata: " + path, e); }
    }
    static boolean valid(Path path, String sha1, long size) {
        try { return Files.isRegularFile(path) && (size < 0 || Files.size(path) == size)
                && (sha1 == null || sha1.equalsIgnoreCase(UtilsCrypto.fastSHA1(path.toFile()))); }
        catch (Exception e) { return false; }
    }
    static Path download(String url, Path destination, String sha1, long size, Consumer<String> progress) throws IOException {
        synchronized (DOWNLOAD_LOCKS[(destination.toAbsolutePath().normalize().hashCode() & 0x7fffffff) % DOWNLOAD_LOCKS.length]) {
            return downloadLocked(url, destination, sha1, size, progress);
        }
    }
    private static Path downloadLocked(String url, Path destination, String sha1, long size, Consumer<String> progress) throws IOException {
        if (valid(destination, sha1, size)) return destination;
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".download-", ".part");
        progress.accept("Downloading " + destination.getFileName());
        try {
            try (Response response = HTTP.newCall(new Request.Builder().url(url).header("User-Agent", "AutoPlug/10 native-launcher").build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code() + " fetching " + url);
                try (InputStream in = response.body().byteStream(); OutputStream out = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[65536];
                    int count;
                    long total = 0;
                    while ((count = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
                        total += count;
                        if (size >= 0 && total > size) throw new IOException("Unexpected download size: " + destination.getFileName());
                        out.write(buffer, 0, count);
                    }
                }
            }
            if (!valid(temporary, sha1, size)) throw new IOException("Checksum or length mismatch: " + destination.getFileName());
            move(temporary, destination);
            return destination;
        } finally { Files.deleteIfExists(temporary); }
    }
}
