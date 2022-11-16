/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * GlobalData, which is always static and used frequently in this project
 */
public class GD {
    // TODO make all of these not static and deprecate this class
    public static String OFFICIAL_WEBSITE = "https://autoplug.one/";
    public static final String AUTHOR = "Osiris-Team";
    public static final File FILE_ERR_OUT = new File(System.getProperty("user.dir") + "/autoplug/logs/console-mirror-err.log");
    public static boolean IS_TEST_MODE = false;
    @NotNull
    public static String VERSION = "AutoPlug-Client (ERROR RETRIEVING VERSION)";
    public static File WORKING_DIR;
    public static File PLUGINS_DIR;
    public static File DOWNLOADS_DIR;
    public static File LOG_FILE = new File(System.getProperty("user.dir") + "/autoplug/logs/latest.log");
    public static File FILE_OUT = new File(System.getProperty("user.dir") + "/autoplug/logs/console-mirror.log");

    static {
        WORKING_DIR = new File(System.getProperty("user.dir"));
        PLUGINS_DIR = new File(System.getProperty("user.dir") + "/plugins");
        DOWNLOADS_DIR = new File(System.getProperty("user.dir") + "/autoplug/downloads");
        try {
            VERSION = "AutoPlug-Client " + new UtilsJar().getThisJarsAutoPlugProperties().getProperty("version");
        } catch (Exception e) {
            System.err.println("Failed to determine AutoPlug-Client version. More details below. Keep in mind that" +
                    " the exception is ignored and does not further affect the application.");
            e.printStackTrace();
        }
    }

}
