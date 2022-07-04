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
import com.osiris.betterthread.exceptions.JLineLinkException;
import com.osiris.dyml.exceptions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskServerUpdaterTest {

    @Test
    void testPaper() throws JLineLinkException, NotLoadedException, YamlReaderException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, IllegalListException, InterruptedException {
        UT.initLogger();
        UT.initDefaults();
        MyBThreadManager maMan = UT.createManagerWithDisplayer();
        UpdaterConfig updaterConfig = new UpdaterConfig();
        updaterConfig.load();
        updaterConfig.server_updater.setValues("true");
        updaterConfig.server_updater_profile.setValues("AUTOMATIC");
        updaterConfig.server_software.setValues("paper");
        updaterConfig.server_version.setValues("1.18.2");
        updaterConfig.server_build_id.setValues("");
        updaterConfig.save();
        new TaskServerUpdater("ServerUpdater", maMan.manager)
                .start(); // Do not run too often because of rest API limits
        maMan.minimalBThreadPrinter.join(); // Wait for completion
        assertEquals(0, maMan.manager.getAllWarnings().size());
    }

    @Test
    void testFabric() throws JLineLinkException, NotLoadedException, YamlReaderException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, IllegalListException, InterruptedException {
        UT.initLogger();
        UT.initDefaults();
        MyBThreadManager maMan = UT.createManagerWithDisplayer();
        UpdaterConfig updaterConfig = new UpdaterConfig();
        updaterConfig.load();
        updaterConfig.server_updater.setValues("true");
        updaterConfig.server_updater_profile.setValues("AUTOMATIC");
        updaterConfig.server_software.setValues("fabric");
        updaterConfig.server_version.setValues("1.18.2");
        updaterConfig.server_build_id.setValues("");
        updaterConfig.save();
        new TaskServerUpdater("ServerUpdater", maMan.manager)
                .start(); // Do not run too often because of rest API limits
        maMan.minimalBThreadPrinter.join(); // Wait for completion
        assertEquals(0, maMan.manager.getAllWarnings().size());
    }
}