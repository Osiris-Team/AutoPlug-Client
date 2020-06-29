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

    public ServerConfig(){
        AutoPlugLogger.newClassDebug("ServerConfig");
    }

    private final YamlFile config = new YamlFile("autoplug-server-config.yml");

    public void load(){

        // Load the YAML file if is already created or create new one otherwise
        try {
            if (!config.exists()) {
                AutoPlugLogger.info(" - autoplug-server-config.yml not found! Creating new one...");
                config.createNewFile(true);
                AutoPlugLogger.debug("create", "Created file at: " + config.getFilePath());
            }
            else {
                AutoPlugLogger.info(" - Loading autoplug-server-config.yml...");
            }
            config.load(); // Loads the entire file
        } catch (Exception e) {
            AutoPlugLogger.warn(" [!] Failed to load autoplug-server-config.yml...");
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

        debug = config.getBoolean("autoplug-server-config.debug");
        debugConfig("debug", String.valueOf(debug));
        AutoPlugLogger.debug("setUserOptions", "Applying values for autoplug-server-config.yml");

        //SERVER
        server_key = config.getString("autoplug-server-config.server.key");
        debugConfig("server_key","##########");
        server_jar = config.getString("autoplug-server-config.server.jar");
        setGlobalServerPath();
        debugConfig("server_jar",server_jar);
        server_flags = config.getStringList("autoplug-server-config.server.flags");
        debugConfig("server_flags", String.valueOf(server_flags));


    }

    private void validateOptions() {
    }

    private void debugConfig(String config_name, String config_value){
        AutoPlugLogger.debug("debugConfig", "Setting value "+config_name+": "+config_value+"");
    }

    private void extraDebugOptions(){

        //Enable debug mode for libs
        Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        if (debug) {
            AutoPlugLogger.debug("extraDebugOptions", "Enabled HtmlUnit logger!");
            Logger.getLogger("com.gargoylesoftware").setLevel(Level.ALL);
        }

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
            AutoPlugLogger.warn(" [!] Issues while saving config.yml [!]");
        }

        AutoPlugLogger.info(" - Configuration file loaded!");

    }

}
