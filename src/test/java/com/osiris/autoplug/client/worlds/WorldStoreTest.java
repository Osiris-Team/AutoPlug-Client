package com.osiris.autoplug.client.worlds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldStoreTest {
    @TempDir Path temporary;
    @Test void roundTripsSeparateWorldDirectoriesAndExplicitEula() throws Exception {
        WorldStore store = new WorldStore(temporary);
        VirtualWorld first = store.create("First", "shared-server", "client");
        VirtualWorld second = store.create("Second", "shared-server", "client");
        assertNotEquals(store.getDirectory(first.id), store.getDirectory(second.id));
        assertFalse(new WorldStore(temporary).get(first.id).eulaAccepted);
        store.setEulaAccepted(first.id, true);
        assertTrue(store.get(first.id).eulaAccepted);
        assertFalse(store.get(second.id).eulaAccepted);
        assertEquals(2, store.list().size());
        Files.write(store.getDirectory(first.id).resolve("server-icon.png"), new byte[]{1});
        assertNotNull(store.get(first.id).thumbnail);
        store.delete(first.id);
        assertEquals(1, store.list().size());
        assertTrue(Files.exists(temporary.resolve(".trash")), "Deletion keeps saves recoverable");
    }
    @Test void rejectsPathsOutsideStore() throws Exception {
        WorldStore store = new WorldStore(temporary);
        assertThrows(IllegalArgumentException.class, () -> store.getDirectory("../escape"));
        assertThrows(IllegalArgumentException.class, () -> store.delete(".."));
    }
}
