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
import com.osiris.autoplug.client.online.Communication;
import com.osiris.autoplug.client.server.UserInput;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;

import java.io.File;

public class Main {


    public static void main(String[]args){

        System.out.println("Initialising " + GD.VERSION);

        try{
            System.out.println("Current working directory: " + GD.WORKING_DIR);
            if (System.getProperty("sun.desktop").equals("windows")){
                System.out.println("Detected windows os, disabling colors :/");
                GD.WINDOWS_OS = true;
            }
        } catch (NullPointerException e) {
            if (System.getProperty("os.name").equals("windows")){
                System.out.println("Detected windows os, disabling colors :/");
                GD.WINDOWS_OS = true;
            }
        }

        AutoPlugLogger logger = new AutoPlugLogger();

        logger.global_info("|----------------------------------------|");
        logger.global_info("     ___       __       ___  __             ");
        logger.global_info("    / _ |__ __/ /____  / _ \\/ /_ _____ _   ");
        logger.global_info("   / __ / // / __/ _ \\/ ___/ / // / _ `/   ");
        logger.global_info("  /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /");
        logger.global_info("                                 /___/    ");
        logger.global_info("|----------------------------------------|");
        logger.global_info("           "+ GD.VERSION+"                ");
        logger.global_info("      Copyright (c) 2020 Osiris Team      ");
        logger.global_info("         "+ GD.OFFICIAL_WEBSITE+"         ");
        logger.global_info("|----------------------------------------|");
        logger.global_info(" - Checking directories...");

        File autoplug_cache = new File(GD.WORKING_DIR+"/autoplug-cache");
        File autoplug_backups = new File(GD.WORKING_DIR+"/autoplug-backups");
        File autoplug_backups_server = new File(GD.WORKING_DIR+"/autoplug-backups/server");
        File autoplug_backups_plugins = new File(GD.WORKING_DIR+"/autoplug-backups/plugins");
        File autoplug_backups_worlds = new File(GD.WORKING_DIR+"/autoplug-backups/worlds");

        if (!autoplug_cache.exists()) {
            logger.global_info(" - Generating: " + autoplug_cache);
            autoplug_cache.mkdirs();}
        if (!autoplug_backups.exists()) {
            logger.global_info(" - Generating: " + autoplug_backups);
            autoplug_backups.mkdirs();}
        if (!autoplug_backups_server.exists()) {
            logger.global_info(" - Generating: " + autoplug_backups_server);
            autoplug_backups_server.mkdirs();}
        if (!autoplug_backups_plugins.exists()) {
            logger.global_info(" - Generating: " + autoplug_backups_plugins);
            autoplug_backups_plugins.mkdirs();}
        if (!autoplug_backups_worlds.exists()) {
            logger.global_info(" - Generating: " + autoplug_backups_worlds);
            autoplug_backups_worlds.mkdirs();}
        logger.global_info(" - All directories ok!");


        // Loads or creates all needed config.yml files
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.create();

        BackupConfig backupConfig = new BackupConfig();
        backupConfig.create();

        RestarterConfig restarterConfig = new RestarterConfig();
        restarterConfig.create();

        CheckConfig checkConfig = new CheckConfig();
        checkConfig.create();

        logger.global_info(" - AutoPlug initialised!");
        logger.global_info("|----------------------------------------|");

        UserInput.keyboard();
        new Communication(ServerConfig.server_key);


        }
}
