/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;

import java.io.File;
import java.io.InputStream;

/**
 * GlobalData, which is always static and used frequently in this project
 */
public class GD {
    public static final String SPIGOT_URL = "https://www.spigotmc.org/members/osiristeam.935748/";
    public static final String OFFICIAL_WEBSITE = "https://autoplug.online/";
    public static final String OFFICIAL_WEBSITE_RAW = "autoplug.online";
    public static final String OFFICIAL_WEBSITE_IP = "144.91.78.158";
    public static final String VERSION = "AutoPlug-Client - v0.9";
    public static final String COPYRIGHT = "Copyright (c) 2020 Osiris Team";
    public static final String COPYRIGHT_WEBSITE = "https://raw.githubusercontent.com/Osiris-Team/AutoPlug-Client/master/LICENSE";

    public static File WORKING_DIR = new File(System.getProperty("user.dir"));
    public static File PLUGINS_DIR = new File(System.getProperty("user.dir")+"/plugins");

    public static InputStream MC_SERVER_IN; // Get input stream after starting the server
    public static File SERVER_PATH = null; // Gets defined in config

    private static File LATEST_LOG;
    public static File getLatestLog(){
        if (LATEST_LOG==null) LATEST_LOG = new File(WORKING_DIR+"/autoplug-logs/latest.log");
        if (!LATEST_LOG.exists()) LATEST_LOG.mkdirs();
        return LATEST_LOG;
    }
}
