/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.UtilsTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.osiris.jprocesses2.util.OS.isMac;
import static com.osiris.jprocesses2.util.OS.isWindows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamCMDTest {

    private SteamCMD steamCmd;
    private final String steamcmdArchive = "steamcmd" + (isWindows ? ".zip" : isMac ? "_osx.tar.gz" : "_linux.tar.gz");
    private final String steamcmdExtension = isWindows ? ".exe" : ".sh";
    private final String steamcmdExecutable = "steamcmd" + steamcmdExtension;
    // removed "{APP} validate" because it takes ages, doesn't work?
    private final String steamcmdCommand = "+login {LOGIN} +force_install_dir \"{DESTINATION}\" +app_update {APP} +quit";
    private final String steamcmdUrl = "https://steamcdn-a.akamaihd.net/client/installer/" + steamcmdArchive;
    public Map<String, String> errorResolutions = new HashMap<String, String>() {{
        put("Invalid platform", "This server does not support this OS; nothing we can do about it.");
    }};
    public File destDir = new File(GD.WORKING_DIR + "/autoplug/system/steamcmd");
    public File destExe = new File(destDir + "/" + steamcmdExecutable);
    public File dirSteamServersDownloads = new File(GD.DOWNLOADS_DIR + "/steam-servers");
    public File destArchive = new File(destDir + "/" + steamcmdArchive);
    @Test
    void installSteamcmd() throws IOException {
        UtilsTest.init();
        new SteamCMD().installIfNeeded();
    }

    @BeforeEach
    void setup() {
        steamCmd = new SteamCMD();
        steamCmd.destExe = new File(destDir + "/" + steamcmdExecutable);
        steamCmd.destArchive = new File(destDir + "/" + steamcmdArchive);
    }

    @Test
    void testIsInstalled() {
        assertTrue(steamCmd.isInstalled());
    }

    @Test
    void testInstallIfNeededNotInstalled() {
        steamCmd.destExe.delete(); // Simulate not installed
        assertTrue(steamCmd.installIfNeeded());
    }

    @Test
    void testInstallIfNeededAlreadyInstalled() {
        // SteamCMD already installed
        assertTrue(steamCmd.installIfNeeded());
    }

    @Test
    void testInstallOrUpdateSuccessful() {
        // Customize the test scenario and assertions here
        boolean result = steamCmd.installOrUpdate(
                line -> {
                    // Handle log messages
                },
                errLine -> {
                    // Handle error log messages
                }
        );

        assertTrue(result);
        assertTrue(steamCmd.isInstalled());
    }

    /*@Test
    void testInstallOrUpdateUnsuccessful() {
        // Customize the test scenario and assertions here
        steamCmd.destArchive.delete(); // Simulate download failure
        boolean result = steamCmd.installOrUpdate(
                line -> {
                    // Handle log messages
                },
                errLine -> {
                    // Handle error log messages
                }
        );

        assertTrue(!result);
        assertTrue(!steamCmd.isInstalled());
    }*/
}