/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * GlobalData, which is always static and used frequently in this project
 */
public class GD {
    public static final String OFFICIAL_WEBSITE = "https://autoplug.online/";
    public static final String OFFICIAL_WEBSITE_IP = "144.91.78.158";
    public static final String AUTHOR = "Osiris Team";
    @NotNull
    public static String VERSION = "AutoPlug-Client - v(ERROR RETRIEVING VERSION)";
    public static File WORKING_DIR;
    public static File PLUGINS_DIR;
    @Nullable
    public static File SERVER_PATH = null; // Gets set in UpdaterConfig

    static {
        WORKING_DIR = new File(System.getProperty("user.dir"));
        PLUGINS_DIR = new File(System.getProperty("user.dir") + "/plugins");
        try {
            VERSION = "AutoPlug-Client - " + new UtilsJar().getThisJarsAutoPlugProperties().getProperty("version");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
