/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.osiris.autoplug.client.UTest;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.utils.MyBetterThreadManager;
import com.osiris.betterthread.exceptions.JLineLinkException;
import com.osiris.dyml.exceptions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskServerUpdaterTest {

    public TaskServerUpdaterTest() throws IOException {
        UTest.initLogger();
        UTest.initDefaults();
    }

    @Test
    void testPaper() throws JLineLinkException, NotLoadedException, YamlReaderException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, IllegalListException, InterruptedException {
        MyBetterThreadManager maMan = UTest.createManagerWithDisplayer();
        UpdaterConfig updaterConfig = new UpdaterConfig();
        updaterConfig.load();
        updaterConfig.server_updater.setValues("true");
        updaterConfig.server_updater_profile.setValues("AUTOMATIC");
        updaterConfig.server_software.setValues("paper");
        updaterConfig.server_version.setValues("1.18.2");
        updaterConfig.save();
        new TaskServerUpdater("ServerUpdater", maMan.manager)
                .start(); // Do not run too often because of rest API limits
        maMan.customDisplayer.join(); // Wait for completion
        assertEquals(0, maMan.manager.getAllWarnings().size());
    }
}