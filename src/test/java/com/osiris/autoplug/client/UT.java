/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.tasks.CustomBThreadPrinter;
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
        CustomBThreadPrinter customBThreadPrinter = new CustomBThreadPrinter(manager);
        customBThreadPrinter.timeoutMs = 1000;
        customBThreadPrinter.start();
        return new MyBThreadManager(manager, null, customBThreadPrinter);
    }

    public static void init() throws IOException {
        initDefaults();
        initLogger();
    }

    public static void initDefaults() throws IOException {
        GD.DOWNLOADS_DIR = new File(System.getProperty("user.dir") + "/test/downloads");
        GD.DOWNLOADS_DIR.mkdirs();

        GD.SERVER_JAR = new File(System.getProperty("user.dir") + "/test/server.jar");
        if (!GD.SERVER_JAR.exists()) GD.SERVER_JAR.createNewFile();
    }

    public static void initLogger() {
        new AL().start("AL", true, new File(System.getProperty("user.dir") + "/test/ap-logs"), false);
    }
}
