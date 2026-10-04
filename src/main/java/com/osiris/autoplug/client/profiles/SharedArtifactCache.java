/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import java.io.*;
import java.nio.file.*;
import java.security.*;

/** Content-addressed artifacts. Installations replace links rather than overwriting shared bytes. */
public final class SharedArtifactCache {
    private final Path root;
    public SharedArtifactCache(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        if (Files.isSymbolicLink(this.root)) throw new IOException("Cache cannot be a symbolic link");
    }
    public Path getRoot() { return root; }
    public Path store(Path source) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Artifact is not a regular file: " + source);
        String hash = hash(source, "SHA-256");
        Path destination = root.resolve(hash + ".jar");
        if (Files.exists(destination)) {
            if (!hash.equals(hash(destination, "SHA-256"))) throw new IOException("Corrupt shared-cache artifact: " + destination);
            return destination;
        }
        Path temporary = Files.createTempFile(root, "artifact-", ".part");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (!hash.equals(hash(temporary, "SHA-256"))) throw new IOException("Artifact changed during cache import");
            ProfileFiles.move(temporary, destination);
            return destination;
        } finally { Files.deleteIfExists(temporary); }
    }
    public void link(Path cached, Path destination) throws IOException {
        cached = cached.toAbsolutePath().normalize();
        if (!cached.getParent().equals(root) || !cached.getFileName().toString().matches("[a-f0-9]{64}\\.jar")) throw new IOException("Invalid cached artifact path");
        String expected = cached.getFileName().toString().substring(0, 64);
        if (!expected.equals(hash(cached, "SHA-256"))) throw new IOException("Shared cache artifact checksum mismatch");
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".link-" + java.util.UUID.randomUUID());
        try {
            try { Files.createLink(temporary, cached); }
            catch (IOException | UnsupportedOperationException exception) { Files.copy(cached, temporary); }
            ProfileFiles.move(temporary, destination);
        } finally { Files.deleteIfExists(temporary); }
    }
    public static String hash(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536]; int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder();
            for (byte part : digest.digest()) result.append(String.format("%02x", part & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
}
