package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftServerInstallerTest {
    @TempDir Path temporary;
    @Test void installsIntoIndependentWorldsCachesJarsAndPreservesMutableWorldConfig() throws Exception {
        ProfileStore store = new ProfileStore(temporary.resolve("profiles"));
        Profile profile = store.create("Server template", "1.20.4", "PAPER", ProfileType.PLUGINS);
        profile.template = true; store.save(profile);
        Path plugin = profile.getDirectory().resolve("plugins/example.jar");
        jar(plugin, "v1");
        Files.write(profile.getDirectory().resolve("server.properties"), "motd=Template".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(profile.getDirectory().resolve("world"));
        Files.write(profile.getDirectory().resolve("world/level.dat"), new byte[]{9});
        AtomicInteger acquisitions = new AtomicInteger();
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        MinecraftServerInstaller installer = new MinecraftServerInstaller(store.getCache(), version -> java, (p, work, runtime) -> {
            acquisitions.incrementAndGet(); Path result = work.resolve("fixture-server.jar"); jar(result, "server"); return result;
        });
        Path first = temporary.resolve("worlds/first"), second = temporary.resolve("worlds/second");
        ServerLaunch firstLaunch = installer.prepare(profile, first);
        installer.prepare(profile, second);
        assertEquals(1, acquisitions.get());
        assertEquals(first.toAbsolutePath(), firstLaunch.directory);
        assertEquals("server.jar", firstLaunch.command.get(4));
        assertTrue(Files.exists(first.resolve("plugins/example.jar")));
        assertFalse(Files.exists(first.resolve("world/level.dat")), "Reusable profiles must not copy saved worlds");
        Files.write(first.resolve("server.properties"), "motd=My world".getBytes(StandardCharsets.UTF_8));
        Files.delete(plugin);
        jar(profile.getDirectory().resolve("plugins/replacement.jar"), "v2");
        installer.prepare(profile, first);
        assertFalse(Files.exists(first.resolve("plugins/example.jar")), "Removed profile artifacts must not remain active");
        assertTrue(Files.exists(first.resolve("plugins/replacement.jar")));
        assertTrue(Files.exists(second.resolve("plugins/example.jar")), "Another world's files remain isolated");
        assertEquals("motd=My world", new String(Files.readAllBytes(first.resolve("server.properties")), StandardCharsets.UTF_8));
        assertEquals("motd=Template", new String(Files.readAllBytes(profile.getDirectory().resolve("server.properties")), StandardCharsets.UTF_8));
    }
    @Test void rejectsAnErrorPageInsteadOfCachingItAsServerJar() throws Exception {
        ProfileStore store = new ProfileStore(temporary.resolve("profiles"));
        Profile profile = store.create("Server", "1.20.4", "PAPER", ProfileType.PLUGINS);
        MinecraftServerInstaller installer = new MinecraftServerInstaller(store.getCache(), version -> temporary.resolve("java"), (p, work, runtime) -> {
            Path bad = work.resolve("bad.jar"); Files.write(bad, "not a jar".getBytes(StandardCharsets.UTF_8)); return bad;
        });
        assertThrows(java.io.IOException.class, () -> installer.prepare(profile, temporary.resolve("world")));
        assertFalse(Files.exists(temporary.resolve("world/server.jar")));
    }
    private static void jar(Path file, String contents) throws Exception {
        Files.createDirectories(file.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new JarEntry("fixture.txt")); out.write(contents.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
        }
    }
}
