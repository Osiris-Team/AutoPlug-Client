/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater;

import com.osiris.autoplug.client.UtilsTest;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginDownload;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;
import com.osiris.dyml.utils.UtilsTimeStopper;
import com.osiris.jlib.logger.AL;

import java.io.File;

class TaskPluginDownloadTest {

    @org.junit.jupiter.api.Test
    void pluginDownloadTest() throws Exception {
        UtilsTest.init();
        BThreadManager man = new BThreadManager();
        BThreadPrinter printer = new BThreadPrinter(man);
        printer.start();

        UtilsTimeStopper timeStopper = new UtilsTimeStopper();
        timeStopper.start();
        TaskPluginDownload download = new TaskPluginDownload("Downloader", man,
                "Autorank",
                "LATEST", "https://api.spiget.org/v2/resources/3239/download", "MANUAL",
                new File("" + System.getProperty("user.dir") + "/src/main/test/TestPlugin.jar"));
        download.start();

        TaskPluginDownload download1 = new TaskPluginDownload("Downloader", man,
                "UltimateChat",
                "LATEST", "https://api.spiget.org/v2/resources/23767/download", "MANUAL",
                new File("" + System.getProperty("user.dir") + "/src/main/test/TestPlugin.jar"));
        download1.start();

        TaskPluginDownload download2 = new TaskPluginDownload("Downloader", man,
                "ViaRewind",
                "LATEST", "https://api.spiget.org/v2/resources/52109/download", "MANUAL",
                new File("" + System.getProperty("user.dir") + "/src/main/test/TestPlugin.jar"));
        download2.start();

        TaskPluginsUpdater taskPluginsUpdater = new TaskPluginsUpdater("PluginsUpdater", man);
        taskPluginsUpdater.start();

        while (!download.isFinished() || !download1.isFinished() || !download2.isFinished())
            Thread.sleep(100);
        timeStopper.stop();
        AL.info("Time took to finish download tasks: " + timeStopper.getFormattedSeconds() + " seconds!");
    }
}