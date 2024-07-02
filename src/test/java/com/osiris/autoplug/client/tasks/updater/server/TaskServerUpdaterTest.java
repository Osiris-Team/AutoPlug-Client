/*
 * Copyright (c) 2022-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.osiris.autoplug.client.UtilsTest;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.utils.tasks.MyBThreadManager;
import com.osiris.betterthread.BWarning;
import com.osiris.jlib.logger.AL;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskServerUpdaterTest {

    private void defaultTest(String serverSoftware) throws Exception {
        defaultTest(serverSoftware, null);
    }

    private void defaultTest(String serverSoftware, String version) throws Exception {
        if (true) return; // TODO RE-ENABLE TESTS
        if (version == null) version = "1.18.2";
        UtilsTest.init();
        MyBThreadManager maMan = UtilsTest.createManagerWithDisplayer();
        UpdaterConfig updaterConfig = new UpdaterConfig();
        updaterConfig.load();
        updaterConfig.server_updater.setValues("true");
        updaterConfig.server_updater_profile.setValues("AUTOMATIC");
        updaterConfig.server_software.setValues(serverSoftware);
        updaterConfig.server_updater_version.setValues(version);
        updaterConfig.server_build_id.setValues("");
        updaterConfig.save();
        new TaskServerUpdater("ServerUpdater", maMan.manager)
                .start(); // Do not run too often because of rest API limits
        maMan.printer.join(); // Wait for completion
        List<BWarning> warnings = maMan.manager.getAllWarnings();
        for (BWarning warning : warnings) {
            AL.warn(warning.getExtraInfo(), warning.getException());
        }
        assertEquals(0, warnings.size());
        assertTrue(maMan.manager.getAll().get(0).isSuccess());
    }

    @org.junit.jupiter.api.Test
    void testSpigot() throws Exception {
        defaultTest("spigot", "1.16.5");
    }

    @org.junit.jupiter.api.Test
    void testWindSpigot() throws Exception {
        defaultTest("windspigot");
    }

    @org.junit.jupiter.api.Test
    void testBungeeCord() throws Exception {
        defaultTest("bungeecord");
    }

    @org.junit.jupiter.api.Test
    void testPaper() throws Exception {
        defaultTest("paper");
    }

    @org.junit.jupiter.api.Test
    void testWaterfall() throws Exception {
        defaultTest("waterfall", "1.18");
    }

    @org.junit.jupiter.api.Test
    void testVelocity() throws Exception {
        defaultTest("velocity", "3.1.1");
    }

    @org.junit.jupiter.api.Test
    void testTravertine() throws Exception {
        defaultTest("travertine", "1.16");
    }

    @org.junit.jupiter.api.Test
    void testPurpur() throws Exception {
        defaultTest("purpur");
    }

    @org.junit.jupiter.api.Test
    void testFabric() throws Exception {
        defaultTest("fabric");
    }

    @org.junit.jupiter.api.Test
    void testSpongeVanilla() throws Exception {
        defaultTest("spongevanilla");
    }

    @org.junit.jupiter.api.Test
    void testSpongeForge() throws Exception {
        defaultTest("spongeforge", "1.16.5");
    }

    @org.junit.jupiter.api.Test
    void testPatina() throws Exception {
        defaultTest("patina");
    }

    @org.junit.jupiter.api.Test
    void testPufferfish() throws Exception {
        defaultTest("pufferfish");
    }

    @org.junit.jupiter.api.Test
    void testMirai() throws Exception {
        defaultTest("mirai");
    }

    @org.junit.jupiter.api.Test
    void testPearl() throws Exception {
        defaultTest("pearl");
    }


}