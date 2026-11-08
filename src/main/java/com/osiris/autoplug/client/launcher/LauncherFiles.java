package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.utils.UtilsCrypto;
import okhttp3.OkHttpClient;
import okhttp3.Call;
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
        catch (RuntimeException e) { throw new IOException("Invalid JSON metadata from " + DownloadProgress.sourceUrl(url), e); }
    }
    static String text(String url) throws IOException {
        Call call = HTTP.newCall(new Request.Builder().url(url).header("User-Agent", "AutoPlug/10 native-launcher").build());
        try (DownloadProgress.Transfer transfer = DownloadProgress.begin("Fetching metadata", url);
             ParallelDownloads.TrackedCall tracked = ParallelDownloads.track(call);
             Response response = call.execute()) {
            transfer.redirect(response.request().url().toString());
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code() + " fetching " + DownloadProgress.sourceUrl(url));
            if (response.body().contentLength() > 32 * 1024 * 1024) throw new IOException("Metadata too large: " + DownloadProgress.sourceUrl(url));
            try (InputStream in = response.body().byteStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count; long total = 0;
                while ((count = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
                    total += count;
                    if (total > 32 * 1024 * 1024) throw new IOException("Metadata too large: " + DownloadProgress.sourceUrl(url));
                    out.write(buffer, 0, count); transfer.update(total, response.body().contentLength());
                }
                transfer.complete(total, response.body().contentLength());
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
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
        for (int attempt = 1; ; attempt++) {
            try { return downloadAttempt(url, destination, sha1, size, progress); }
            catch (IOException failure) {
                if (Thread.currentThread().isInterrupted() || failure instanceof java.io.InterruptedIOException
                        && !(failure instanceof java.net.SocketTimeoutException)
                        || failure instanceof java.nio.file.FileSystemException
                        || failure instanceof HttpFailure && !((HttpFailure) failure).retryable()
                        || attempt >= 3) throw failure;
                if (progress != null) progress.accept("Retrying " + destination.getFileName() + " (" + (attempt + 1)
                        + "/3) from " + DownloadProgress.sourceUrl(url));
                try { Thread.sleep(attempt * 200L); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new java.io.InterruptedIOException("Download cancelled.");
                }
            }
        }
    }
    private static Path downloadAttempt(String url, Path destination, String sha1, long size, Consumer<String> progress) throws IOException {
        if (valid(destination, sha1, size)) return destination;
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".download-", ".part");
        String description = "Downloading " + destination.getFileName();
        DownloadProgress.Transfer transfer = DownloadProgress.begin(description, url);
        long total = 0, expected = size;
        try (DownloadProgress.Transfer ignored = transfer) {
            if (progress != null) progress.accept(description + " from " + DownloadProgress.sourceUrl(url));
            Call call = HTTP.newCall(new Request.Builder().url(url).header("User-Agent", "AutoPlug/10 native-launcher").build());
            try (ParallelDownloads.TrackedCall tracked = ParallelDownloads.track(call); Response response = call.execute()) {
                transfer.redirect(response.request().url().toString());
                if (!response.isSuccessful() || response.body() == null) throw new HttpFailure(response.code(), url);
                if (expected < 0) expected = response.body().contentLength();
                try (InputStream in = response.body().byteStream(); OutputStream out = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
                        total += count;
                        if (size >= 0 && total > size) throw new IOException("Unexpected download size: " + destination.getFileName());
                        out.write(buffer, 0, count);
                        transfer.update(total, expected);
                    }
                }
            }
            if (!valid(temporary, sha1, size)) throw new IOException("Checksum or length mismatch: " + destination.getFileName());
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
            move(temporary, destination);
            transfer.complete(total, expected);
            return destination;
        } finally { Files.deleteIfExists(temporary); }
    }
    private static final class HttpFailure extends IOException {
        private final int status;
        HttpFailure(int status, String url) {
            super("HTTP " + status + " fetching " + DownloadProgress.sourceUrl(url));
            this.status = status;
        }
        boolean retryable() { return status == 408 || status == 429 || status >= 500; }
    }
}
