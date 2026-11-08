package com.osiris.autoplug.client.profiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Content-addressed, immutable JAR cache. Replacements never write through existing links. */
public class ArtifactCache {
    private final Path root;
    public ArtifactCache(Path root) throws IOException { this.root = root.toAbsolutePath().normalize(); Files.createDirectories(this.root); }
    public Path getRoot() { return root; }
    public Path store(Path source) throws IOException {
        String hash = digest(source);
        Path target = root.resolve("jars").resolve(hash.substring(0, 2)).resolve(hash + ".jar");
        Files.createDirectories(target.getParent());
        if (!Files.exists(target)) {
            Path tmp = Files.createTempFile(target.getParent(), "artifact-", ".tmp");
            try {
                Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
                if (!hash.equals(digest(tmp))) throw new IOException("Artifact changed while being cached");
                try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (FileAlreadyExistsException e) { /* Another process cached identical bytes. */ }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(tmp); }
        } else if (!hash.equals(digest(target))) throw new IOException("Cached artifact failed integrity check: " + target);
        return target;
    }
    public void link(Path cached, Path destination) throws IOException {
        if (!cached.toAbsolutePath().normalize().startsWith(root) || Files.isSymbolicLink(cached))
            throw new IOException("Artifact is outside the cache");
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), ".link-", ".tmp");
        Files.delete(temp);
        try {
            try { Files.createLink(temp, cached); }
            catch (IOException | UnsupportedOperationException e) {
                // Symlinks need privileges on some Windows installations; copies are the portable fallback.
                try { Files.createSymbolicLink(temp, cached); }
                catch (IOException | UnsupportedOperationException denied) { Files.copy(cached, temp); }
            }
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public String digest(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            }
            StringBuilder out = new StringBuilder();
            for (byte b : md.digest()) out.append(String.format("%02x", b & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
