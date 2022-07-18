/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.osiris.autoplug.client.UT;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.utils.tasks.MyBThreadManager;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BWarning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskServerUpdaterTest {

    private void defaultTest(String serverSoftware) throws Exception {
        defaultTest(serverSoftware, null);
    }

    private void defaultTest(String serverSoftware, String version) throws Exception {
        if (version == null) version = "1.18.2";
        UT.initLogger();
        UT.initDefaults();
        MyBThreadManager maMan = UT.createManagerWithDisplayer();
        UpdaterConfig updaterConfig = new UpdaterConfig();
        updaterConfig.load();
        updaterConfig.server_updater.setValues("true");
        updaterConfig.server_updater_profile.setValues("AUTOMATIC");
        updaterConfig.server_software.setValues(serverSoftware);
        updaterConfig.server_version.setValues(version);
        updaterConfig.server_build_id.setValues("");
        updaterConfig.save();
        new TaskServerUpdater("ServerUpdater", maMan.manager)
                .start(); // Do not run too often because of rest API limits
        maMan.minimalBThreadPrinter.join(); // Wait for completion
        List<BWarning> warnings = maMan.manager.getAllWarnings();
        for (BWarning warning : warnings) {
            AL.warn(warning.getExtraInfo(), warning.getException());
        }
        assertEquals(0, warnings.size());
        assertTrue(maMan.manager.getAll().get(0).isSuccess());
    }

    @Test
    void testSpigot() throws Exception {
        defaultTest("spigot");
    }

    @Test
    void testWindSpigot() throws Exception {
        defaultTest("windspigot");
    }

    @Test
    void testBungeeCord() throws Exception {
        defaultTest("bungeecord");
    }

    @Test
    void testPaper() throws Exception {
        defaultTest("paper");
    }

    @Test
    void testWaterfall() throws Exception {
        defaultTest("waterfall", "1.18");
    }

    @Test
    void testVelocity() throws Exception {
        defaultTest("velocity", "3.1.1");
    }

    @Test
    void testTravertine() throws Exception {
        defaultTest("travertine", "1.16");
    }

    @Test
    void testPurpur() throws Exception {
        defaultTest("purpur");
    }

    @Test
    void testFabric() throws Exception {
        defaultTest("fabric");
    }

    @Test
    void testSpongeVanilla() throws Exception {
        defaultTest("spongevanilla");
    }

    @Test
    void testSpongeForge() throws Exception {
        defaultTest("spongeforge", "1.16.5");
    }

    @Test
    void testPatina() throws Exception {
        defaultTest("patina");
    }

    @Test
    void testPufferfish() throws Exception {
        defaultTest("pufferfish");
    }

    @Test
    void testMirai() throws Exception {
        defaultTest("mirai");
    }

    @Test
    void testPearl() throws Exception {
        defaultTest("pearl");
    }


}