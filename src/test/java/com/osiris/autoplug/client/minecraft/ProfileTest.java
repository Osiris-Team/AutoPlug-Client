/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 tests.
 */

package com.osiris.autoplug.client.minecraft;

import com.osiris.autoplug.client.minecraft.profile.LaunchProfile;
import com.osiris.autoplug.client.minecraft.profile.ProfileManager;
import com.osiris.autoplug.client.utils.GD;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProfileTest {

    private static File testWorkingDir;

    @BeforeAll
    public static void setUp() throws Exception {
        testWorkingDir = Files.createTempDirectory("ap-profile-test-").toFile();
        GD.WORKING_DIR = testWorkingDir;
        File logFile = new File(testWorkingDir, "logs/test.log");
        logFile.getParentFile().mkdirs();
        try {
            new com.osiris.jlib.logger.AL().start("AL", true, logFile, false, false);
        } catch (Exception ignored) {}
    }

    @AfterAll
    public static void tearDown() {
        if (testWorkingDir != null && testWorkingDir.exists()) {
            deleteDir(testWorkingDir);
        }
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    @Test
    @Order(1)
    public void testCreateAndLoadProfile() throws Exception {
        String name = "survival-test";
        LaunchProfile p = ProfileManager.create(name, "1.21.1");
        assertNotNull(p);
        assertEquals(name, p.name);
        assertEquals("1.21.1", p.minecraftVersion);
        assertEquals("vanilla", p.loaderType);

        // Load back
        LaunchProfile loaded = ProfileManager.load(name);
        assertNotNull(loaded);
        assertEquals(name, loaded.name);
        assertEquals("1.21.1", loaded.minecraftVersion);
        assertEquals("vanilla", loaded.loaderType);
    }

    @Test
    @Order(2)
    public void testProfileSaveModifications() throws Exception {
        String name = "modded-test";
        LaunchProfile p = ProfileManager.create(name, "1.20.4");
        p.loaderType = "fabric";
        p.maxMemoryMb = 4096;
        p.jvmArgs.add("-XX:+UseG1GC");
        p.serverAddress = "mc.hypixel.net";
        ProfileManager.save(p);

        LaunchProfile loaded = ProfileManager.load(name);
        assertNotNull(loaded);
        assertEquals("fabric", loaded.loaderType);
        assertEquals(4096, loaded.maxMemoryMb);
        assertTrue(loaded.jvmArgs.contains("-XX:+UseG1GC"));
        assertEquals("mc.hypixel.net", loaded.serverAddress);
    }

    @Test
    @Order(3)
    public void testProfileIsolation() {
        LaunchProfile p1 = new LaunchProfile("p1", "1.20.1");
        LaunchProfile p2 = new LaunchProfile("p2", "1.21.1");

        File p1Dir = p1.profileDir(testWorkingDir);
        File p2Dir = p2.profileDir(testWorkingDir);
        assertNotEquals(p1Dir.getAbsolutePath(), p2Dir.getAbsolutePath());

        File p1Mods = p1.modsDir(testWorkingDir);
        File p2Mods = p2.modsDir(testWorkingDir);
        assertNotEquals(p1Mods.getAbsolutePath(), p2Mods.getAbsolutePath());
    }

    @Test
    @Order(4)
    public void testListProfiles() {
        List<String> names = ProfileManager.listNames();
        assertTrue(names.contains("survival-test"));
        assertTrue(names.contains("modded-test"));
    }

    @Test
    @Order(5)
    public void testDeleteProfile() {
        assertTrue(ProfileManager.delete("survival-test"));
        assertNull(tryLoad("survival-test"));
        assertFalse(ProfileManager.listNames().contains("survival-test"));
    }

    private LaunchProfile tryLoad(String name) {
        try {
            return ProfileManager.load(name);
        } catch (Exception e) {
            return null;
        }
    }
}
