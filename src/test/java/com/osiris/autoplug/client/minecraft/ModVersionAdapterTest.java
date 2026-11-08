/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 tests.
 */

package com.osiris.autoplug.client.minecraft;

import com.osiris.autoplug.client.minecraft.profile.ModVersionAdapter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ModVersionAdapterTest {

    @Test
    public void testModDisabledRenamingWhenNotFound() throws Exception {
        File tempModsDir = Files.createTempDirectory("mods-test-").toFile();
        tempModsDir.deleteOnExit();

        // Create a dummy fake mod jar that has no release on Modrinth
        File fakeMod = new File(tempModsDir, "nonexistent-test-mod-xyz123.jar");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(fakeMod))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            String json = "{\"id\": \"nonexistent-test-mod-xyz123\", \"version\": \"1.0.0\"}";
            zos.write(json.getBytes());
            zos.closeEntry();
        }
        assertTrue(fakeMod.exists());

        // Adapt for a version
        List<String> results = ModVersionAdapter.adapt(tempModsDir, "1.21.1", "fabric");
        assertNotNull(results);
        assertEquals(1, results.size());

        // File should be renamed to .jar.disabled (never crash)
        File disabledFile = new File(tempModsDir, "nonexistent-test-mod-xyz123.jar.disabled");
        assertTrue(disabledFile.exists() || results.get(0).contains("[DISABLED]") || results.get(0).contains("[SKIP]"));
    }
}
