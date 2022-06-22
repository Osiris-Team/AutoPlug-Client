/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;


import com.osiris.autoplug.client.configs.*;
import com.osiris.autoplug.client.console.ThreadUserInput;
import com.osiris.autoplug.client.managers.SyncFilesManager;
import com.osiris.autoplug.client.network.local.ConPluginCommandReceive;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.ui.MainWindow;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsConfig;
import com.osiris.autoplug.client.utils.UtilsJar;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.osiris.autoplug.client.utils.GD.WORKING_DIR;

public class Main {
    //public static NonBlockingPipedInputStream PIPED_IN;
    public static ConMain CON_MAIN = new ConMain();
    public static Target TARGET = null;

    public static void main(String[] args) {
        // Check various things to ensure an fully functioning application.
        // If one of these checks fails this application is stopped.
        try {
            System.out.println();
            System.out.println("Initialising " + GD.VERSION);
            // SELF-UPDATER: Are we in the downloads directory? If yes, it means that this jar is an update and we need to install it.
            try {
                File curDir = new File(System.getProperty("user.dir"));
                if (curDir.getName().equals("downloads")) {
                    // We are inside ./autoplug/downloads
                    // but want to go to server root dir at ./
                    new SelfInstaller().installUpdateAndStartIt(curDir.getParentFile().getParentFile());
                    return;
                }
            } catch (Exception e) {
                // This is a critical error and stops the application.
                File selfUpdaterLogFile = null;
                if (WORKING_DIR.getName().equals("downloads"))
                    selfUpdaterLogFile = new File(WORKING_DIR.getParentFile().getParentFile() + "/A0-CRITICAL-SELF-UPDATER-ERROR.log");
                else
                    selfUpdaterLogFile = new File(WORKING_DIR + "/A0-CRITICAL-SELF-UPDATER-ERROR.log");
                Date date = new Date();
                try (PrintWriter bw = new PrintWriter(new FileWriter(selfUpdaterLogFile, true))) {
                    bw.println();
                    bw.println(e.getMessage());
                    for (StackTraceElement el :
                            e.getStackTrace()) {
                        bw.println(date + " | " + el.toString());
                    }
                }
                e.printStackTrace();
                System.err.println("AutoPlug had to exit due to a critical Self-Updater error.");
                System.err.println("The error log has been saved to: " + selfUpdaterLogFile.getAbsolutePath());
                return;
            }

            SystemChecker system = new SystemChecker();
            system.checkReadWritePermissions();
            system.checkInternetAccess();
            system.addShutDownHook();

            // Set default SysOut to TeeOutput, for the OnlineConsole
            AnsiConsole.systemInstall(); // This must happen before the stuff below.
            // Else the pipedOut won't display ansi. Idk why though...
            //PIPED_IN = new NonBlockingPipedInputStream();
            //OutputStream pipedOut = new PipedOutputStream(PIPED_IN);
            //MyTeeOutputStream teeOut = new MyTeeOutputStream(TERMINAL.output(), pipedOut);
            //PrintStream newOut = new PrintStream(teeOut);
            //System.setOut(newOut); // This causes
            // the standard System.out stream to be mirrored to pipedOut, which then can get
            // read by PIPED_IN. This ensures, that the original System.out is not touched.
            //PIPED_IN.actionsOnWriteLineEvent.add(line -> AL.debug(Main.class, line)); // For debugging

            // Start the logger
            Yaml logC = new Yaml(System.getProperty("user.dir") + "/autoplug/logger.yml");
            logC.load();
            YamlSection debug = logC.put("logger", "debug").setDefValues("false");
            YamlSection autoplug_label = logC.put("logger", "autoplug-label").setDefValues("AP");
            YamlSection force_ansi = logC.put("logger", "force-ANSI").setDefValues("false");
            new AL().start(autoplug_label.asString(),
                    debug.asBoolean(), // must be a new Yaml and not the LoggerConfig
                    new File(System.getProperty("user.dir") + "/autoplug/logs"),
                    force_ansi.asBoolean()
            );
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "Running autoplug from: " + new UtilsJar().getThisJar());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("There was a critical error that prevented AutoPlug from starting!");
            return;
        }

