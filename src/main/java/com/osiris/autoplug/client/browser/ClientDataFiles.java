package com.osiris.autoplug.client.browser;

import java.io.IOException;
import java.nio.file.*;

public final class ClientDataFiles {
    private ClientDataFiles() { }
    public static void rejectLinks(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path current = absolute; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("Client data cannot use linked paths");
        }
    }
    public static void atomicWrite(Path path, byte[] contents) throws IOException {
        rejectLinks(path); Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), ".autoplug-", ".part");
        try {
            Files.write(temp, contents);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
