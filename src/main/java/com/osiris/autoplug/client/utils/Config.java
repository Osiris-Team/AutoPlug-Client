/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.managers.FileManager;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YAML is a human-readable data serialization language.<br>
 * This class shows you how to use this API to use these files to save your data.
 * @author Carlos Lazaro Costa
 */
public final class Config {

    private AutoPlugLogger logger = new AutoPlugLogger();

    //User configuration
    public static String server_key;
    public static String server_jar;
    public static List<String> server_flags;

    public static boolean server_check;
    public static String server_software;
    public static String server_version;

    public static boolean backup_server;
    public static int backup_server_max_days;

    public static boolean backup_plugins;
    public static int backup_plugins_max_days;

    public static boolean plugin_check;
    public static String profile;

    public static boolean debug;

    public void create() {

        // Create new YAML file with relative path
        YamlFile yamlFile = new YamlFile("autoplug-config.yml");

        // Load the YAML file if is already created or create new one otherwise
        try {
            if (!yamlFile.exists()) {
                logger.global_info(" - Config not found! Creating new one: " + yamlFile.getFilePath() + "\n");
                yamlFile.createNewFile(true);
            }
            else {
                logger.global_info(" - Loading configurations...\n");
            }
            yamlFile.load(); // Loads the entire file
        } catch (Exception e) {
            e.printStackTrace();
        }

        // You can manage hierarchies by separating the sections with a dot at path
        // Let's put some values to the file
        yamlFile.addDefault("autoplug-config.server.key", "1111");
        yamlFile.addDefault("autoplug-config.server.jar", "auto-find");
        List<String> list = Arrays.asList("Xms2G Xmx2G".split("[\\s]+"));
        yamlFile.addDefault("autoplug-config.server.flags", list);

        yamlFile.addDefault("autoplug-config.server-check.enable", false);
        yamlFile.addDefault("autoplug-config.server-check.software", "PAPER");
        yamlFile.addDefault("autoplug-config.server-check.version", "1.15.x");

        yamlFile.addDefault("autoplug-config.backup-server-jar.enable", false);
        yamlFile.addDefault("autoplug-config.backup-server-jar.max-days", 7);

        yamlFile.addDefault("autoplug-config.backup-plugins.enable", true);
        yamlFile.addDefault("autoplug-config.backup-plugins.max-days", 7);

        //User can choose between MANUAL and AUTOMATIC
        yamlFile.addDefault("autoplug-config.plugins-check.enable", true);
        yamlFile.addDefault("autoplug-config.plugins-check.profile", "MANUAL");

        yamlFile.addDefault("autoplug-config.debug", false);

        //Set logger
        GD.DEBUG = yamlFile.getBoolean("autoplug-config.debug");

        //Enable debug mode for libs
        Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        if (this.debug) {
            logger.global_info(" - DEBUG mode enabled!");
            Logger.getLogger("com.gargoylesoftware").setLevel(Level.ALL);
        }
        logger.global_debugger("Settings", "create", "Copy default values: " + yamlFile.options().copyDefaults());

        //Set the data to be able to retrieve it from across the plugin
        server_key = yamlFile.getString("autoplug-config.server.key");
        server_jar = yamlFile.getString("autoplug-config.server.jar");
        setGlobalServerPath();
        server_flags = yamlFile.getStringList("autoplug-config.server.flags");

        server_check = yamlFile.getBoolean("autoplug-config.server-check.enable");
        server_software = yamlFile.getString("autoplug-config.server-check.software");
        server_version = yamlFile.getString("autoplug-config.server-check.version");

        backup_server = yamlFile.getBoolean("autoplug-config.backup-server-jar.enable");
        backup_server_max_days = yamlFile.getInt("autoplug-config.backup-server-jar.max-days");

        backup_plugins = yamlFile.getBoolean("autoplug-config.backup-plugins.enable");
        backup_plugins_max_days = yamlFile.getInt("autoplug-config.backup-plugins.max-days");

        plugin_check = yamlFile.getBoolean("autoplug-config.plugins-check.enable");
        profile =  yamlFile.getString("autoplug-config.plugins-check.profile");
        debug = yamlFile.getBoolean("autoplug-config.debug");

        logger.global_debugger("Config", "create", "Setting value server_key: ##########");
        logger.global_debugger("Config", "create", "Setting value server_jar: " + server_jar);
        logger.global_debugger("Config", "create", "Setting value server_flags: " + server_flags);
        logger.global_debugger("Config", "create", "Setting value server_check: " + server_check);
        logger.global_debugger("Config", "create", "Setting value server_software: " + server_software);
        logger.global_debugger("Config", "create", "Setting value server_version: " + server_version);
        logger.global_debugger("Config", "create", "Setting value backup_server: " + backup_server);
        logger.global_debugger("Config", "create", "Setting value backup_server_max_days: " + backup_server_max_days);
        logger.global_debugger("Config", "create", "Setting value backup_plugins: " + backup_plugins);
        logger.global_debugger("Config", "create", "Setting value backup_plugins_max_days: " + backup_plugins_max_days);
        logger.global_debugger("Config", "create", "Setting value plugin_check: " + plugin_check);
        logger.global_debugger("Config", "create", "Setting value profile: " + profile);
        logger.global_debugger("Config", "create", "Setting value debug: " + debug);

        logger.global_debugger("Config", "create", "Checking values for issues...");
        //TODO CHECK IF BOOLEANS GET AUTOMATICALLY DETECTED
        //Checking string values for issues
        if (!server_software.equals("PAPER")){
            String correction = "PAPER";
            logger.global_warn(" [!] Config error -> " + server_software+" must be: " +correction + " Applying default!");
            server_software = correction;
        }

        if (server_version.equals("1.15.x") || server_version.equals("1.14.x") || server_version.equals("1.13.x") || server_version.equals("1.12.x") || server_version.equals("1.8.x") ){
        } else{
            String correction = "1.15.x";
            logger.global_warn(" [!] Config error -> " + server_version+" must be: 1.15.x, 1.14.x, 1.13.x, 1.12.x, 1.8.x Applying default!");
            server_version = correction;
        }

        if (profile.equals("MANUAL") || profile.equals("AUTOMATIC") ){
        } else{
            String correction = "MANUAL";
            logger.global_warn(" [!] Config error -> " + profile+" must be: MANUAL or AUTOMATIC Applying default!");
            profile = correction;
        }

        // Finally, save changes!
        try {
            yamlFile.saveWithComments();
            // If your file has comments inside you have to save it with yamlFile.saveWithComments()
        } catch (IOException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Issues while saving config.yml [!]");
        }

        logger.global_info(" - Configuration file loaded!");

        // Now, you can restart this autoplug-config and see how the file is loaded due to it's already created

        // You can delete the generated file uncommenting next line and catching the I/O Exception
        // yamlFile.deleteFile();
    }

    //Set the path in GD so its easier to access
    private void setGlobalServerPath() {
        FileManager fileManager = new FileManager();
        if (!Config.server_jar.equals("auto-find")){
            GD.SERVER_PATH = fileManager.serverJar(Config.server_jar);
        } else {
            GD.SERVER_PATH = fileManager.serverJar();
        }
    }

}