        try {
            AL.info("| ------------------------------------------- |");
            AL.info("     ___       __       ___  __             ");
            AL.info("    / _ |__ __/ /____  / _ \\/ /_ _____ _   ");
            AL.info("   / __ / // / __/ _ \\/ ___/ / // / _ `/   ");
            AL.info("  /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /");
            AL.info("                                 /___/    ");
            AL.info("Version: " + GD.VERSION);
            AL.info("Author: " + GD.AUTHOR);
            AL.info("Web-Panel: " + GD.OFFICIAL_WEBSITE);
            AL.debug(Main.class, " ");
            AL.debug(Main.class, "DEBUG DETAILS:");
            AL.debug(Main.class, "SYSTEM OS: " + System.getProperty("os.name"));
            AL.debug(Main.class, "SYSTEM OS ARCH: " + System.getProperty("os.arch"));
            AL.debug(Main.class, "SYSTEM VERSION: " + System.getProperty("os.version"));
            AL.debug(Main.class, "JAVA VERSION: " + System.getProperty("java.version"));
            AL.debug(Main.class, "JAVA VENDOR: " + System.getProperty("java.vendor") + " " + System.getProperty("java.vendor.url"));
            AL.debug(Main.class, "WORKING DIR: " + WORKING_DIR);
            AL.info("| ------------------------------------------- |");
            Server.getServerExecutable(); // Make sure this is called here first and not in a task later
            // to avoid infinite initialising

            AL.info("Checking configurations...");
            UtilsConfig utilsConfig = new UtilsConfig();
            utilsConfig.convertToNewNames();

            List<YamlSection> allModules = new ArrayList<>();

            // Loads or creates all needed configuration files
            GeneralConfig generalConfig = new GeneralConfig();
            String target = generalConfig.autoplug_target_software.asString();
            while (true) {
                if (target == null) {
                    for (String comment : generalConfig.autoplug_target_software.getComments()) {
                        AL.info(comment);
                    }
                    AL.info("Please enter a valid option and press enter:");
                    target = new Scanner(System.in).nextLine();
                    generalConfig.autoplug_target_software.setValues(target);
                    generalConfig.save();
                } else if (target.equals("MINECRAFT_CLIENT")) {
                    TARGET = Target.MINECRAFT_CLIENT;
                    break;
                } else if (target.equals("MINECRAFT_SERVER")) {
                    TARGET = Target.MINECRAFT_SERVER;
                    break;
                } else if (target.equals("MINDUSTRY_SERVER")) {
                    TARGET = Target.MINDUSTRY_SERVER;
                    break;
                } else if (target.equals("MINDUSTRY_CLIENT")) {
                    TARGET = Target.MINDUSTRY_CLIENT;
                    break;
                } else if (target.equals("OTHER")) {
                    TARGET = Target.OTHER;
                    break;
                } else {
                    for (String comment : generalConfig.autoplug_target_software.getComments()) {
                        AL.info(comment);
                    }
                    AL.info("The selected target software '" + target + "' is not a valid option.");
                    AL.info("Please enter a valid option and press enter:");
                    target = new Scanner(System.in).nextLine();
                    generalConfig.autoplug_target_software.setValues(target);
                    generalConfig.save();
                }
            }
            utilsConfig.checkForDeprecatedSections(generalConfig);
            allModules.addAll(generalConfig.getAllInEdit());

            LoggerConfig loggerConfig = new LoggerConfig();
            utilsConfig.checkForDeprecatedSections(loggerConfig);
            allModules.addAll(loggerConfig.getAllInEdit());
            // Extra debug options
            if (loggerConfig.debug.asBoolean()) {
                AL.info("Note that debug mode is enabled.");
                Logger.getLogger("com.gargoylesoftware").setLevel(Level.ALL);
                Logger.getLogger("org.quartz.impl.StdSchedulerFactory").setLevel(Level.ALL);
                Logger.getLogger("org.quartz.core.SchedulerSignalerImpl").setLevel(Level.ALL);
                Logger.getLogger("org.jline").setLevel(Level.ALL);
            } else {
                Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
                Logger.getLogger("org.quartz.impl.StdSchedulerFactory").setLevel(Level.OFF);
                Logger.getLogger("org.quartz.core.SchedulerSignalerImpl").setLevel(Level.OFF);
            }

            WebConfig webConfig = new WebConfig();
            utilsConfig.checkForDeprecatedSections(webConfig);
            allModules.addAll(webConfig.getAllInEdit());

            //PluginsConfig pluginsConfig = new PluginsConfig(); // Gets loaded anyway before the plugin updater starts
            //allModules.addAll(pluginsConfig.getAllInEdit()); // Do not do this because its A LOT of unneeded log spam

            BackupConfig backupConfig = new BackupConfig();
            utilsConfig.checkForDeprecatedSections(backupConfig);
            allModules.addAll(backupConfig.getAllInEdit());

            RestarterConfig restarterConfig = new RestarterConfig();
            utilsConfig.checkForDeprecatedSections(restarterConfig);
            allModules.addAll(restarterConfig.getAllInEdit());

            UpdaterConfig updaterConfig = new UpdaterConfig();
            utilsConfig.checkForDeprecatedSections(updaterConfig);
            allModules.addAll(updaterConfig.getAllInEdit());

            TasksConfig tasksConfig = new TasksConfig();
            utilsConfig.checkForDeprecatedSections(tasksConfig);
            allModules.addAll(tasksConfig.getAllInEdit());

            SharedFilesConfig sharedFilesConfig = new SharedFilesConfig();
            utilsConfig.checkForDeprecatedSections(sharedFilesConfig);
            allModules.addAll(sharedFilesConfig.getAllInEdit());

            utilsConfig.printAllModulesToDebugExceptServerKey(allModules, generalConfig.server_key.asString());
            AL.info("Configurations checked.");
            AL.info("Initialised successfully.");
            AL.info("| ------------------------------------------- |");


            try {
                if (sharedFilesConfig.enable.asBoolean()) new SyncFilesManager(sharedFilesConfig);
            } catch (Exception e) {
                AL.warn(e);
            }

            try {
                if (generalConfig.autoplug_system_tray.asBoolean()) new MainWindow();
            } catch (Exception e) {
                AL.warn(e);
            }

            CON_MAIN.start();

            if (TARGET != Target.MINECRAFT_CLIENT)
                new ConPluginCommandReceive();

            new ThreadUserInput().start();

            if (TARGET != Target.MINECRAFT_CLIENT && generalConfig.server_auto_start.asBoolean())
                Server.start();

            // We have to keep this main Thread running.
            // If we don't, the NonBlockingPipedInputStream stops working
            // and thus no information will be sent to the online console, when the user is online.
            //while (true)
            //    Thread.sleep(1000);

        } catch (Exception e) {
            AL.error(e.getMessage(), e);
        }
    }

}
