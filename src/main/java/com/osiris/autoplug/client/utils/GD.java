/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.Target;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import com.osiris.dyml.exceptions.YamlReaderException;
import com.osiris.dyml.exceptions.YamlWriterException;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

/**
 * GlobalData, which is always static and used frequently in this project
 */
public class GD {
    public static final String AUTHOR = "Osiris-Team";
    public static final File FILE_ERR_OUT = new File(System.getProperty("user.dir") + "/autoplug/logs/console-mirror-err.log");
    // TODO make all of these not static and deprecate this class
    public static String OFFICIAL_WEBSITE = "https://autoplug.one/";
    public static boolean IS_TEST_MODE = false;
    @NotNull
    public static String VERSION = "AutoPlug-Client (ERROR RETRIEVING VERSION)";
    public static File WORKING_DIR;
    public static File PLUGINS_DIR;
    public static File DOWNLOADS_DIR;
    public static File AP_LATEST_LOG = new File(System.getProperty("user.dir") + "/autoplug/logs/latest.log");
    public static File SYSTEM_LATEST_LOG = new File(System.getProperty("user.dir") + "/autoplug/logs/system-latest.log");
    public static File FILE_OUT = new File(System.getProperty("user.dir") + "/autoplug/logs/console-mirror.log");
    public static Target TARGET = null;

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

    public static String errorMsgFailedToGetMCVersion() {
        return "Failed to determine Minecraft version. Make sure the server jar exists or the version is provided in general.yml or updater.yml.";
    }

    public static void determineTarget(GeneralConfig generalConfig) throws YamlReaderException, YamlWriterException, IOException, DuplicateKeyException, IllegalListException {
        String target = generalConfig.autoplug_target_software.asString();
        while (true) {
            if (target == null) {
                for (String comment : generalConfig.autoplug_target_software.getComments()) {
                    AL.info(comment);
                }
                AL.info("Please enter a valid option for target ("+ Arrays.stream(Target.values()).map(t -> " "+t.name()) +") and press enter:");
                target = new Scanner(System.in).nextLine();
                generalConfig.autoplug_target_software.setValues(target);
                generalConfig.save();
            } else {
                TARGET = Target.fromString(target);
                if (TARGET != null) break;
                for (String comment : generalConfig.autoplug_target_software.getComments()) {
                    AL.info(comment);
                }
                AL.info("The selected target software '" + target + "' is not a valid option.");
                AL.info("Please enter a valid option and press enter:");
                target = new Scanner(System.in).nextLine();
                generalConfig.autoplug_target_software.setValues(target);
                generalConfig.save();
            }
        }
    }
}
