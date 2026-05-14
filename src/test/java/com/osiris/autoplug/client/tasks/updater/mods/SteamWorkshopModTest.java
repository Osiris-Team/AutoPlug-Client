/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SteamWorkshopModTest {

    @TempDir
    Path tempDir;

    @Test
    void readsPublishedIdAndNameFromMetaCpp() throws Exception {
        File modDir = tempDir.resolve("@CF").toFile();
        modDir.mkdirs();
        File metaFile = writeMeta(modDir,
                "protocol = 1;",
                "publishedid = 1559212036;",
                "name = \"CF\";",
                "timestamp = 5249804932187309401;");

        SteamWorkshopMod mod = SteamWorkshopMod.readFromMeta(modDir, metaFile);

        assertEquals(modDir, mod.getDirectory());
        assertEquals("CF", mod.getName());
        assertEquals("1559212036", mod.getPublishedId());
    }

    @Test
    void findsModsWithMetaCppInDirectSubdirectories() throws Exception {
        File firstModDir = tempDir.resolve("@CF").toFile();
        File secondModDir = tempDir.resolve("@CommunityOnlineTools").toFile();
        File ignoredDir = tempDir.resolve("keys").toFile();
        firstModDir.mkdirs();
        secondModDir.mkdirs();
        ignoredDir.mkdirs();
        writeMeta(firstModDir, "publishedid = 1559212036;", "name = \"CF\";");
        writeMeta(secondModDir, "publishedid = 1564026768;", "name = \"Community-Online-Tools\";");

        List<SteamWorkshopMod> mods = SteamWorkshopMod.findIn(tempDir.toFile());

        assertEquals(2, mods.size());
        assertEquals("1559212036", mods.get(0).getPublishedId());
        assertEquals("1564026768", mods.get(1).getPublishedId());
    }

    private File writeMeta(File modDir, String... lines) throws Exception {
        File metaFile = new File(modDir, "meta.cpp");
        Files.write(metaFile.toPath(), Arrays.asList(lines), StandardCharsets.UTF_8);
        return metaFile;
    }
}
