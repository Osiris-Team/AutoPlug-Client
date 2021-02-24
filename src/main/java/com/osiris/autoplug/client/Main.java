/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;


import com.osiris.autoplug.client.configs.*;
import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.network.online.MainConnection;
import com.osiris.autoplug.client.server.UserInput;
import com.osiris.autoplug.client.utils.ConfigUtils;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        try{
            System.out.println("Initialising "+ GD.VERSION);

            GeneralCheck gc = new GeneralCheck();
            gc.checkFilePermission();
            gc.checkInternetAccess();
            gc.addShutDownHook();

            FileChecker fc = new FileChecker();
            fc.check();

            // Initialises the logging system
            new AL().start("AutoPlug",
                    new DreamYaml(System.getProperty("user.dir")+"/autoplug-logger-config.yml"), // must be a new DreamYaml and not the LoggerConfig
                    new File(System.getProperty("user.dir")+"/autoplug-logs")
            );
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");

            AL.info("| ------------------------------------------- |");
            AL.info("     ___       __       ___  __             ");
            AL.info("    / _ |__ __/ /____  / _ \\/ /_ _____ _   ");
            AL.info("   / __ / // / __/ _ \\/ ___/ / // / _ `/   ");
            AL.info("  /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /");
            AL.info("                                 /___/    ");
            AL.info("");
            AL.info("           "+GD.VERSION+"                ");
            AL.info("      "+GD.COPYRIGHT+"                   ");
            AL.info("         "+GD.OFFICIAL_WEBSITE+"         ");
            AL.info("| ------------------------------------------- |");
            AL.info("Loading configurations...");

            List<DYModule> allModules = new ArrayList<>();

            // Loads or creates all needed configuration files
            GeneralConfig generalConfig = new GeneralConfig();
            allModules.addAll(generalConfig.getAllAdded());

            LoggerConfig loggerConfig = new LoggerConfig();
            allModules.addAll(loggerConfig.getAllAdded());

            //PluginsConfig pluginsConfig = new PluginsConfig(); // Gets loaded anyway before the plugin updater starts
            //allModules.addAll(pluginsConfig.getAllAdded()); // Do not do this because its A LOT of unneeded log spam

            BackupConfig backupConfig = new BackupConfig();
            allModules.addAll(backupConfig.getAllAdded());

            RestarterConfig restarterConfig = new RestarterConfig();
            allModules.addAll(restarterConfig.getAllAdded());

            UpdaterConfig updaterConfig = new UpdaterConfig();
            allModules.addAll(updaterConfig.getAllAdded());

            TasksConfig tasksConfig = new TasksConfig();
            allModules.addAll(tasksConfig.getAllAdded());

            new ConfigUtils().printAllModulesToDebug(allModules);
            AL.info("Configurations loaded.");

            AL.debug(Main.class," ");
            AL.debug(Main.class,"DEBUG DETAILS:");
            AL.debug(Main.class,"SYSTEM OS: "+ System.getProperty("os.name"));
            AL.debug(Main.class,"SYSTEM VERSION: "+ System.getProperty("os.version"));
            AL.debug(Main.class,"JAVA VERSION: "+ System.getProperty("java.version"));
            AL.debug(Main.class,"JAVA VENDOR: "+ System.getProperty("java.vendor")+" "+System.getProperty("java.vendor.url"));
            AL.debug(Main.class,"WORKING DIR: "+ GD.WORKING_DIR);
            AL.debug(Main.class,"SERVER FILE: "+ GD.SERVER_PATH);

            AL.info("AutoPlug initialised!");
            AL.info("| ------------------------------------------- |");

            String key = generalConfig.server_key.asString();
            if (key==null || key.isEmpty() || key.equals("INSERT_KEY_HERE")){
                AL.info("Thank you for installing AutoPlug!");
                AL.info("It seems like this is your first run and you haven't set your server key yet.");
                AL.info("For that, register yourself at "+GD.OFFICIAL_WEBSITE+" and add a new server.");
                AL.info("Enter the key below:");
                Scanner scanner = new Scanner(System.in);
                generalConfig.server_key.setValue(scanner.nextLine());
                generalConfig.save();
            }

            MainConnection mainConnection = new MainConnection();
            mainConnection.start();

            UserInput.keyboard();
            Server.start();

        } catch (Exception e) {
            AL.error(e.getMessage(), e);
        }

    }
}
