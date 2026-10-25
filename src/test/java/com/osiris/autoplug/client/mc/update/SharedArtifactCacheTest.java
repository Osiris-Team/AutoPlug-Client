package com.osiris.autoplug.client.mc.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;

import static org.junit.jupiter.api.Assertions.*;

class SharedArtifactCacheTest {
    @TempDir Path temporary;

    @Test void storesByContentAndLinksOnlyVerifiedReadOnlyJars() throws Exception {
        SharedArtifactCache cache = new SharedArtifactCache(temporary.resolve("cache"));
        Path jar = ProfileUpdateServiceTest.jar(temporary.resolve("one.jar"), "fixture");
        Path object = cache.store(jar);
        assertEquals(object, cache.store(jar));
        Path directory = Files.createDirectory(temporary.resolve("mods"));
        Path link = directory.resolve("one.jar");
        cache.link(object, link);
        assertTrue(Files.isSameFile(object, link));
        assertFalse(Files.getPosixFilePermissions(object).contains(PosixFilePermission.OWNER_WRITE));
        assertFalse(Files.isSameFile(object, jar), "Original inputs are not made into shared mutable cache objects");
    }

    @Test void checksumMismatchAndNonJarDownloadAreNeverPublished() throws Exception {
        SharedArtifactCache cache = new SharedArtifactCache(temporary.resolve("cache"));
        Path jar = ProfileUpdateServiceTest.jar(temporary.resolve("one.jar"), "fixture");
        assertThrows(IOException.class, () -> cache.fetch(new String(new char[64]).replace("\0", "0"), path -> Files.copy(jar, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)));
        assertThrows(IOException.class, () -> cache.fetch(null, path -> Files.write(path, new byte[]{1, 2, 3})));
        try (java.util.stream.Stream<Path> objects = Files.list(cache.getDirectory())) { assertEquals(0, objects.count()); }
    }

    @Test void cachedCorruptionIsDetectedBeforeAnyLinkOrReuse() throws Exception {
        SharedArtifactCache cache = new SharedArtifactCache(temporary.resolve("cache"));
        Path jar = ProfileUpdateServiceTest.jar(temporary.resolve("one.jar"), "fixture");
        Path object = cache.store(jar);
        String hash = SharedArtifactCache.sha256(object);
        object.toFile().setWritable(true);
        ProfileUpdateServiceTest.jar(object, "tampered");
        assertThrows(IOException.class, () -> cache.fetch(hash, path -> fail("Must not hide corruption by downloading")));
        Path target = Files.createDirectory(temporary.resolve("mods")).resolve("one.jar");
        assertThrows(IOException.class, () -> cache.link(object, target));
        assertFalse(Files.exists(target));
    }

    @Test void symlinkRootsAndExternalObjectsAreRejected() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path linkedRoot = temporary.resolve("linked-cache");
        Files.createSymbolicLink(linkedRoot, outside);
        assertThrows(IOException.class, () -> new SharedArtifactCache(linkedRoot));
        SharedArtifactCache cache = new SharedArtifactCache(temporary.resolve("cache"));
        Path jar = ProfileUpdateServiceTest.jar(outside.resolve("one.jar"), "fixture");
        Path target = Files.createDirectory(temporary.resolve("mods")).resolve("one.jar");
        assertThrows(IOException.class, () -> cache.link(jar, target));
        Path hashLink = cache.getDirectory().resolve(SharedArtifactCache.sha256(jar) + ".jar");
        Files.createSymbolicLink(hashLink, jar);
        assertThrows(IOException.class, () -> cache.fetch(SharedArtifactCache.sha256(jar), p -> fail("No download")));
    }
}
