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

    private AutoPlugLogger logger = new AutoPlugLogger();
    private YamlFile config = new YamlFile("autoplug-backup-config.yml");

    public void create(){

        // Load the YAML file if is already created or create new one otherwise
        try {
            if (!config.exists()) {
                logger.global_info(" - autoplug-backup-config.yml not found! Creating new one...");
                config.createNewFile(true);
                logger.global_debugger("BackupConfig", "create", "Created file at: " + config.getFilePath());
            }
            else {
                logger.global_info(" - Loading autoplug-backup-config.yml...");
            }
            config.load(); // Loads the entire file
        } catch (Exception e) {
            logger.global_warn(" [!] Failed to load autoplug-backup-config.yml...");
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
        config.addDefault("autoplug-backup-config.server-files-backup.max-days", 7);

        config.addDefault("autoplug-backup-config.worlds-backup.enable", false);
        config.addDefault("autoplug-backup-config.worlds-backup.max-days", 7);

        config.addDefault("autoplug-backup-config.plugins-backup.enable", true);
        config.addDefault("autoplug-backup-config.plugins-backup.max-days", 7);

    }

    //User configuration
    public static boolean backup_server;
    public static int backup_server_max_days;

    public static boolean backup_worlds;
    public static int backup_worlds_max_days;

    public static boolean backup_plugins;
    public static int backup_plugins_max_days;

    private void setUserOptions(){

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

    private void debugConfig(String config_name, String config_value){
        logger.global_debugger("Config", "create", "Setting value "+config_name+": "+config_value+"");
    }

    private void save() {

        // Finally, save changes!
        try {
            config.saveWithComments();
        } catch (IOException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Issues while saving config.yml [!]");
        }

        logger.global_info(" - Configuration file loaded!");

    }

}
