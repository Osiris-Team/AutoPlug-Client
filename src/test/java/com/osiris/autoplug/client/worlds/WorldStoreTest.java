/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldStoreTest {
    @TempDir Path temporary;
    @Test void copiesProfileAssetsAndMutableConfigsIntoIndependentWorldsWithoutAccountFilesOrImplicitEula() throws Exception {
        ProfileStore profiles = new ProfileStore(temporary.resolve("autoplug"));
        Profile source = profiles.create("plugins", Profile.Type.PLUGINS, "1.20.4", "PAPER", false);
        Files.write(source.assetsDirectory().resolve("example.jar"), new byte[]{1, 2, 3});
        Files.createDirectories(source.assetsDirectory().resolve("Example")); Files.write(source.assetsDirectory().resolve("Example/settings.yml"), Collections.singletonList("original"));
        Files.createDirectories(source.directoryPath.resolve("config")); Files.write(source.directoryPath.resolve("config/setting.toml"), Collections.singletonList("source=true"));
        Files.write(source.directoryPath.resolve("credentials.json"), Collections.singletonList("do not copy"));
        WorldStore worlds = new WorldStore(profiles.getRoot(), profiles);
        WorldDefinition first = worlds.create("Survival world", source.id), second = worlds.create("Survival world", source.id);
        assertEquals("survival-world", first.id); assertEquals("survival-world-2", second.id); assertFalse(first.acceptedEula);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(first.directoryPath.resolve("plugins/example.jar")));
        assertFalse(Files.exists(first.directoryPath.resolve("credentials.json"))); assertFalse(Files.exists(first.directoryPath.resolve("eula.txt")));
        Files.write(first.directoryPath.resolve("plugins/Example/settings.yml"), Collections.singletonList("world only"));
        assertEquals("original", Files.readAllLines(source.assetsDirectory().resolve("Example/settings.yml")).get(0));
        assertEquals("original", Files.readAllLines(second.directoryPath.resolve("plugins/Example/settings.yml")).get(0));
        assertTrue(worlds.acceptEula(first.id, true).acceptedEula); assertFalse(new WorldStore(profiles.getRoot(), profiles).get(second.id).acceptedEula);
        assertEquals(2, worlds.list().size()); worlds.delete(first.id); assertFalse(Files.exists(first.directoryPath)); assertTrue(Files.exists(second.directoryPath));
    }
    @Test void serverModsNeedMatchingClientProfilesAndRejectClientOnlyWorldsAndEscapingIds() throws Exception {
        ProfileStore profiles = new ProfileStore(temporary.resolve("autoplug")); WorldStore worlds = new WorldStore(profiles.getRoot(), profiles);
        Profile server = profiles.create("server-mods", Profile.Type.MODS_SERVER, "1.20.4", "FABRIC", false);
        Profile client = profiles.create("client", Profile.Type.MODS, "1.20.4", "FABRIC", false);
        profiles.create("wrong-version", Profile.Type.MODS, "1.21.1", "FABRIC", false); profiles.create("wrong-loader", Profile.Type.MODS, "1.20.4", "FORGE", false);
        Files.write(server.assetsDirectory().resolve("server-mod.jar.disabled"), new byte[]{9}); WorldDefinition world = worlds.create("Mod world", server.id);
        assertTrue(Files.exists(world.directoryPath.resolve("mods/server-mod.jar.disabled"))); assertEquals(Profile.Type.MODS_SERVER, world.profileType);
        assertEquals(client.id, worlds.setClientProfile(world.id, client.id).clientProfileId);
        assertThrows(IOException.class, () -> worlds.setClientProfile(world.id, "wrong-version")); assertThrows(IOException.class, () -> worlds.setClientProfile(world.id, "wrong-loader"));
        assertThrows(IOException.class, () -> worlds.create("client world", client.id)); assertThrows(IllegalArgumentException.class, () -> worlds.create("../escape", "bad", server.id));
    }
    @Test void rejectsMalformedAndEscapingMetadata() throws Exception {
        ProfileStore profiles = new ProfileStore(temporary.resolve("autoplug")); WorldStore worlds = new WorldStore(profiles.getRoot(), profiles);
        Profile source = profiles.create("plugins", Profile.Type.PLUGINS, "1.20.4", "PAPER", false); WorldDefinition world = worlds.create("world", source.id);
        Path definition = profiles.getRoot().resolve("worlds/definitions/" + world.id + ".properties"); Files.write(definition, Collections.singletonList("id=world"));
        assertThrows(IOException.class, () -> worlds.get(world.id)); assertThrows(IllegalArgumentException.class, () -> worlds.get("../escape"));
    }
    @Test void rejectsKnownContradictoryContainersBeforeCreatingWorldsAndPreservesCustomHybridExecutables() throws Exception {
        ProfileStore profiles = new ProfileStore(temporary.resolve("autoplug")); WorldStore worlds = new WorldStore(profiles.getRoot(), profiles);
        for (String loader : Arrays.asList("FABRIC", "QUILT", "FORGE", "NEOFORGE")) {
            Profile profile = profiles.create("plugins-" + loader, Profile.Type.PLUGINS, "1.20.4", loader, false);
            IOException failure = assertThrows(IOException.class, () -> worlds.create("invalid-" + loader, profile.id)); assertTrue(failure.getMessage().contains("MODS_SERVER"));
        }
        for (String loader : Arrays.asList("PAPER", "PURPUR", "SPIGOT", "BUKKIT")) {
            Profile profile = profiles.create("mods-" + loader, Profile.Type.MODS_SERVER, "1.20.4", loader, false);
            IOException failure = assertThrows(IOException.class, () -> worlds.create("invalid-" + loader, profile.id)); assertTrue(failure.getMessage().contains("PLUGINS"));
        }
        assertTrue(worlds.list().isEmpty());
        Path supplied = WorldServerPreparerTest.fixtureJar(temporary, WorldFixtureProcesses.class.getName(), "1.20.4");
        for (Profile.Type type : Arrays.asList(Profile.Type.PLUGINS, Profile.Type.MODS_SERVER)) {
            Profile profile = profiles.create("hybrid-" + type.name(), type, "1.20.4", "MOHIST", false); WorldDefinition world = worlds.create("hybrid-" + type.name(), profile.id);
            assertEquals(supplied, worlds.setServerExecutable(world.id, supplied).serverExecutable); assertEquals(type, world.profileType);
        }
        assertEquals(2, worlds.list().size());
    }
}
