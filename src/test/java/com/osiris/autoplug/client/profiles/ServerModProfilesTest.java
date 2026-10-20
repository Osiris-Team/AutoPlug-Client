/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerModProfilesTest {
    @TempDir Path temporary;
    @Test void serverModsPersistCloneAndUseIndependentActiveDefaultsWithBothCommandAliases() throws Exception {
        ProfileStore store = new ProfileStore(temporary.resolve("autoplug"));
        ProfileUpdater updater = new ProfileUpdater(store.getCache(), (profile, installed) -> { throw new AssertionError("Empty profiles have no lookup"); }, (url, destination) -> { throw new AssertionError("Empty profiles have no download"); });
        ProfileCommands commands = new ProfileCommands(store, updater); List<String> output = new ArrayList<>();
        assertTrue(commands.handle(".profiles create server 1.20.4 fabric --type mods-server --template", output::add));
        Profile server = store.get("server"); assertEquals(Profile.Type.MODS_SERVER, server.type); assertEquals(server.directoryPath.resolve("mods"), server.assetsDirectory());
        assertEquals(Profile.Type.MODS_SERVER, new ProfileStore(store.getRoot()).get("server").type);
        store.create("client", Profile.Type.MODS, "1.20.4", "FABRIC", false); store.setActive("client");
        commands.handle(".profiles activate server", output::add); assertEquals("server", store.getActive(Profile.Type.MODS_SERVER).id); assertEquals("client", store.getActive(Profile.Type.MODS).id);
        output.clear(); assertTrue(commands.handle(".check mods-server", output::add)); assertFalse(output.stream().anyMatch(text -> text.contains("failed")));
        output.clear(); assertTrue(commands.handle(".update mods_server", output::add)); assertFalse(output.stream().anyMatch(text -> text.contains("failed")));
        output.clear(); assertTrue(commands.handle(".check mods --profile server", output::add)); assertFalse(output.stream().anyMatch(text -> text.contains("failed")));
        commands.handle(".profiles clone server 1.20.1 downgraded", output::add); assertEquals(Profile.Type.MODS_SERVER, store.get("downgraded").type); assertEquals("1.20.1", store.get("downgraded").gameVersion);
        output.clear(); commands.handle(".profiles list --type mods_server", output::add); assertEquals(2, output.size()); assertTrue(output.stream().allMatch(text -> text.contains("MODS_SERVER")));
        store.delete("server"); assertNull(store.getActive(Profile.Type.MODS_SERVER)); assertEquals("client", store.getActive(Profile.Type.MODS).id);
    }
}
