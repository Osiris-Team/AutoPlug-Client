/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;


import com.osiris.autoplug.client.configs.*;
import com.osiris.autoplug.client.console.AutoPlugConsole;
import com.osiris.autoplug.client.console.ThreadUserInput;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.network.local.ConPluginCommandReceive;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsConfig;
import com.osiris.autoplug.client.utils.UtilsJar;
import com.osiris.autoplug.client.utils.UtilsLogger;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.watcher.DirWatcher;
import com.osiris.dyml.watcher.FileEvent;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.osiris.autoplug.client.utils.GD.WORKING_DIR;

public class Main {
    //public static NonBlockingPipedInputStream PIPED_IN;
    public static ConMain CON_MAIN = new ConMain();

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
            Yaml logC = new Yaml(System.getProperty("user.dir") + "/autoplug/logger-config.yml");
            logC.load();
            YamlSection debug = logC.put("logger-config", "debug").setDefValues("false");
            YamlSection autoplug_label = logC.put("logger-config", "autoplug-label").setDefValues("AP");
            YamlSection force_ansi = logC.put("logger-config", "force-ANSI").setDefValues("false");
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
            GeneralConfig generalConfig = new GeneralConfig();
            if (generalConfig.server_start_command.asString() == null) {
                UtilsLogger uLog = new UtilsLogger();
                uLog.animatedPrintln("Setup:\n" +
                        "Hey! Welcome to AutoPlug. It seems like this is your first run.\n" +
                        "Please enter the command used to start your server below and press enter:\n" +
                        "(Example: java -jar server.jar)\n" +
                        "(Note: Also include the flags/arguments if you have any)");

                generalConfig.server_start_command.setValues(uLog.expectInput());
                generalConfig.save();

                uLog.animatedPrintln("Setup:\n" +
                        "Start your server automatically when you start AutoPlug?\n" +
                        "Enter yes/no below and press enter:");
                String autoStart = uLog.expectInput("yes", "no");
                if (autoStart.equals("yes")) {
                    generalConfig.server_auto_start.setValues("true");
                    generalConfig.save();
                } else {
                    generalConfig.server_auto_start.setValues("false");
                    generalConfig.save();
                }

                uLog.animatedPrintln("Setup:\n" +
                        "Auto-update your server?\n" +
                        "Enter the server software below\n" +
                        "or leave empty to disable and press enter:\n" +
                        "Supported Minecraft server software:\n" +
                        "(paper, waterfall, travertine, velocity, purpur, fabric)");
                String software = uLog.expectInput("", "paper", "waterfall", "travertine", "velocity", "purpur", "fabric");
                UpdaterConfig updaterConfig = new UpdaterConfig();
                if (software.isEmpty())
                    updaterConfig.server_updater.setValues("false");
                else
                    updaterConfig.server_software.setValues(software);
                updaterConfig.save();

                uLog.animatedPrintln("Setup:\n" +
                        "AutoPlug also provides a free web-panel at " + GD.OFFICIAL_WEBSITE + "\n" +
                        "that can start/stop/restart your server and show summaries of updates.\n" +
                        "If you want to use it enter the server-key below,\n" +
                        "otherwise leave it empty and press enter:\n" +
                        "(Note: Connections can be enabled/disabled in /autoplug/web-config.yml)");
                String key = uLog.expectInput();
                if (key.isEmpty()) generalConfig.server_key.setValues("NO_KEY");
                else {
                    WebConfig webConfig = new WebConfig();
                    webConfig.online_console.setValues("true");
                    webConfig.file_manager.setValues("true");
                    webConfig.save();
                    generalConfig.server_key.setValues(key);
                }
                generalConfig.save();
                if (!key.isEmpty())

                    AutoPlugConsole.executeCommand(".help");
                uLog.animatedPrintln("Setup:\n" +
                        "Above you can see a list of AutoPlug commands (command: .help).\n" +
                        "The .check command for example force-checks for updates and can\n" +
                        "be pretty useful since there are update cool-downs.\n" +
                        "AutoPlug has a few configs at /autoplug you can configure.\n" +
                        "Everything we setup before (and more) can be changed/enabled/disabled in them.\n" +
                        "This should be enough to get you started!\n" +
                        "Press enter to leave the setup:");
                uLog.expectInput();
            }

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
            AL.debug(Main.class, "SERVER FILE: " + GD.SERVER_JAR);
            AL.info("| ------------------------------------------- |");

            AL.info("Checking configurations...");
            UtilsConfig utilsConfig = new UtilsConfig();

            List<YamlSection> allModules = new ArrayList<>();

            // Loads or creates all needed configuration files
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
                if (sharedFilesConfig.enable.asBoolean()) {

                    List<File> foldersToWatch = new ArrayList<>();
                    for (String pathAsString :
                            sharedFilesConfig.copy_from.asStringList()) {
                        if (pathAsString.startsWith("./"))
                            foldersToWatch.add(FileManager.convertRelativeToAbsolutePath(pathAsString));
                        else
                            throw new Exception("Wrongly formatted or absolute path: " + pathAsString);
                    }

                    List<File> filesToSendTo = new ArrayList<>();
                    //List<String> ipsToSendTo = new ArrayList<>();
                    for (String value :
                            sharedFilesConfig.send_to.asStringList()) {
                        if (value.startsWith("./"))
                            filesToSendTo.add(FileManager.convertRelativeToAbsolutePath(value));
                        else if (value.contains("/") || value.contains("\\"))
                            filesToSendTo.add(new File(value));
                            // TODO else if (value.contains("."))
                            //    ipsToSendTo.add(value);
                        else
                            throw new Exception("Failed to determine if '" + value + "' is absolute/relative path address."); //TODO or ipv4/ipv6
                    }

                    Consumer<FileEvent> onFileChangeEvent = event -> {
                        // Determine relative path from file to server root
                        // Example: C:/Users/Server/plugins/AutoPlug.jar -> /plugins/AutoPlug.jar
                        String relPath = event.file.getAbsolutePath().replace(WORKING_DIR.getAbsolutePath(), "");
                        if (event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                            for (File receivingServerRootDir :
                                    filesToSendTo) {
                                new File(receivingServerRootDir + relPath)
                                        .delete();
                            }
                        } else if (event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_MODIFY)
                                || event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                            for (File receivingServerRootDir :
                                    filesToSendTo) {
                                try {
                                    File f = new File(receivingServerRootDir + relPath);
                                    if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
                                    if (!f.exists()) f.createNewFile();
                                    Files.copy(event.path, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                } catch (Exception e) {
                                    AL.warn(e);
                                }
                            }
                        } else
                            AL.warn("Failed to execute 'send-to' for event type '" + event.getWatchEventKind().name() + "' for file '" + event.file + "'!");
                    };

                    for (File folder :
                            foldersToWatch) {
                        DirWatcher.get(folder, true).addListeners(onFileChangeEvent);
                        AL.debug(Main.class, "Watching 'copy-from' folder and sub-folders from: " + folder);
                    }
                }
            } catch (Exception e) {
                AL.warn(e);
            }

            CON_MAIN.start();

            new ConPluginCommandReceive();

            new ThreadUserInput().start();

            if (generalConfig.server_auto_start.asBoolean())
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
