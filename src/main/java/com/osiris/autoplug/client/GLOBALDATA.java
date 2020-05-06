/*
 *  Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;

public class GLOBALDATA {

    public static final String SPIGOT_URL = "https://www.spigotmc.org/members/osiristeam.935748/";
    public static String OFFICIAL_WEBSITE = "http://autoplug.ddns.net/";
    public static String VERSION = "AutoPlugClient - v0.1";
    public static String COPYRIGHT = "Copyright (c) 2020 Osiris Team";
    public static String COPYRIGHT_WEBSITE = "https://raw.githubusercontent.com/Osiris-Team/AutoPlug-Client/master/LICENSE";
    public static boolean DEBUG = true;
    public static boolean WINDOWS_OS = false;

    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static void setDEBUG(boolean DEBUG) {
        GLOBALDATA.DEBUG = DEBUG;
    }

    public static boolean isWindowsOs() {
        return WINDOWS_OS;
    }

    public static void setWindowsOs(boolean windowsOs) {
        WINDOWS_OS = windowsOs;
    }
}
