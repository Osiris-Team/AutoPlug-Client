/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;


import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.configs.CheckConfig;
import com.osiris.autoplug.client.configs.RestarterConfig;
import com.osiris.autoplug.client.configs.ServerConfig;
import com.osiris.autoplug.client.network.local.LocalListener;
import com.osiris.autoplug.client.server.Server;
import com.osiris.autoplug.client.server.UserInput;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;

import java.io.File;

public class Main {


    public Main(){
        AutoPlugLogger.newClassDebug("Main");
    }

    public static void main(String[]args){

        System.out.println("Initialising "+GD.VERSION);

        //Check if all directories are there
        File autoplug_cache = new File(GD.WORKING_DIR+"/autoplug-cache");
        File autoplug_backups = new File(GD.WORKING_DIR+"/autoplug-backups");
        File autoplug_logs = new File(GD.WORKING_DIR+"/autoplug-logs");
        File autoplug_backups_server = new File(GD.WORKING_DIR+"/autoplug-backups/server");
        File autoplug_backups_plugins = new File(GD.WORKING_DIR+"/autoplug-backups/plugins");
        File autoplug_backups_worlds = new File(GD.WORKING_DIR+"/autoplug-backups/worlds");

        if (!autoplug_cache.exists()) {
            System.out.println(" - Generating: " + autoplug_cache);
            autoplug_cache.mkdirs();}
        if (!autoplug_backups.exists()) {
            System.out.println(" - Generating: " + autoplug_backups);
            autoplug_backups.mkdirs();}
        if (!autoplug_logs.exists()) {
            System.out.println(" - Generating: " + autoplug_logs);
            autoplug_logs.mkdirs();}
        if (!autoplug_backups_server.exists()) {
            System.out.println(" - Generating: " + autoplug_backups_server);
            autoplug_backups_server.mkdirs();}
        if (!autoplug_backups_plugins.exists()) {
            System.out.println(" - Generating: " + autoplug_backups_plugins);
            autoplug_backups_plugins.mkdirs();}
        if (!autoplug_backups_worlds.exists()) {
            System.out.println(" - Generating: " + autoplug_backups_worlds);
            autoplug_backups_worlds.mkdirs();}
        System.out.println("All directories ok!");

        //Initialises the logger
        AutoPlugLogger.start();

        AutoPlugLogger.barrier();
        AutoPlugLogger.info("     ___       __       ___  __             ");
        AutoPlugLogger.info("    / _ |__ __/ /____  / _ \\/ /_ _____ _   ");
        AutoPlugLogger.info("   / __ / // / __/ _ \\/ ___/ / // / _ `/   ");
        AutoPlugLogger.info("  /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /");
        AutoPlugLogger.info("                                 /___/    ");
        AutoPlugLogger.info("");
        AutoPlugLogger.info("           "+GD.VERSION+"                ");
        AutoPlugLogger.info("      "+GD.COPYRIGHT+"                   ");
        AutoPlugLogger.info("         "+GD.OFFICIAL_WEBSITE+"         ");
        AutoPlugLogger.barrier();

        // Loads or creates all needed config.yml files
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.load();

        BackupConfig backupConfig = new BackupConfig();
        backupConfig.load();

        RestarterConfig restarterConfig = new RestarterConfig();
        restarterConfig.load();

        CheckConfig checkConfig = new CheckConfig();
        checkConfig.load();

        AutoPlugLogger.info(" - AutoPlug initialised!");
        AutoPlugLogger.barrier();

        if (ServerConfig.debug){
            AutoPlugLogger.debug("main","DEBUG DETAILS");
            AutoPlugLogger.debug("main","SYSTEM OS: "+ System.getProperty("os.name"));
            AutoPlugLogger.debug("main","SYSTEM VERSION: "+ System.getProperty("os.version"));
            AutoPlugLogger.debug("main","JAVA VERSION: "+ System.getProperty("java.version"));
            AutoPlugLogger.debug("main","WORKING DIR: "+ GD.WORKING_DIR);
            AutoPlugLogger.debug("main","SERVER FILE: "+ GD.SERVER_PATH);
        }

        UserInput.keyboard();

        //Start minecraft server
        Server.start();

        //This starts the updater
        new LocalListener();

        }
}
