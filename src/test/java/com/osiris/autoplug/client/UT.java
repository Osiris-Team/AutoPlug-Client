/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.tasks.MinimalBThreadPrinter;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.tasks.MyBThreadManager;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.exceptions.JLineLinkException;

import java.io.File;
import java.io.IOException;

/**
 * Utils for tests.
 */
public class UT {
    public static MyBThreadManager createManagerWithDisplayer() throws JLineLinkException {
        BThreadManager manager = new BThreadManager();
        MinimalBThreadPrinter minimalBThreadPrinter = new MinimalBThreadPrinter(manager);
        minimalBThreadPrinter.timeoutMs = 1000;
        minimalBThreadPrinter.start();
        return new MyBThreadManager(manager, null, minimalBThreadPrinter);
    }

    public static void init() throws IOException {
        initDefaults();
        initLogger();
    }

    public static void initDefaults() throws IOException {
        GD.VERSION = "AutoPlug-Client Test-Version";
        GD.WORKING_DIR = new File(System.getProperty("user.dir") + "/test");
        System.setProperty("user.dir", GD.WORKING_DIR.getAbsolutePath());
        GD.DOWNLOADS_DIR = new File(GD.WORKING_DIR + "/downloads");
        GD.DOWNLOADS_DIR.mkdirs();

        File serverJar = new File(GD.WORKING_DIR + "/server.jar");
        GeneralConfig config = null;
        try {
            config = new GeneralConfig();
            config.lockFile();
            config.load();
            config.server_start_command.setValues("java -jar \"" + serverJar + "\"");
            config.save();
        } catch (Exception e) {
            if (config != null) config.unlockFile();
            throw new RuntimeException(e);
        } finally {
            if (config != null) config.unlockFile();
        }

        if (!serverJar.exists()) serverJar.createNewFile();
    }

    public static void initLogger() {
        new AL().start("AL", true, new File(System.getProperty("user.dir") + "/test/ap-logs/latest.log"), false);
    }
}
