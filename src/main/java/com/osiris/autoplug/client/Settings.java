/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.utils.AutoPlugLogger;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.IOException;
import java.util.List;

/**
 * YAML is a human-readable data serialization language.<br>
 * This class shows you how to use this API to use these files to save your data.
 * @author Carlos Lazaro Costa
 */
public final class Settings {

    private AutoPlugLogger logger = new AutoPlugLogger();

    //User configuration
    private static boolean server_check;
    private static String server_software;
    private static String server_version;

    private static boolean backup_server;
    private static int backup_server_max_days;

    private static boolean backup_plugins;
    private static int backup_plugins_max_days;

    private static boolean plugin_check;
    private static String profile;
    private static List plugin_excluded;
    private static boolean debug;

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
        yamlFile.addDefault("autoplug-config.server-check.version", "1.15.x");

        yamlFile.addDefault("autoplug-config.backup-server.enable", false);
        yamlFile.addDefault("autoplug-config.backup-server.max-days", 7);

        yamlFile.addDefault("autoplug-config.backup-plugins.enable", true);
        yamlFile.addDefault("autoplug-config.backup-plugins.max-days", 7);

        //User can choose between MANUAL and AUTOMATIC
        yamlFile.addDefault("autoplug-config.plugins-check.enable", true);
        yamlFile.addDefault("autoplug-config.plugins-check.profile", "MANUAL");
        yamlFile.addDefault("autoplug-config.plugins-check.excluded-plugins", "[Plugin1,Plugin2]");

        yamlFile.addDefault("autoplug-config.debug", false);

        //Set logger
        GLOBALDATA.setDEBUG(yamlFile.getBoolean("autoplug-config.debug"));
        logger.global_debugger("Settings", "create", "Copy default values: " + yamlFile.options().copyDefaults());

        //Set the data to be able to retrieve it accross the plugin
        setServer_check(yamlFile.getBoolean("autoplug-config.server-check.enable"));
        setServer_software(yamlFile.getString("autoplug-config.server-check.software"));
        setServer_version(yamlFile.getString("autoplug-config.server-check.version"));

        setBackupServer(yamlFile.getBoolean("autoplug-config.backup-server.enable"));
        setBackupServerMaxDays(yamlFile.getInt("autoplug-config.backup-server.max-days"));

        setBackupPlugins(yamlFile.getBoolean("autoplug-config.backup-plugins.enable"));
        setBackupPluginsMaxDays(yamlFile.getInt("autoplug-config.backup-plugins.max-days"));

        setPlugin_check(yamlFile.getBoolean("autoplug-config.plugins-check.enable"));
        setProfile(yamlFile.getString("autoplug-config.plugins-check.profile"));
        setPlugin_excluded(yamlFile.getStringList("autoplug-config.plugins-check.excluded-plugins")); //todo list
        setDebug(yamlFile.getBoolean("autoplug-config.debug"));

        System.out.println("PLUGINS EXCLUDED: "+getPlugin_excluded());



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

    public boolean isServer_check() {
        logger.global_debugger("Settings", "getServer_check" ,""+server_check);
        return server_check;
    }
    public void setServer_check(boolean server_check) {
        Settings.server_check = server_check;
        logger.global_debugger("Settings", "setServer_check" ,""+server_check);
    }

    public String getServer_software() {
        logger.global_debugger("Settings", "getServer_software" ,""+server_software);
        return server_software;
    }
    public void setServer_software(String server_software) {
        Settings.server_software = server_software;
        logger.global_debugger("Settings", "setServer_software" ,""+server_software);
    }

    public String getServer_version() {
        logger.global_debugger("Settings", "getServer_version" ,""+server_version);
        return server_version;
    }
    public void setServer_version(String server_version) {
        Settings.server_version = server_version;
        logger.global_debugger("Settings", "setServer_version" ,""+server_version);
    }

    public boolean isBackupServer() {
        logger.global_debugger("Settings", "isBackupServer" ,""+backup_server);
        return backup_server;
    }
    public void setBackupServer(boolean backup_server) {
        Settings.backup_server = backup_server;
        logger.global_debugger("Settings", "setBackupServer" ,""+backup_server);
    }

    public int getBackupServerMaxDays() {
        logger.global_debugger("Settings", "getBackupServer" ,""+backup_server_max_days);
        return backup_server_max_days;
    }
    public void setBackupServerMaxDays(int backup_server_max_days) {
        Settings.backup_server_max_days = backup_server_max_days;
        logger.global_debugger("Settings", "setBackupServer" ,""+backup_server_max_days);
    }

    public boolean isBackupPlugins() {
        logger.global_debugger("Settings", "setBackupPlugins" ,""+backup_plugins);
        return backup_plugins;
    }
    public void setBackupPlugins(boolean backup_plugins) {
        Settings.backup_plugins = backup_plugins;
        logger.global_debugger("Settings", "setBackupPlugins" ,""+backup_plugins);
    }

    public int getBackupPluginsMaxDays() {
        logger.global_debugger("Settings", "getBackupPluginsMaxDays" ,""+backup_plugins_max_days);
        return backup_plugins_max_days;
    }
    public void setBackupPluginsMaxDays(int backup_plugins_max_days) {
        Settings.backup_plugins_max_days = backup_plugins_max_days;
        logger.global_debugger("Settings", "setBackupPluginsMaxDays" ,""+backup_plugins_max_days);
    }

    public boolean isPlugin_check() {
        logger.global_debugger("Settings", "isPlugin_check" ,""+plugin_check);
        return plugin_check;
    }
    public void setPlugin_check(boolean plugin_check) {
        Settings.plugin_check = plugin_check;
        logger.global_debugger("Settings", "setPlugin_check" ,""+plugin_check);
    }

    public String getProfile() {
        logger.global_debugger("Settings", "getServer_version" ,""+profile);
        return profile;
    }
    public void setProfile(String profile) {
        Settings.profile = profile;
        logger.global_debugger("Settings", "setServer_version" ,""+profile);
    }

    public List getPlugin_excluded() {
        logger.global_debugger("Settings", "getPlugin_excluded" ,""+plugin_excluded);
        return plugin_excluded;
    }
    public void setPlugin_excluded(List plugin_excluded) {
        Settings.plugin_excluded = plugin_excluded;
        logger.global_debugger("Settings", "setPlugin_excluded" ,""+plugin_excluded);
    }

    public boolean isDebug() {
        logger.global_debugger("Settings", "isDebug" ,""+debug);
        return debug;
    }
    public void setDebug(boolean debug) {
        Settings.debug = debug;
        logger.global_debugger("Settings", "setDebug" ,""+debug);
    }
}
