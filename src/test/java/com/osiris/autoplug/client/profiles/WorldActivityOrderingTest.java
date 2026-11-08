package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.ui.LauncherActions.WorldInfo;
import com.osiris.autoplug.client.worlds.WorldStore;
import com.osiris.autoplug.client.worlds.VirtualWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class WorldActivityOrderingTest {
    @TempDir Path directory;

    @Test void newlyCreatedOrRecentlyUpdatedManagedWorldAppearsFirst() throws Exception {
        Path root = directory.resolve("autoplug");
        try (LauncherServices services = new LauncherServices(root, directory.resolve("minecraft"), null)) {
            WorldInfo first = services.createWorld("First", "", "");
            WorldInfo second = services.createWorld("Second", "", "");
            WorldStore store = new WorldStore(root.resolve("worlds"));
            long future = System.currentTimeMillis() + 120_000;
            VirtualWorld fresh = store.get(second.id); fresh.createdAt = future; store.save(fresh);
            assertEquals(second.id, services.worlds().get(0).id);
            Files.setLastModifiedTime(Paths.get(first.directory), FileTime.fromMillis(future + 10_000));
            assertEquals(first.id, services.worlds().get(0).id, "A newer update takes priority over creation time");
            assertFalse(store.get(first.id).eulaAccepted); assertFalse(store.get(second.id).eulaAccepted);
        }
    }

    @Test void localSaveLastPlayedAndLevelDataModificationAreBothConsidered() throws Exception {
        Path save = Files.createDirectories(directory.resolve("save"));
        long future = System.currentTimeMillis() + 120_000;
        WorldInfo world = new WorldInfo("local:save", "Save", "", "", save.toString(), "", false, true, "1.21.1",
                false, future, 0, true, Collections.emptyList(), false, false, true);
        assertEquals(future, LauncherServices.worldActivity(world, 0));
        Path level = Files.write(save.resolve("level.dat"), new byte[]{0});
        Files.setLastModifiedTime(level, FileTime.fromMillis(future + 20_000));
        assertEquals(future + 20_000, LauncherServices.worldActivity(world, future - 10_000));
        assertArrayEquals(new byte[]{0}, Files.readAllBytes(level), "Ordering only reads metadata");
    }
}
