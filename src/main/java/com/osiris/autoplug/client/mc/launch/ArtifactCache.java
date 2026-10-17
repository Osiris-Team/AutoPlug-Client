package com.osiris.autoplug.client.mc.launch;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Files become visible only after a complete download and expected SHA-1/size verification. */
public final class ArtifactCache {
    private final Path root;
    private final LaunchTransport transport;

    public ArtifactCache(Path root, LaunchTransport transport) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
        this.transport = transport;
    }

    public Path getRoot() { return root; }

    /** Maven-style loader metadata may publish a checksum sidecar instead of embedding it. */
    public Path obtainWithSidecar(String relative, URI url, Cancellation cancellation) throws IOException {
        Path installed = installedLibrary(relative);
        if (installed != null) return installed;
        Path checksum = Files.createTempFile(root, ".checksum-", ".txt");
        try {
            transport.download(URI.create(url.toString() + ".sha1"), checksum, cancellation);
            if (Files.size(checksum) > 4096) throw new IOException("Invalid library checksum sidecar");
            String text = new String(Files.readAllBytes(checksum), java.nio.charset.StandardCharsets.US_ASCII).trim();
            String expected = text.split("\\s+")[0];
            return obtain(relative, url, expected, -1, cancellation);
        } finally {
            Files.deleteIfExists(checksum);
        }
    }

    /** A verified official installer may generate libraries that have no public Maven URL. */
    public synchronized void importInstallerLibrary(String relative, Path generated) throws IOException {
        Path target = resolve(relative);
        Files.createDirectories(target.getParent());
        Files.copy(generated, target, StandardCopyOption.REPLACE_EXISTING);
        Files.write(target.resolveSibling(target.getFileName() + ".autoplug-sha1"),
                sha1(target).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public Path installedLibrary(String relative) throws IOException {
        Path target = resolve(relative);
        Path checksum = target.resolveSibling(target.getFileName() + ".autoplug-sha1");
        if (!Files.isRegularFile(target) || !Files.isRegularFile(checksum)) return null;
        if (Files.size(checksum) != 40) throw new IOException("Invalid installer library checksum");
        String hash = new String(Files.readAllBytes(checksum), java.nio.charset.StandardCharsets.US_ASCII);
        if (!valid(target, hash, -1)) throw new IOException("Installed loader library changed after installation");
        return target;
    }

    public Path resolve(String relative) throws IOException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new IOException("Invalid cache path");
        return target;
    }

    public synchronized Path obtain(String relative, URI url, String sha1, long size,
                                    Cancellation cancellation) throws IOException {
        cancellation.check();
        Path target = resolve(relative);
        if (Files.isRegularFile(target) && valid(target, sha1, size)) return target;
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".download-", ".part");
        try {
            transport.download(url, temporary, cancellation);
            cancellation.check();
            if (!valid(temporary, sha1, size)) throw new IOException("Artifact checksum or size mismatch: " + relative);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public boolean valid(Path file, String expectedSha1, long size) throws IOException {
        if (size >= 0 && Files.size(file) != size) return false;
        if (expectedSha1 == null || !expectedSha1.matches("[a-fA-F0-9]{40}")) {
            throw new IOException("An expected SHA-1 is required for official Minecraft artifacts");
        }
        return expectedSha1.equalsIgnoreCase(sha1(file));
    }

    public String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format("%02x", value & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-1 unavailable", error);
        }
    }
}
