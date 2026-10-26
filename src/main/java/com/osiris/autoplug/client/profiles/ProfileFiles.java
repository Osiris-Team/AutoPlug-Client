/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

final class ProfileFiles {
    private ProfileFiles() {}
    static Path contained(Path root, Path value) throws IOException {
        root = root.toAbsolutePath().normalize();
        value = value.toAbsolutePath().normalize();
        if (!value.startsWith(root) || value.equals(root)) throw new IOException("Path is outside the managed profile directory: " + value);
        for (Path at = value; at != null && at.startsWith(root); at = at.getParent()) {
            if (Files.isSymbolicLink(at)) throw new IOException("Symbolic profile paths are not supported: " + at);
            if (at.equals(root)) break;
        }
        if (Files.exists(root) && !value.getParent().toRealPath().startsWith(root.toRealPath())) throw new IOException("Profile path escapes its root");
        return value;
    }
    static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
    static void deleteTree(Path root, Path target) throws IOException {
        target = contained(root, target);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException { if (failure != null) throw failure; Files.delete(directory); return FileVisitResult.CONTINUE; }
        });
    }
}
