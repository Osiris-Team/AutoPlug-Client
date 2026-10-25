package com.osiris.autoplug.client.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerFavoriteHistoryTest {
    @TempDir Path directory;

    @Test void oldFavoritesGainPersistentJoinHistoryWithoutLosingItOnRename() throws Exception {
        Path file = directory.resolve("servers.json");
        Files.write(file, "[{\"name\":\"Minecraft Server\",\"address\":\"EXAMPLE.com\"}]".getBytes(StandardCharsets.UTF_8));
        ServerBrowserService browser = new ServerBrowserService(file, directory.resolve("servers.dat"));
        SavedServer original = browser.list().get(0);
        assertEquals("example.com", original.name); assertEquals(0, original.lastJoinedAt);
        browser.recordJoined("EXAMPLE.COM:25565", 1000);
        browser.add("Friends", "example.com");
        SavedServer reopened = new ServerBrowserService(file, directory.resolve("servers.dat")).list().get(0);
        assertEquals("Friends", reopened.name); assertEquals(1000, reopened.lastJoinedAt);
        browser.recordJoined("example.com", 900);
        assertEquals(1000, browser.list().get(0).lastJoinedAt, "An older completion must not move activity backwards");
    }

    @Test void lateLaunchNeverRecreatesRemovedFavoriteAndBlankNamesUseAddress() throws Exception {
        Path file = directory.resolve("servers.json");
        ServerBrowserService browser = new ServerBrowserService(file, directory.resolve("servers.dat"));
        browser.add("Minecraft Server", "[::1]:25566");
        assertEquals("[::1]:25566", browser.list().get(0).name);
        browser.remove("[::1]:25566"); byte[] afterRemoval = Files.readAllBytes(file);
        browser.recordJoined("[::1]:25566", 2000);
        assertArrayEquals(afterRemoval, Files.readAllBytes(file)); assertTrue(browser.list().isEmpty());
        assertEquals("example.com", new SavedServer("  ", "example.com").name);
        assertEquals("My Minecraft Server", new SavedServer("My Minecraft Server", "example.com").name);
    }
}
