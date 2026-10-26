package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Integrity checked, atomic downloads. All metadata-controlled paths remain inside the selected cache. */
public final class LauncherDownloads {
    public interface Transport { InputStream open(URI uri) throws IOException; }
    public static final class HttpsTransport implements Transport {
        public InputStream open(URI uri) throws IOException {
            URI current = uri;
            for (int redirect = 0; redirect <= 5; redirect++) {
                checkInterrupted();
                if (!"https".equalsIgnoreCase(current.getScheme()) || current.getUserInfo() != null)
                    throw new IOException("Launcher downloads require HTTPS without URL credentials");
                HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
                connection.setConnectTimeout(15000); connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "AutoPlug-NativeLauncher/10.2.0");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location"); connection.disconnect();
                    if (location == null) throw new IOException("Download redirect has no destination");
                    current = current.resolve(location); continue;
                }
                if (code != 200) { connection.disconnect(); throw new IOException("Launcher download returned HTTP " + code); }
                return new FilterInputStream(connection.getInputStream()) {
                    @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
                };
            }
            throw new IOException("Too many download redirects");
        }
    }
    private final Path root;
    private final Transport transport;
    public LauncherDownloads(Path root, Transport transport) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.transport = transport;
        rejectLinks(this.root); Files.createDirectories(this.root);
    }
    public Path getRoot() { return root; }
    public Path resolve(String relative) throws IOException { return safeResolve(root, relative); }
    public static Path safeResolve(Path directory, String relative) throws IOException {
        Path base = directory.toAbsolutePath().normalize();
        if (relative == null || relative.isEmpty() || relative.contains("\\") || relative.contains(":"))
            throw new IOException("Invalid metadata path");
        Path candidate = base.resolve(relative).normalize();
        if (!candidate.startsWith(base) || candidate.equals(base) || Paths.get(relative).isAbsolute())
            throw new IOException("Metadata path escapes its directory");
        rejectLinks(candidate); return candidate;
    }
    static void rejectLinks(Path path) throws IOException {
        for (Path cursor = path.toAbsolutePath(); cursor != null; cursor = cursor.getParent())
            if (Files.isSymbolicLink(cursor)) throw new IOException("Launcher path contains a symbolic link");
    }
    public JsonObject json(String url) throws IOException {
        checkInterrupted();
        try (InputStream in = transport.open(URI.create(url))) {
            byte[] bytes = readBounded(in, 32L * 1024 * 1024);
            try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException e) { throw new IOException("Invalid launcher JSON metadata", e); }
        }
    }
    public String text(String url, int limit) throws IOException {
        checkInterrupted();
        try (InputStream in = transport.open(URI.create(url))) { return new String(readBounded(in, limit), StandardCharsets.UTF_8); }
    }
    public Path download(String url, String relative, String hash, String algorithm, long size) throws IOException {
        checkInterrupted();
        Path target = resolve(relative);
        if (Files.isRegularFile(target) && valid(target, hash, algorithm, size)) return target;
        Files.createDirectories(target.getParent()); rejectLinks(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".part-" + UUID.randomUUID());
        try {
            try (InputStream in = transport.open(URI.create(url)); OutputStream out = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[65536]; long count = 0; int n;
                while ((n = in.read(buffer)) != -1) {
                    checkInterrupted();
                    count += n;
                    if (count > (size >= 0 ? size : 1024L * 1024 * 1024)) throw new IOException("Download exceeds declared size or safety limit");
                    out.write(buffer, 0, n);
                }
            }
            if (!valid(temporary, hash, algorithm, size)) throw new IOException("Downloaded artifact failed integrity verification");
            checkInterrupted();
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }
    public Path artifact(JsonObject artifact, String fallbackPath) throws IOException {
        String relative = artifact.has("path") ? artifact.get("path").getAsString() : fallbackPath;
        return download(artifact.get("url").getAsString(), relative,
                artifact.has("sha1") ? artifact.get("sha1").getAsString() : null, "SHA-1",
                artifact.has("size") ? artifact.get("size").getAsLong() : -1);
    }
    public static boolean valid(Path file, String expected, String algorithm, long size) throws IOException {
        return Files.isRegularFile(file) && (size < 0 || Files.size(file) == size)
                && (expected == null || expected.equalsIgnoreCase(hash(file, algorithm)));
    }
    public static String hash(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream in = Files.newInputStream(file)) { byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) { checkInterrupted(); digest.update(buffer, 0, n); } }
            StringBuilder result = new StringBuilder(); for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException("Unavailable integrity algorithm", e); }
    }
    static byte[] readBounded(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n; long count = 0;
        while ((n = in.read(buffer)) != -1) { checkInterrupted(); count += n; if (count > limit) throw new IOException("Metadata exceeds safety limit"); out.write(buffer, 0, n); }
        return out.toByteArray();
    }
    public static void extractZip(Path archive, Path directory, java.util.List<String> excludes) throws IOException {
        checkInterrupted();
        rejectLinks(directory); Files.createDirectories(directory);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry; long total = 0; int count = 0;
            while ((entry = zip.getNextEntry()) != null) {
                checkInterrupted();
                if (++count > 200000) throw new IOException("Archive has too many entries");
                String name = entry.getName(); boolean skip = name.startsWith("META-INF/");
                for (String prefix : excludes) skip |= name.startsWith(prefix);
                if (skip) continue;
                Path target = safeResolve(directory, name);
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = zip.read(buffer)) != -1) { checkInterrupted(); total += n; if (total > 1024L * 1024 * 1024) throw new IOException("Expanded archive exceeds safety limit"); out.write(buffer, 0, n); }
                }
            }
        }
    }
    public static void checkInterrupted() throws InterruptedIOException { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Launcher preparation was cancelled"); }
}
