package com.osiris.autoplug.client.mc.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class ClientStorage {
    private static final long MAX_BYTES = 8 * 1024 * 1024;

    Properties read(Path path) throws IOException {
        path = directPath(path, false);
        Properties values = new Properties();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return values;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Expected a regular settings file: " + path);
        if (Files.size(path) > MAX_BYTES) throw new IOException("Settings file exceeds size limit");
        try (InputStream input = new java.io.FilterInputStream(Files.newInputStream(path)) {
            private long count;
            @Override public int read() throws IOException {
                int value = in.read(); if (value != -1 && ++count > MAX_BYTES) throw new IOException("Settings file exceeds size limit"); return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                int read = in.read(bytes, offset, (int) Math.min(length, MAX_BYTES - count + 1));
                if (read > 0 && (count += read) > MAX_BYTES) throw new IOException("Settings file exceeds size limit"); return read;
            }
        }) { values.load(input); }
        catch (IllegalArgumentException error) { throw new IOException("Invalid settings file: " + path, error); }
        return values;
    }

    void write(Path path, Properties values) throws IOException {
        path = directPath(path, true);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot replace a linked or special settings file");
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        values.store(bytes, "AutoPlug native Minecraft client");
        if (bytes.size() > MAX_BYTES) throw new IOException("Settings exceed size limit");
        Path temporary = Files.createTempFile(path.getParent(), ".settings-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) { bytes.writeTo(output); }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private Path directPath(Path path, boolean createParent) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Settings file needs a parent directory");
        Path existing = parent;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing != null && (!Files.isDirectory(existing, LinkOption.NOFOLLOW_LINKS) || !existing.equals(existing.toRealPath()))) {
            throw new IOException("Settings ancestor must be a direct canonical directory");
        }
        if (createParent) Files.createDirectories(parent);
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || !parent.equals(parent.toRealPath()))) {
            throw new IOException("Settings parent must be a direct canonical directory");
        }
        return absolute;
    }
}
