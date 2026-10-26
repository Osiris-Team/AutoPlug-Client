package com.osiris.autoplug.client.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LauncherCancellationTest {
    @TempDir Path root;
    @Test void cancelledPreparationDoesNotOpenTransportOrInstallArtifacts() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        LauncherDownloads downloads = new LauncherDownloads(root, uri -> { opens.incrementAndGet(); return new ByteArrayInputStream(new byte[]{1}); });
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class, () -> downloads.download("https://fixture.invalid/client.jar", "client.jar", null, "SHA-256", 1));
            assertThrows(InterruptedIOException.class, () -> downloads.json("https://fixture.invalid/metadata"));
            assertEquals(0, opens.get()); assertFalse(Files.exists(root.resolve("client.jar"))); assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test void interruptionDuringReadRemovesPartialArtifactAndPreservesExistingTarget() throws Exception {
        Path target = root.resolve("client.jar"); Files.write(target, new byte[]{7});
        LauncherDownloads downloads = new LauncherDownloads(root, uri -> new ByteArrayInputStream(new byte[]{1, 2}) {
            public synchronized int read(byte[] buffer, int offset, int length) { int count = super.read(buffer, offset, length); Thread.currentThread().interrupt(); return count; }
        });
        try {
            assertThrows(InterruptedIOException.class, () -> downloads.download("https://fixture.invalid/client.jar", "client.jar", null, "SHA-256", 2));
            assertArrayEquals(new byte[]{7}, Files.readAllBytes(target));
            try (java.util.stream.Stream<Path> files = Files.list(root)) { assertEquals(1, files.count()); }
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
