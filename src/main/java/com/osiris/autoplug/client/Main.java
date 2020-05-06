/*
 *  Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;


import com.osiris.autoplug.client.mcserver.MCServer;
import com.osiris.autoplug.client.utils.AutoPlugLogger;

import java.io.File;

public class Main {


    public static void main(String[]args){
        System.out.println("Powered by AutoPlug - " + GLOBALDATA.COPYRIGHT);

        if (System.getProperty("sun.desktop").equals("windows")){
            System.out.println("Detected windows os, disabling colors :/");
            GLOBALDATA.setWindowsOs(true);
        }

        AutoPlugLogger logger = new AutoPlugLogger();

        logger.global_info(" Initialising system...");



        String working_dir = System.getProperty("user.dir");
        File autoplug_cache = new File(working_dir+"\\autoplug-cache");
        File autoplug_backups = new File(working_dir+"\\autoplug-backups");
        File autoplug_backups_server = new File(working_dir+"\\autoplug-backups\\server");
        File autoplug_backups_plugins = new File(working_dir+"\\autoplug-backups\\plugins");
        if (!autoplug_cache.exists()) {autoplug_cache.mkdirs();}
        if (!autoplug_backups.exists()) {autoplug_backups.mkdirs();}
        if (!autoplug_backups_server.exists()) {autoplug_backups_server.mkdirs();}
        if (!autoplug_backups_plugins.exists()) {autoplug_backups_plugins.mkdirs();}

        //System.getProperties().list(System.out);


        //Checks if settings file exists and creates one
        Settings settings = new Settings();
        settings.create();

        ClientListener clientListener = new ClientListener();
        clientListener.start();


        }
}
