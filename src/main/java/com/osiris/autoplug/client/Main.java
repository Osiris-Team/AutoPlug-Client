/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;


import com.osiris.autoplug.client.configs.*;
import com.osiris.autoplug.client.console.UserInput;
import com.osiris.autoplug.client.network.local.ConPluginCommandReceive;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginDownload;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsConfig;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.utils.UtilsTimeStopper;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

import static com.osiris.autoplug.client.utils.GD.WORKING_DIR;

public class Main {
    //public static NonBlockingPipedInputStream PIPED_IN;

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
                    // We are inside /autoplug/downloads
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

            String jarPath = Main.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            GD.AUTOPLUG_JAR = new File(jarPath);

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
            DreamYaml logC = new DreamYaml(System.getProperty("user.dir") + "/autoplug/logger-config.yml");
            logC.load();
            DYModule debug = logC.put("autoplug-logger-config", "debug").setDefValues("false");
            DYModule autoplug_label = logC.put("autoplug-logger-config", "autoplug-label").setDefValues("AP");
            DYModule force_ansi = logC.put("autoplug-logger-config", "force-ANSI").setDefValues("false");
            new AL().start(autoplug_label.asString(),
                    debug.asBoolean(), // must be a new DreamYaml and not the LoggerConfig
                    new File(System.getProperty("user.dir") + "/autoplug/logs"),
                    force_ansi.asBoolean()
            );
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "Running jar: " + jarPath);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("There was a critical error that prevented AutoPlug from starting!");
            return;
        }

        // IF THIS JAR WAS EXECUTED IN TESTING MODE:
        if (args != null) {
            List<String> argsList = Arrays.asList(args);
            if (argsList.contains("-test")) {
                AL.warn("Running in TEST-MODE!");
                try {
                    BetterThreadManager man = new BetterThreadManager();
                    BetterThreadDisplayer dis = new BetterThreadDisplayer(man);
                    dis.start();

                    UtilsTimeStopper timeStopper = new UtilsTimeStopper();
                    timeStopper.start();
                    TaskPluginDownload download = new TaskPluginDownload("Downloader", man,
                            "Autorank",
                            "LATEST", "https://api.spiget.org/v2/resources/3239/download", "MANUAL",
                            new File("" + System.getProperty("user.dir") + "/src/main/test/TestPlugin.jar"));
                    download.start();

                    TaskPluginDownload download1 = new TaskPluginDownload("Downloader", man,
                            "UltimateChat",
                            "LATEST", "https://api.spiget.org/v2/resources/23767/download", "MANUAL",
                            new File("" + System.getProperty("user.dir") + "/src/main/test/TestPlugin.jar"));
                    download1.start();

                    TaskPluginDownload download2 = new TaskPluginDownload("Downloader", man,
                            "ViaRewind",
                            "LATEST", "https://api.spiget.org/v2/resources/52109/download", "MANUAL",
                            new File("" + System.getProperty("user.dir") + "/src/main/test/TestPlugin.jar"));
                    download2.start();

                    TaskPluginsUpdater taskPluginsUpdater = new TaskPluginsUpdater("PluginsUpdater", man);
                    taskPluginsUpdater.start();

                    while (!download.isFinished() || !download1.isFinished() || !download2.isFinished())
                        Thread.sleep(100);
                    timeStopper.stop();
                    AL.info("Time took to finish download tasks: " + timeStopper.getFormattedSeconds() + " seconds!");

                    timeStopper.start();
                    while (!taskPluginsUpdater.isFinished())
                        Thread.sleep(100);
                    timeStopper.stop();
                    AL.info("Time took to finish update checking tasks: " + timeStopper.getFormattedSeconds() + " seconds!");
                } catch (Exception e) {
                    AL.error(e);
                }
                return; // Stop the program
            }
        }

        try {
            AL.info("| ------------------------------------------- |");
            AL.info("     ___       __       ___  __             ");
            AL.info("    / _ |__ __/ /____  / _ \\/ /_ _____ _   ");
            AL.info("   / __ / // / __/ _ \\/ ___/ / // / _ `/   ");
            AL.info("  /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /");
            AL.info("                                 /___/    ");
            AL.info("");
            AL.info("Version: " + GD.VERSION);
            AL.info("Author: " + GD.AUTHOR);
            AL.info("Web-Panel: " + GD.OFFICIAL_WEBSITE);
            AL.info("| ------------------------------------------- |");

            ConfigPreset preset = ConfigPreset.DEFAULT;
            if (!new File(GD.WORKING_DIR + "/autoplug/general-config.yml").exists()) {
                int a = 0;
                while (true) {
                    AL.info("Thank you for installing AutoPlug!");
                    AL.info("It seems like this is your first run,");
                    AL.info("please select a configuration preset:");
                    AL.info("1: The 'fast preset' makes sure that all recommended features");
                    AL.info("are enabled and thus saves you a lot of time configuring AutoPlug.");
                    AL.info("2: The 'default preset' is for sceptics and a bunch of");
                    AL.info("features need to be enabled manually. Have fun configuring!");
                    AL.info("Insert your desired preset (1 or 2) below and press enter:");
                    Scanner scanner = new Scanner(System.in);
                    a = scanner.nextInt();
                    if (a == 1) {
                        preset = ConfigPreset.FAST;
                        break;
                    } else if (a == 2) {
                        preset = ConfigPreset.DEFAULT;
                        break;
                    }
                }
            }

            AL.info("Loading configurations...");

            List<DYModule> allModules = new ArrayList<>();

            // Loads or creates all needed configuration files
            GeneralConfig generalConfig = new GeneralConfig();
            new UtilsConfig().setCommentsOfNotUsedOldDYModules(generalConfig.getAllInEdit(), generalConfig.getAllLoaded());
            allModules.addAll(generalConfig.getAllInEdit());

            LoggerConfig loggerConfig = new LoggerConfig();
            new UtilsConfig().setCommentsOfNotUsedOldDYModules(loggerConfig.getAllInEdit(), loggerConfig.getAllLoaded());
            allModules.addAll(loggerConfig.getAllInEdit());

            WebConfig webConfig = new WebConfig(preset);
            new UtilsConfig().setCommentsOfNotUsedOldDYModules(webConfig.getAllInEdit(), webConfig.getAllLoaded());
            allModules.addAll(webConfig.getAllInEdit());

            //PluginsConfig pluginsConfig = new PluginsConfig(); // Gets loaded anyway before the plugin updater starts
            //allModules.addAll(pluginsConfig.getAllInEdit()); // Do not do this because its A LOT of unneeded log spam

            BackupConfig backupConfig = new BackupConfig(preset);
            new UtilsConfig().setCommentsOfNotUsedOldDYModules(backupConfig.getAllInEdit(), backupConfig.getAllLoaded());
            allModules.addAll(backupConfig.getAllInEdit());

            RestarterConfig restarterConfig = new RestarterConfig(preset);
            new UtilsConfig().setCommentsOfNotUsedOldDYModules(restarterConfig.getAllInEdit(), restarterConfig.getAllLoaded());
            allModules.addAll(restarterConfig.getAllInEdit());

            UpdaterConfig updaterConfig = new UpdaterConfig(preset);
            new UtilsConfig().setCommentsOfNotUsedOldDYModules(updaterConfig.getAllInEdit(), updaterConfig.getAllLoaded());
            allModules.addAll(updaterConfig.getAllInEdit());

            TasksConfig tasksConfig = new TasksConfig();
            new UtilsConfig().setCommentsOfNotUsedOldDYModules(tasksConfig.getAllInEdit(), tasksConfig.getAllLoaded());
            allModules.addAll(tasksConfig.getAllInEdit());

            new UtilsConfig().printAllModulesToDebug(allModules);
            AL.info("Configurations loaded.");

            AL.debug(Main.class, " ");
            AL.debug(Main.class, "DEBUG DETAILS:");
            AL.debug(Main.class, "SYSTEM OS: " + System.getProperty("os.name"));
            AL.debug(Main.class, "SYSTEM VERSION: " + System.getProperty("os.version"));
            AL.debug(Main.class, "JAVA VERSION: " + System.getProperty("java.version"));
            AL.debug(Main.class, "JAVA VENDOR: " + System.getProperty("java.vendor") + " " + System.getProperty("java.vendor.url"));
            AL.debug(Main.class, "WORKING DIR: " + WORKING_DIR);
            AL.debug(Main.class, "SERVER FILE: " + GD.SERVER_JAR);

            AL.info("Initialised successfully.");
            AL.info("| ------------------------------------------- |");

            String key = generalConfig.server_key.asString();
            if (key == null || key.isEmpty() || key.equals("INSERT_KEY_HERE")) {
                AL.info("No Server-Key found at " + generalConfig.server_key.getKeys().toString() + ".");
                AL.info("To get a Server-Key for this server, register yourself at");
                AL.info(GD.OFFICIAL_WEBSITE + " and add this server.");
                AL.info("Insert your Server-Key below and press enter:");
                Scanner scanner = new Scanner(System.in);
                generalConfig.server_key.setValues(scanner.nextLine());
                generalConfig.save();
            }

            ConMain conMain = new ConMain();
            conMain.start();

            new ConPluginCommandReceive();

            UserInput.keyboard();

            if (generalConfig.server_auto_start.asBoolean())
                Server.start();

            // We have to keep this main Thread running.
            // If we don't, the NonBlockingPipedInputStream stops working
            // and thus no information will be sent to the online console, when the user is online.
            while (true)
                Thread.sleep(1000);

        } catch (Exception e) {
            AL.error(e.getMessage(), e);
        }
    }

}
