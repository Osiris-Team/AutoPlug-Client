/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater;

import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;
import org.junit.jupiter.api.Test;

import java.io.File;

class TaskPluginDownloadTest {

    @Test
    void pluginDownloadTest() throws Exception {
        BetterThreadManager man = new BetterThreadManager();
        BetterThreadDisplayer dis = new BetterThreadDisplayer(man);
        dis.start();

        TaskPluginDownload download = new TaskPluginDownload("Downloader", man,
                "Autorank",
                "LATEST", "https://api.spiget.org/v2/resources/3239/download", "MANUAL",
                new File(""+System.getProperty("user.dir")+"/src/main/test/TestPlugin.jar"));
        download.start();

        TaskPluginDownload download1 = new TaskPluginDownload("Downloader", man,
                "UltimateChat",
                "LATEST", "https://api.spiget.org/v2/resources/23767/download", "MANUAL",
                new File(""+System.getProperty("user.dir")+"/src/main/test/TestPlugin.jar"));
        download1.start();

        TaskPluginDownload download2 = new TaskPluginDownload("Downloader", man,
                "ViaRewind",
                "LATEST", "https://api.spiget.org/v2/resources/52109/download", "MANUAL",
                new File(""+System.getProperty("user.dir")+"/src/main/test/TestPlugin.jar"));
        download2.start();


        while (!download.isFinished() || !download1.isFinished() || !download2.isFinished())
            Thread.sleep(500);
    }
}