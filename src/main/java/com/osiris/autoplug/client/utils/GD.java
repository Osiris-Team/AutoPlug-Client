/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.managers.FileManager;

import java.io.File;

/**
 * GlobalData, which is always static and used frequently in this project
 */
public class GD {

    public static final String SPIGOT_URL = "https://www.spigotmc.org/members/osiristeam.935748/";
    public static String OFFICIAL_WEBSITE = "https://autoplug.ddns.net/";
    public static String VERSION = "AutoPlugClient - v0.5";
    public static String COPYRIGHT = "Copyright (c) 2020 Osiris Team";
    public static String COPYRIGHT_WEBSITE = "https://raw.githubusercontent.com/Osiris-Team/AutoPlug-Client/master/LICENSE";
    public static File WORKING_DIR = new File(System.getProperty("user.dir"));
    public static File PLUGINS_DIR = new File(System.getProperty("user.dir")+"/plugins");
    public static File SERVER_PATH = null;

    public static boolean DEBUG = true;
    public static boolean WINDOWS_OS = false;
}
