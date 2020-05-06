/*
 *  Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.osiris.autoplug.client.utils.AutoPlugLogger;
import org.simpleyaml.configuration.file.YamlFile;

/**
 * YAML is a human-readable data serialization language.<br>
 * This class shows you how to use this API to use these files to save your data.
 * @author Carlos Lazaro Costa
 */
public final class Settings {

    private AutoPlugLogger logger = new AutoPlugLogger();

    //User configuration
    private static boolean config_server_check;
    private static String config_server_software;
    private static String config_server_version;
    private static String config_profile;
    private static boolean config_debug;

    public void create() {

        // Create new YAML file with relative path
        YamlFile yamlFile = new YamlFile("autoplug-config.yml");

        logger.global_info(" Checking autoplug-config.yml...");
        // Load the YAML file if is already created or create new one otherwise
        try {
            if (!yamlFile.exists()) {
                logger.global_info(" Config not found! Creating new one: " + yamlFile.getFilePath() + "\n");
                yamlFile.createNewFile(true);
            }
            else {
                logger.global_info(" Loading configurations...\n");
            }
            yamlFile.load(); // Loads the entire file
        } catch (Exception e) {
            e.printStackTrace();
        }

        // You can manage hierarchies by separating the sections with a dot at path
        // Let's put some values to the file

        yamlFile.addDefault("autoplug-config.server-check.enable", false);
        yamlFile.addDefault("autoplug-config.server-check.software", "PAPER");
        yamlFile.addDefault("autoplug-config.server-check.version", "1.15");
        //User can choose between MANUAL and AUTOMATIC
        yamlFile.addDefault("autoplug-config.profile", "MANUAL");
        yamlFile.addDefault("autoplug-config.debug", false);

        //Set logger
        GLOBALDATA.setDEBUG(yamlFile.getBoolean("autoplug-config.debug"));
        logger.global_debugger("Settings", "create", "Copy default values: " + yamlFile.options().copyDefaults());

        //Set the data to be able to retrieve it accross the plugin
        setConfig_server_check(yamlFile.getBoolean("autoplug-config.server-check.enable"));
        setConfig_server_software(yamlFile.getString("autoplug-config.server-check.software"));
        setConfig_server_version(yamlFile.getString("autoplug-config.server-check.version"));
        setConfig_profile(yamlFile.getString("autoplug-config.profile"));
        setConfig_debug(yamlFile.getBoolean("autoplug-config.debug"));



        // Finally, save changes!
        try {
            yamlFile.saveWithComments();
            // If your file has comments inside you have to save it with yamlFile.saveWithComments()
        } catch (IOException e) {
            logger.global_warn("Issues while saving config.yml!");
            e.printStackTrace();
        }

        // Now, you can restart this autoplug-config and see how the file is loaded due to it's already created

        // You can delete the generated file uncommenting next line and catching the I/O Exception
        // yamlFile.deleteFile();
    }

    public boolean isConfig_server_check() {
        logger.global_debugger("Settings", "getConfig_server_check" ,""+config_server_check);
        return config_server_check;
    }

    public void setConfig_server_check(boolean config_server_check) {
        Settings.config_server_check = config_server_check;
        logger.global_debugger("Settings", "setConfig_server_check" ,""+config_server_check);
    }


    public String getConfig_server_software() {
        logger.global_debugger("Settings", "getConfig_server_software" ,""+config_server_software);
        return config_server_software;
    }

    public void setConfig_server_software(String config_server_software) {
        Settings.config_server_software = config_server_software;
        logger.global_debugger("Settings", "setConfig_server_software" ,""+config_server_software);
    }

    public String getConfig_server_version() {
        logger.global_debugger("Settings", "getConfig_server_version" ,""+config_server_version);
        return config_server_version;
    }

    public void setConfig_server_version(String config_server_version) {
        Settings.config_server_version = config_server_version;
        logger.global_debugger("Settings", "setConfig_server_version" ,""+config_server_version);
    }

    public String getConfig_profile() {
        logger.global_debugger("Settings", "getConfig_server_version" ,""+config_profile);
        return config_profile;
    }

    public void setConfig_profile(String config_profile) {
        Settings.config_profile = config_profile;
        logger.global_debugger("Settings", "setConfig_server_version" ,""+config_profile);
    }

    public boolean isConfig_debug() {
        logger.global_debugger("Settings", "isConfig_debug" ,""+config_debug);
        return config_debug;
    }

    public void setConfig_debug(boolean config_debug) {
        Settings.config_debug = config_debug;
        logger.global_debugger("Settings", "setConfig_debug" ,""+config_debug);
    }
}
