package com.osiris.autoplug.client.tasks.updater.mods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayZWorkshopModUpdaterTest {
    @TempDir
    Path tempDir;

    @Test
    void findsDayZModsAndParsesPublishedIdWithSemicolon() throws Exception {
        Path serverDir = tempDir.resolve("server");
        Path modDir = serverDir.resolve("@CF");
        Files.createDirectories(modDir);
        Files.write(modDir.resolve("meta.cpp"), (
                "protocol = 1;\n" +
                "publishedid = 1559212036;\n" +
                "name = \"CF\";\n" +
                "timestamp = 5249804932187309401;\n").getBytes(StandardCharsets.UTF_8));

        DayZWorkshopModUpdater updater = new DayZWorkshopModUpdater(
                serverDir.toFile(),
                tempDir.resolve("downloads").toFile());

        List<DayZWorkshopModUpdater.DayZWorkshopMod> mods = updater.findInstalledMods();

        assertEquals(1, mods.size());
        assertEquals("1559212036", mods.get(0).publishedId);
        assertEquals("CF", mods.get(0).name);
    }

    @Test
    void installsDownloadedWorkshopModAndCopiesKeys() throws Exception {
        Path serverDir = tempDir.resolve("server");
        Path modDir = serverDir.resolve("@CF");
        Files.createDirectories(modDir);
        Files.write(modDir.resolve("meta.cpp"), "publishedid = 1559212036;\nname = \"CF\";\n".getBytes(StandardCharsets.UTF_8));
        Files.write(modDir.resolve("stale.txt"), "old".getBytes(StandardCharsets.UTF_8));

        Path downloadsDir = tempDir.resolve("downloads");
        Path downloadedModDir = downloadsDir.resolve("steamapps/workshop/content/221100/1559212036");
        Files.createDirectories(downloadedModDir.resolve("keys"));
        Files.write(downloadedModDir.resolve("meta.cpp"), "publishedid = 1559212036;\nname = \"CF\";\n".getBytes(StandardCharsets.UTF_8));
        Files.write(downloadedModDir.resolve("updated.txt"), "new".getBytes(StandardCharsets.UTF_8));
        Files.write(downloadedModDir.resolve("keys/cf.bikey"), "key".getBytes(StandardCharsets.UTF_8));

        DayZWorkshopModUpdater updater = new DayZWorkshopModUpdater(serverDir.toFile(), downloadsDir.toFile());
        List<DayZWorkshopModUpdater.DayZWorkshopMod> mods = updater.findInstalledMods();

        assertEquals(1, updater.installDownloadedMods(mods));
        assertTrue(Files.exists(modDir.resolve("updated.txt")));
        assertFalse(Files.exists(modDir.resolve("stale.txt")));
        assertTrue(Files.exists(serverDir.resolve("keys/cf.bikey")));
    }
}
