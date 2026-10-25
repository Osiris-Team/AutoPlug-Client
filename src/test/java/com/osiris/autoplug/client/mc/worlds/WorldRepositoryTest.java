package com.osiris.autoplug.client.mc.worlds;

import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileRepository;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorldRepositoryTest {
    @TempDir Path root;

    @Test void persistsWorldProfileLinkAndRejectsClientOnlyProfiles() throws Exception {
        ProfileRepository profiles = new ProfileRepository(root);
        profiles.create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        profiles.create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
        WorldRepository worlds = new WorldRepository(root, profiles);
        World world = worlds.create("island", "server");
        assertEquals(root.resolve("worlds/island").toRealPath(), world.getDirectory());
        assertEquals("server", new WorldRepository(root, profiles).find("island").get().getProfileId());
        assertEquals(1, worlds.list().size());
        assertThrows(IOException.class, () -> worlds.create("invalid", "client"));
        assertThrows(IOException.class, () -> worlds.create("island", "server"));
        assertThrows(IOException.class, () -> worlds.create("../outside", "server"));
    }

    @Test void neverAcceptsTheEulaOrStartsPreparationBeforeExplicitAcceptance() throws Exception {
        ProfileRepository profiles = new ProfileRepository(root);
        profiles.create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        WorldRepository worlds = new WorldRepository(root, profiles);
        World world = worlds.create("island", "server");
        byte[] before = Files.readAllBytes(world.getDirectory().resolve("eula.txt"));
        IOException failure = assertThrows(IOException.class, () -> worlds.prepare(world));
        assertTrue(failure.getMessage().contains("explicitly accept"));
        assertArrayEquals(before, Files.readAllBytes(world.getDirectory().resolve("eula.txt")));
        assertFalse(Files.exists(world.getDirectory().resolve("plugins")));
    }

    @Test void refreshesOnlyManagedAssetsAndPreservesWorldDataAndExistingConfigs() throws Exception {
        ProfileRepository profiles = new ProfileRepository(root);
        Profile profile = profiles.create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        Files.write(profile.getAssetsDirectory().resolve("plugin.jar"), new byte[]{1});
        Files.write(profile.getConfigDirectory().resolve("settings.txt"), new byte[]{2});
        WorldRepository worlds = new WorldRepository(root, profiles);
        World world = worlds.create("island", "server");
        acceptFixtureEula(world);
        worlds.prepare(world);
        Files.createDirectory(world.getDirectory().resolve("world"));
        Files.write(world.getDirectory().resolve("world/level.dat"), new byte[]{9});
        Files.write(world.getDirectory().resolve("config/settings.txt"), new byte[]{3});
        Files.delete(profile.getAssetsDirectory().resolve("plugin.jar"));
        Files.write(profile.getAssetsDirectory().resolve("updated.jar"), new byte[]{4});
        worlds.prepare(world);
        assertFalse(Files.exists(world.getDirectory().resolve("plugins/plugin.jar")));
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(world.getDirectory().resolve("plugins/updated.jar")));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(world.getDirectory().resolve("config/settings.txt")));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(profile.getConfigDirectory().resolve("settings.txt")));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(world.getDirectory().resolve("world/level.dat")));
    }

    @Test void worldsDoNotShareMutableFilesOrUseTheGlobalServerDirectory() throws Exception {
        Path global = root.resolve("server.properties"); Files.write(global, new byte[]{8});
        ProfileRepository profiles = new ProfileRepository(root);
        Profile profile = profiles.create("mod-server", ProfileType.MODS_SERVER, "1.20.4", "FABRIC", false);
        Files.write(profile.getAssetsDirectory().resolve("mod.jar"), new byte[]{1});
        WorldRepository worlds = new WorldRepository(root, profiles);
        World first = worlds.create("first", "mod-server"), second = worlds.create("second", "mod-server");
        acceptFixtureEula(first); acceptFixtureEula(second); worlds.prepare(first); worlds.prepare(second);
        Files.write(first.getDirectory().resolve("mods/mod.jar"), new byte[]{7});
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(second.getDirectory().resolve("mods/mod.jar")));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(profile.getAssetsDirectory().resolve("mod.jar")));
        assertArrayEquals(new byte[]{8}, Files.readAllBytes(global));
    }

    @Test void configConflictDoesNotPartiallyReplaceLiveAssetsOrConfig() throws Exception {
        ProfileRepository profiles = new ProfileRepository(root);
        Profile profile = profiles.create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        Files.write(profile.getAssetsDirectory().resolve("original.jar"), new byte[]{1});
        WorldRepository worlds = new WorldRepository(root, profiles);
        World world = worlds.create("island", "server"); acceptFixtureEula(world); worlds.prepare(world);
        Files.write(world.getDirectory().resolve("config/conflict"), new byte[]{7});
        Files.createDirectory(profile.getConfigDirectory().resolve("conflict"));
        Files.write(profile.getConfigDirectory().resolve("conflict/new.txt"), new byte[]{8});
        Files.delete(profile.getAssetsDirectory().resolve("original.jar"));
        Files.write(profile.getAssetsDirectory().resolve("replacement.jar"), new byte[]{2});

        assertThrows(IOException.class, () -> worlds.prepare(world));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(world.getDirectory().resolve("plugins/original.jar")));
        assertFalse(Files.exists(world.getDirectory().resolve("plugins/replacement.jar")));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(world.getDirectory().resolve("config/conflict")));
        try (java.util.stream.Stream<Path> paths = Files.list(world.getDirectory())) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".profile-")));
        }
    }

    @Test void rejectsLinkedDirectoriesAndProfileFilesWithoutFollowingThem() throws Exception {
        ProfileRepository profiles = new ProfileRepository(root);
        Profile profile = profiles.create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        WorldRepository worlds = new WorldRepository(root, profiles);
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.createSymbolicLink(root.resolve("worlds/linked"), outside);
        assertThrows(IOException.class, () -> worlds.find("linked"));
        World world = worlds.create("island", "server"); acceptFixtureEula(world);
        Files.write(outside.resolve("secret.jar"), new byte[]{8});
        Files.createSymbolicLink(profile.getAssetsDirectory().resolve("linked.jar"), outside.resolve("secret.jar"));
        assertThrows(IOException.class, () -> worlds.prepare(world));
        assertArrayEquals(new byte[]{8}, Files.readAllBytes(outside.resolve("secret.jar")));
        assertFalse(Files.exists(world.getDirectory().resolve("plugins")));
    }

    @Test void rejectsChangedWorldOwnershipMetadata() throws Exception {
        ProfileRepository profiles = new ProfileRepository(root);
        profiles.create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
        WorldRepository worlds = new WorldRepository(root, profiles);
        World world = worlds.create("island", "server"); acceptFixtureEula(world);
        Files.write(world.getDirectory().resolve("world.properties"), "id=other\nprofileId=server\nformatVersion=1\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> worlds.find("island"));
        assertThrows(IOException.class, () -> worlds.prepare(world));
    }

    static void acceptFixtureEula(World world) throws IOException {
        // Synthetic fixture only: production never performs this action.
        Files.write(world.getDirectory().resolve("eula.txt"), "eula=true\n".getBytes(StandardCharsets.US_ASCII));
    }
}
