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

public class CheckConfig {

    private AutoPlugLogger logger = new AutoPlugLogger();
    private YamlFile config = new YamlFile("autoplug-check-config.yml");

    public void create(){

        // Load the YAML file if is already created or create new one otherwise
        try {
            if (!config.exists()) {
                logger.global_info(" - autoplug-check-config.yml not found! Creating new one...");
                config.createNewFile(true);
                logger.global_debugger("CheckConfig", "create", "Created file at: " + config.getFilePath());
            }
            else {
                logger.global_info(" - Loading autoplug-check-config.yml...");
            }
            config.load(); // Loads the entire file
        } catch (Exception e) {
            logger.global_warn(" [!] Failed to load autoplug-check-config.yml...");
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

        config.addDefault("autoplug-check-config.server-check.enable", false);
        config.addDefault("autoplug-check-config.server-check.software", "PAPER");
        config.addDefault("autoplug-check-config.server-check.version", "1.15.x");

        //User can choose between MANUAL and AUTOMATIC
        config.addDefault("autoplug-check-config.plugins-check.enable", true);
        config.addDefault("autoplug-check-config.plugins-check.profile", "MANUAL");

    }

    //User configuration
    public static boolean server_check;
    public static String server_software;
    public static String server_version;

    public static boolean plugin_check;
    public static String profile;

    private void setUserOptions(){

        //SERVER CHECK
        server_check = config.getBoolean("autoplug-check-config.server-check.enable");
        debugConfig("server_check", String.valueOf(server_check));
        server_software = config.getString("autoplug-check-config.server-check.software");
        debugConfig("server_software",server_software);
        server_version = config.getString("autoplug-check-config.server-check.version");
        debugConfig("server_version",server_version);

        //PLUGINS CHECK
        plugin_check = config.getBoolean("autoplug-check-config.plugins-check.enable");
        debugConfig("plugin_check", String.valueOf(plugin_check));
        profile =  config.getString("autoplug-check-config.plugins-check.profile");
        debugConfig("profile",profile);

    }

    private void validateOptions() {

        //Checking string values for issues
        if (!server_software.equals("PAPER")){
            String correction = "PAPER";
            logger.global_warn(" [!] Config error -> " +server_software+" must be: " +correction + " Applying default!");
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
