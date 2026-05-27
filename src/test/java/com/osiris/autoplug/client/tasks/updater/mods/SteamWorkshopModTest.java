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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SteamWorkshopModTest {

    @TempDir
    File tempDir;

    @Test
    void readsSteamWorkshopMetaCpp() throws Exception {
        File modDir = new File(tempDir, "@CF");
        modDir.mkdirs();
        Files.write(new File(modDir, "meta.cpp").toPath(), (
                "protocol = 1;\n" +
                        "publishedid = 1559212036;\n" +
                        "name = \"CF\";\n" +
                        "timestamp = 5249804932187309401;\n"
        ).getBytes(StandardCharsets.UTF_8));

        SteamWorkshopMod mod = SteamWorkshopMod.readFromMeta(modDir, new File(modDir, "meta.cpp"));

        assertEquals("CF", mod.getName());
        assertEquals("1559212036", mod.getPublishedId());
        assertEquals("5249804932187309401", mod.getVersion());
        assertEquals("SteamWorkshop", mod.getAuthor());
        assertEquals(modDir.getAbsolutePath(), mod.installationPath);
    }

    @Test
    void findsWorkshopFoldersWithMetaCppOnly() throws Exception {
        File first = new File(tempDir, "@First");
        File second = new File(tempDir, "@Second");
        File ignored = new File(tempDir, "@Ignored");
        first.mkdirs();
        second.mkdirs();
        ignored.mkdirs();
        Files.write(new File(first, "meta.cpp").toPath(), "publishedid = 1;\nname = \"First\";\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(second, "meta.cpp").toPath(), "publishedid = 2;\nname = \"Second\";\n".getBytes(StandardCharsets.UTF_8));

        List<SteamWorkshopMod> mods = SteamWorkshopMod.findIn(tempDir);

        assertEquals(2, mods.size());
        assertEquals("First", mods.get(0).getName());
        assertEquals("Second", mods.get(1).getName());
    }
}
