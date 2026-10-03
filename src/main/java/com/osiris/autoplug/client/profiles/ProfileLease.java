package com.osiris.autoplug.client.profiles;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/** An OS lock, shared by update/launch/delete operations across AutoPlug processes. */
public class ProfileLease implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    public ProfileLease(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) throw new IOException("Profile directory does not exist: " + normalized);
        // Windows cannot rename a directory containing an open lock file. A sibling
        // lock keeps delete/restore atomic without releasing protection before rename.
        Path locks = normalized.getParent().resolve(".autoplug-locks");
        Files.createDirectories(locks);
        channel = FileChannel.open(locks.resolve(normalized.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            acquired = channel.tryLock();
            if (acquired == null) throw new IOException("Profile is in use: " + directory.getFileName());
            if (!Files.isDirectory(normalized)) throw new IOException("Profile directory no longer exists: " + normalized);
        } catch (IOException | OverlappingFileLockException e) {
            channel.close();
            throw new IOException("Profile is in use: " + directory.getFileName(), e);
        }
        lock = acquired;
    }
    @Override public void close() throws IOException { try { if (lock.isValid()) lock.release(); } finally { channel.close(); } }
}
