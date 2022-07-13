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
import com.osiris.autoplug.client.tasks.updater.java.TaskJavaUpdater;
import com.osiris.autoplug.client.tasks.updater.mods.TaskModsUpdater;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.autoplug.client.tasks.updater.self.TaskSelfUpdater;
import com.osiris.autoplug.client.tasks.updater.server.TaskServerUpdater;
import com.osiris.autoplug.client.ui.MainWindow;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsConfig;
import com.osiris.autoplug.client.utils.UtilsJar;
import com.osiris.autoplug.client.utils.UtilsStart;
import com.osiris.autoplug.client.utils.tasks.MyBThreadManager;
import com.osiris.autoplug.client.utils.tasks.UtilsTasks;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
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
        long now = System.currentTimeMillis();
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
            AL.start(autoplug_label.asString(),
                    debug.asBoolean(), // must be a new Yaml and not the LoggerConfig
                    new File(System.getProperty("user.dir") + "/autoplug/logs/latest.log"),
                    force_ansi.asBoolean()
            );
            AL.mirrorSystemStreams(new File(System.getProperty("user.dir") + "/autoplug/logs/console-mirror.log"),
                    new File(System.getProperty("user.dir") + "/autoplug/logs/console-mirror-err.log"));
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
            AL.info(GD.VERSION + " by " + GD.AUTHOR);
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

            //AL.info("Checking configurations...");
            now = System.currentTimeMillis();
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
                } else {
                    TARGET = Target.fromString(target);
                    if (TARGET != null) break;
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

            SharedFilesConfig sharedFilesConfig = new SharedFilesConfig();
            utilsConfig.checkForDeprecatedSections(sharedFilesConfig);
            allModules.addAll(sharedFilesConfig.getAllInEdit());

            utilsConfig.printAllModulesToDebugExceptServerKey(allModules, generalConfig.server_key.asString());
            AL.info("Checked configs, took " + (System.currentTimeMillis() - now) + "ms");

            try {
                if (sharedFilesConfig.enable.asBoolean()) {
                    now = System.currentTimeMillis();
                    new SyncFilesManager(sharedFilesConfig);
                    AL.info("Enabled sync for " + sharedFilesConfig.copy_from.getValues().size() + " directories, took " + (System.currentTimeMillis() - now) + "ms");
                }
            } catch (Exception e) {
                AL.warn(e);
            }

            try {
                if (generalConfig.autoplug_system_tray.asBoolean()) {
                    now = System.currentTimeMillis();
                    new MainWindow();
                    AL.info("Started system-tray GUI, took " + (System.currentTimeMillis() - now) + "ms");
                }
            } catch (Exception e) {
                AL.warn(e);
            }

            try {
                if (generalConfig.autoplug_start_on_boot.asBoolean())
                    new UtilsStart().enableStartOnBootIfNeeded(new UtilsJar().getThisJar());
                else new UtilsStart().disableStartOnBootIfNeeded();
            } catch (Exception e) {
                AL.warn(e);
            }

            try {
                if (updaterConfig.global_recurring_checks.asBoolean()) {
                    now = System.currentTimeMillis();
                    new Thread(() -> {
                        try {
                            while (true) {
                                long last = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                                        .parse(new SystemConfig().timestamp_last_updater_tasks.asString())
                                        .getTime();
                                long now1 = System.currentTimeMillis();
                                long msSinceLast = now1 - last;
                                long msLeft = (new UpdaterConfig().global_recurring_checks_intervall.asInt() * 3600000L) // 1h in ms
                                        - msSinceLast;
                                if (msLeft > 0) Thread.sleep(msLeft);
                                AL.info("Running tasks from recurring update-checker thread.");
                                MyBThreadManager man = new UtilsTasks().createManagerWithDisplayer();
                                TaskSelfUpdater selfUpdater = new TaskSelfUpdater("SelfUpdater", man.manager);
                                TaskJavaUpdater taskJavaUpdater = new TaskJavaUpdater("JavaUpdater", man.manager);
                                TaskServerUpdater taskServerUpdater = new TaskServerUpdater("ServerUpdater", man.manager);
                                TaskPluginsUpdater taskPluginsUpdater = new TaskPluginsUpdater("PluginsUpdater", man.manager);
                                TaskModsUpdater taskModsUpdater = new TaskModsUpdater("ModsUpdater", man.manager);
                                selfUpdater.start();
                                while (!selfUpdater.isFinished()) // Wait until the self updater finishes
                                    Thread.sleep(1000);
                                taskJavaUpdater.start();
                                taskServerUpdater.start();
                                taskPluginsUpdater.start();
                                taskModsUpdater.start();
                                while (!man.manager.isFinished())
                                    Thread.sleep(1000);
                            }
                        } catch (Exception e) {
                            AL.warn(e);
                        }
                    }).start();
                    AL.info("Started update-checker thread with " + updaterConfig.global_recurring_checks_intervall.asString() + "h intervall, took " + (System.currentTimeMillis() - now) + "ms");
                }
            } catch (Exception e) {
                AL.warn(e);
            }

            AL.info("Initialised successfully.");
            AL.info("| ------------------------------------------- |");

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
