package com.osiris.autoplug.client.mc.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Content-addressed, read-only jars. Mutable profile configuration never enters this cache. */
public final class SharedArtifactCache {
    public interface Downloader { void download(Path destination) throws IOException; }
    private static final long MAX_JAR_BYTES = 512L * 1024 * 1024;
    private final Path directory;

    public SharedArtifactCache(Path directory) throws IOException {
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cache must be a direct directory: " + directory);
        }
        this.directory = directory.toRealPath();
    }

    public Path getDirectory() { return directory; }

    /** The optional publisher SHA-256 is verified before publishing a new cache object. */
    public synchronized Path fetch(String expectedSha256, Downloader downloader) throws IOException {
        String expected = normalizeHash(expectedSha256);
        if (expected != null) {
            Path existing = directory.resolve(expected + ".jar");
            if (Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                verifyObject(existing, expected);
                return existing;
            }
        }
        Path temporary = Files.createTempFile(directory, ".download-", ".jar");
        try {
            downloader.download(temporary);
            verifyJar(temporary);
            String hash = sha256(temporary);
            if (expected != null && !expected.equals(hash)) throw new IOException("Downloaded jar checksum mismatch");
            Path object = directory.resolve(hash + ".jar");
            makeReadOnly(temporary);
            try {
                Files.move(temporary, object);
            } catch (FileAlreadyExistsException race) {
                verifyObject(object, hash);
            }
            verifyObject(object, hash);
            return object;
        } finally {
            // Windows cannot unlink a DOS read-only file after a publication race.
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)
                    && !Files.getFileStore(temporary).supportsFileAttributeView("posix")) temporary.toFile().setWritable(true);
            Files.deleteIfExists(temporary);
        }
    }

    public Path store(Path localJar) throws IOException {
        if (!Files.isRegularFile(localJar, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a direct jar: " + localJar);
        return fetch(sha256(localJar), destination -> Files.copy(localJar, destination,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING));
    }

    /** Creates a hardlink where supported, otherwise an independent read-only copy. */
    public void link(Path object, Path destination) throws IOException {
        if (!object.getParent().equals(directory) || !object.getFileName().toString().matches("[0-9a-f]{64}\\.jar")) {
            throw new IOException("Not a cache object: " + object);
        }
        verifyObject(object, object.getFileName().toString().substring(0, 64));
        if (!Files.isDirectory(destination.getParent(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a direct destination directory");
        }
        if (!Files.getFileStore(object).supportsFileAttributeView("posix")) {
            // DOS read-only is shared by hardlinks and prevents normal profile deletion.
            // Keep the cached object read-only and use an independent profile copy here.
            Files.copy(object, destination);
            if (!destination.toFile().setWritable(true)) throw new IOException("Cannot make independent profile copy writable");
            return;
        }
        try {
            Files.createLink(destination, object);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw unsupported;
            Files.copy(object, destination);
            makeReadOnly(destination);
        }
    }

    public static String sha256(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a direct regular file: " + file);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[16384];
                int length;
                while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
            }
            StringBuilder hash = new StringBuilder(64);
            for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            return hash.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String normalizeHash(String hash) {
        if (hash == null || hash.isEmpty()) return null;
        if (!hash.matches("(?i)[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid SHA-256");
        return hash.toLowerCase(Locale.ROOT);
    }

    private static void verifyObject(Path object, String hash) throws IOException {
        verifyJar(object);
        if (!hash.equals(sha256(object))) throw new IOException("Cached jar checksum mismatch: " + object.getFileName());
    }

    private static void verifyJar(Path jar) throws IOException {
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.size(jar) == 0 || Files.size(jar) > MAX_JAR_BYTES) {
            throw new IOException("Invalid or oversized jar: " + jar);
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            if (!zip.entries().hasMoreElements()) throw new IOException("Empty jar archive");
        }
    }

    private static void makeReadOnly(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
        } catch (UnsupportedOperationException unsupported) {
            if (!file.toFile().setReadOnly()) throw new IOException("Cannot make cache object read-only");
        }
    }
}
