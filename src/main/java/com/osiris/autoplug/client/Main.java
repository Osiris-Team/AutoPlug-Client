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
import com.osiris.autoplug.client.network.online.MainConnection;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginDownload;
import com.osiris.autoplug.client.utils.ConfigUtils;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    //public static NonBlockingPipedInputStream PIPED_IN;

    public static void main(String[] args) {

        if (args != null) {
            List<String> argsList = Arrays.asList(args);
            if (argsList.contains("-test")) { // TESTING STUFF BELOW:
                new AL().start();
                try {
                    BetterThreadManager man = new BetterThreadManager();
                    BetterThreadDisplayer dis = new BetterThreadDisplayer(man);
                    dis.start();

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


                    while (!download.isFinished() || !download1.isFinished() || !download2.isFinished())
                        Thread.sleep(500);
                } catch (Exception e) {
                    AL.error(e);
                }
                return; // Stop the program
            }
        }

        // Check various things to ensure an fully functioning application.
        // If one of these checks fails this application is stopped.
        try {
            System.out.println();
            System.out.println("Initialising " + GD.VERSION);
            SystemChecker system = new SystemChecker();
            system.checkReadWritePermissions();
            system.checkInternetAccess();
            system.checkMissingFiles();
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
            DreamYaml logC = new DreamYaml(System.getProperty("user.dir") + "/autoplug-logger-config.yml");
            DYModule debug = logC.put("autoplug-logger-config", "debug").setDefValues("false");
            DYModule autoplug_label = logC.put("autoplug-logger-config", "autoplug-label").setDefValues("AP");
            DYModule force_ansi = logC.put("autoplug-logger-config", "force-ANSI").setDefValues("false");
            new AL().start(autoplug_label.asString(),
                    debug.asBoolean(), // must be a new DreamYaml and not the LoggerConfig
                    new File(System.getProperty("user.dir") + "/autoplug-logs"),
                    force_ansi.asBoolean()
            );


        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("There was a critical error that prevented AutoPlug from starting!");
            return;
        }

        // SELF-UPDATER: Are we in the downloads directory? If yes, it means that this jar is an update and we need to install it.
        try {
            File curDir = new File(System.getProperty("user.dir"));
            if (curDir.getName().equals("autoplug-downloads")) {
                new SelfInstaller().installUpdateAndStartIt(curDir.getParentFile());
                return;
            }
        } catch (Exception e) {
            AL.warn(e, "Update installation failed!");
        }

        try {
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
            AL.info("Version: " + GD.VERSION);
            AL.info("Author: " + GD.AUTHOR);
            AL.info("Web-Panel: " + GD.OFFICIAL_WEBSITE);
            AL.info("| ------------------------------------------- |");
            AL.info("Loading configurations...");

            List<DYModule> allModules = new ArrayList<>();

            // Loads or creates all needed configuration files
            GeneralConfig generalConfig = new GeneralConfig();
            allModules.addAll(generalConfig.getAllInEdit());

            LoggerConfig loggerConfig = new LoggerConfig();
            allModules.addAll(loggerConfig.getAllInEdit());

            WebConfig webConfig = new WebConfig();
            allModules.addAll(webConfig.getAllInEdit());

            //PluginsConfig pluginsConfig = new PluginsConfig(); // Gets loaded anyway before the plugin updater starts
            //allModules.addAll(pluginsConfig.getAllInEdit()); // Do not do this because its A LOT of unneeded log spam

            BackupConfig backupConfig = new BackupConfig();
            allModules.addAll(backupConfig.getAllInEdit());

            RestarterConfig restarterConfig = new RestarterConfig();
            allModules.addAll(restarterConfig.getAllInEdit());

            UpdaterConfig updaterConfig = new UpdaterConfig();
            allModules.addAll(updaterConfig.getAllInEdit());

            TasksConfig tasksConfig = new TasksConfig();
            allModules.addAll(tasksConfig.getAllInEdit());

            new ConfigUtils().printAllModulesToDebug(allModules);
            AL.info("Configurations loaded.");

            AL.debug(Main.class, " ");
            AL.debug(Main.class, "DEBUG DETAILS:");
            AL.debug(Main.class, "SYSTEM OS: " + System.getProperty("os.name"));
            AL.debug(Main.class, "SYSTEM VERSION: " + System.getProperty("os.version"));
            AL.debug(Main.class, "JAVA VERSION: " + System.getProperty("java.version"));
            AL.debug(Main.class, "JAVA VENDOR: " + System.getProperty("java.vendor") + " " + System.getProperty("java.vendor.url"));
            AL.debug(Main.class, "WORKING DIR: " + GD.WORKING_DIR);
            AL.debug(Main.class, "SERVER FILE: " + GD.SERVER_PATH);

            AL.info("| ------------------------------------------- |");

            String key = generalConfig.server_key.asString();
            if (key == null || key.isEmpty() || key.equals("INSERT_KEY_HERE")) {
                AL.info("Thank you for installing AutoPlug!");
                AL.info("It seems like this is your first run and you haven't set your server key yet.");
                AL.info("For that, register yourself at " + GD.OFFICIAL_WEBSITE + " and add a new server.");
                AL.info("Enter the key below:");
                Scanner scanner = new Scanner(System.in);
                generalConfig.server_key.setValues(scanner.nextLine());
                generalConfig.save();
            }

            MainConnection mainConnection = new MainConnection();
            mainConnection.start();

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
