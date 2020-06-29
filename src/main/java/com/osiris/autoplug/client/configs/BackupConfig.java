/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.utils.AutoPlugLogger;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.IOException;

public class BackupConfig {

    public BackupConfig(){
        AutoPlugLogger.newClassDebug("BackupConfig");
    }

    private final YamlFile config = new YamlFile("autoplug-backup-config.yml");

    public void load(){

        // Load the YAML file if is already created or create new one otherwise
        try {
            if (!config.exists()) {
                AutoPlugLogger.info(" - autoplug-backup-config.yml not found! Creating new one...");
                config.createNewFile(true);
                AutoPlugLogger.debug("create", "Created file at: " + config.getFilePath());
            }
            else {
                AutoPlugLogger.info(" - Loading autoplug-backup-config.yml...");
            }
            config.load(); // Loads the entire file
        } catch (Exception e) {
            AutoPlugLogger.warn("Failed to load autoplug-backup-config.yml...");
            e.printStackTrace();
        }

        // Insert defaults
        insertDefaults();

        // Makes settings globally accessible
        setUserOptions();

        // Validates options
        validateOptions();

        //Finally save the file
        save();

    }

    private void insertDefaults(){

        config.addDefault("autoplug-backup-config.server-files-backup.enable", false);
        config.addDefault("autoplug-backup-config.server-files-backup.max-days", (int)7);

        config.addDefault("autoplug-backup-config.worlds-backup.enable", false);
        config.addDefault("autoplug-backup-config.worlds-backup.max-days", (int)7);

        config.addDefault("autoplug-backup-config.plugins-backup.enable", true);
        config.addDefault("autoplug-backup-config.plugins-backup.max-days", (int)7);

    }

    //User configuration
    public static boolean backup_server;
    public static int backup_server_max_days;

    public static boolean backup_worlds;
    public static int backup_worlds_max_days;

    public static boolean backup_plugins;
    public static int backup_plugins_max_days;

    private void setUserOptions(){

        AutoPlugLogger.debug("setUserOptions", "Applying values for autoplug-backup-config.yml");

        //SERVER JAR BACKUP
        backup_server = config.getBoolean("autoplug-backup-config.server-files-backup.enable");
        debugConfig("backup_server", String.valueOf(backup_server));
        backup_server_max_days = config.getInt("autoplug-backup-config.server-files-backup.max-days");
        debugConfig("backup_server_max_days", String.valueOf(backup_server_max_days));

        //WORLDS BACKUP
        backup_worlds = config.getBoolean("autoplug-backup-config.worlds-backup.enable");
        debugConfig("backup_worlds", String.valueOf(backup_worlds));
        backup_worlds_max_days = config.getInt("autoplug-backup-config.worlds-backup.max-days");
        debugConfig("backup_worlds_max_days", String.valueOf(backup_worlds_max_days));

        //PLUGINS BACKUP
        backup_plugins = config.getBoolean("autoplug-backup-config.plugins-backup.enable");
        debugConfig("backup_plugins", String.valueOf(backup_plugins));
        backup_plugins_max_days = config.getInt("autoplug-backup-config.plugins-backup.max-days");
        debugConfig("backup_plugins_max_days", String.valueOf(backup_plugins_max_days));

    }

    private void validateOptions() {
    }

    //Shortcut for easier logging
    private void debugConfig(String config_name, String config_value){
        AutoPlugLogger.debug("debugConfig", "Setting value "+config_name+": "+config_value+"");
    }


    private void save() {

        // Finally, save changes!
        try {
            config.saveWithComments();
        } catch (IOException e) {
            e.printStackTrace();
            AutoPlugLogger.warn("Issues while saving config.yml");
        }

        AutoPlugLogger.info(" - Configuration file loaded!");

    }

}
