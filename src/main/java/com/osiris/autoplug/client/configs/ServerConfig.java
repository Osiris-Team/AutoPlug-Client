/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerConfig {

    private AutoPlugLogger logger = new AutoPlugLogger();
    private YamlFile config = new YamlFile("autoplug-server-config.yml");

    public void create(){

        // Load the YAML file if is already created or create new one otherwise
        try {
            if (!config.exists()) {
                logger.global_info(" - autoplug-server-config.yml not found! Creating new one...");
                config.createNewFile(true);
                logger.global_debugger("ServerConfig", "create", "Created file at: " + config.getFilePath());
            }
            else {
                logger.global_info(" - Loading autoplug-server-config.yml...");
            }
            config.load(); // Loads the entire file
        } catch (Exception e) {
            logger.global_warn(" [!] Failed to load autoplug-server-config.yml...");
            e.printStackTrace();
        }

        // Insert defaults
        insertDefaults();

        // Makes settings globally accessible
        setUserOptions();

        // Validates options
        validateOptions();

        // Debug specific
        extraDebugOptions();

        //Finally save the file
        save();

    }

    private void insertDefaults(){

        config.addDefault("autoplug-server-config.server.key", "1111");
        config.addDefault("autoplug-server-config.server.jar", "auto-find");
        List<String> list = Arrays.asList("Xms2G Xmx2G".split("[\\s]+"));
        config.addDefault("autoplug-server-config.server.flags", list);

        config.addDefault("autoplug-server-config.debug", false);

    }

    //User configuration
    public static String server_key;
    public static String server_jar;
    public static List<String> server_flags;
    public static boolean debug;

    private void setUserOptions(){

        //SERVER
        server_key = config.getString("autoplug-server-config.server.key");
        debugConfig("server_key","##########");
        server_jar = config.getString("autoplug-server-config.server.jar");
        setGlobalServerPath();
        debugConfig("server_jar",server_jar);
        server_flags = config.getStringList("autoplug-server-config.server.flags");
        debugConfig("server_flags", String.valueOf(server_flags));
        debug = config.getBoolean("autoplug-server-config.debug");
        debugConfig("debug", String.valueOf(debug));

    }

    private void validateOptions() {
    }

    private void debugConfig(String config_name, String config_value){
        logger.global_debugger("Config", "create", "Setting value "+config_name+": "+config_value+"");
    }

    private void extraDebugOptions(){

        //Set logger
        GD.DEBUG = config.getBoolean("autoplug-server-config.debug");

        //Enable debug mode for libs
        Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        if (this.debug) {
            logger.global_info(" - DEBUG mode enabled!");
            Logger.getLogger("com.gargoylesoftware").setLevel(Level.ALL);
        }
        logger.global_debugger("Settings", "create", "Copy default values: " + config.options().copyDefaults());
    }

    //Set the path in GD so its easier to access
    private void setGlobalServerPath() {
        FileManager fileManager = new FileManager();
        if (!server_jar.equals("auto-find")){
            GD.SERVER_PATH = fileManager.serverJar(server_jar);
        } else {
            GD.SERVER_PATH = fileManager.serverJar();
        }
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
