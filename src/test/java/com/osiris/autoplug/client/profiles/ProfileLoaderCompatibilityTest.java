package com.osiris.autoplug.client.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileLoaderCompatibilityTest {
    @TempDir Path temp;
    @Test void rejectsPluginCollectionOnModLoaderBeforeCreatingAnyProfile() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles"));
        assertThrows(IOException.class, () -> store.create("Wrong", "1.20.4", "FABRIC", ProfileType.PLUGINS));
        assertTrue(store.list().isEmpty());
    }
    @Test void rejectsServerModCollectionOnPluginLoaderBeforeCreatingAnyProfile() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles"));
        assertThrows(IOException.class, () -> store.create("Wrong", "1.20.4", "PAPER", ProfileType.MODS_SERVER));
        assertTrue(store.list().isEmpty());
    }
    @Test void vanillaServerUsesServerModsTypeRatherThanPretendingToLoadPlugins() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles"));
        assertThrows(IOException.class, () -> store.create("Wrong", "1.20.4", "VANILLA", ProfileType.PLUGINS));
        assertEquals(ProfileType.MODS_SERVER, store.create("Vanilla", "1.20.4", "VANILLA", ProfileType.MODS_SERVER).type);
    }
    @Test void saveAndReadRejectContradictoryMetadataWithoutOverwritingValidProfile() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles"));
        Profile profile = store.create("Server", "1.20.4", "PAPER", ProfileType.PLUGINS); profile.loader = "FABRIC";
        assertThrows(IOException.class, () -> store.save(profile)); assertEquals("PAPER", store.get(profile.id).loader);
        new JsonFiles().write(profile.getDirectory().resolve("profile.json"), profile);
        assertThrows(IOException.class, () -> store.get(profile.id));
    }
    @Test void supportedClientPluginServerModAndVanillaPairsRemainUsable() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("profiles"));
        assertEquals("FABRIC", store.create("Client", "1.20.4", "FABRIC", ProfileType.MODS).loader);
        assertEquals("PAPER", store.create("Plugins", "1.20.4", "PAPER", ProfileType.PLUGINS).loader);
        assertEquals("FABRIC", store.create("Server mods", "1.20.4", "FABRIC", ProfileType.MODS_SERVER).loader);
        assertEquals("VANILLA", store.create("Vanilla", "1.20.4", "VANILLA", ProfileType.MODS_SERVER).loader);
    }
    @Test void cliAndServiceRejectWrongTypesBeforeCreatingWorldOrInstallingAnything() throws Exception {
        try (LauncherServices services = new LauncherServices(temp, temp.resolve("minecraft"), ignored -> { })) {
            int before = services.profiles().size();
            assertThrows(IOException.class, () -> services.createProfile("Wrong", "1.20.4", "FABRIC", "PLUGINS", false));
            LauncherCommands commands = new LauncherCommands(services, ignored -> { });
            assertThrows(IOException.class, () -> commands.execute(commands.tokenize(".profiles create Wrong 1.20.4 PAPER --type mods-server")));
            assertEquals(before, services.profiles().size()); assertTrue(services.worlds().isEmpty());
            assertFalse(Files.exists(temp.resolve("autoplug")), "No legacy server initialization");
        }
    }
}
