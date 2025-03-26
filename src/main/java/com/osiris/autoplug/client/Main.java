/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.fusesource.jansi.AnsiConsole;

import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.ModsConfig;
import com.osiris.autoplug.client.configs.PluginsConfig;
import com.osiris.autoplug.client.configs.RestarterConfig;
import com.osiris.autoplug.client.configs.SSHConfig;
import com.osiris.autoplug.client.configs.SharedFilesConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.console.Commands;
import com.osiris.autoplug.client.console.ThreadUserInput;
import com.osiris.autoplug.client.managers.SyncFilesManager;
import com.osiris.autoplug.client.network.local.ConPluginCommandReceive;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.tasks.SSHManager;
import com.osiris.autoplug.client.ui.MainWindow;
import com.osiris.autoplug.client.utils.GD;
import static com.osiris.autoplug.client.utils.GD.WORKING_DIR;
import com.osiris.autoplug.client.utils.UpdateCheckerThread;
import com.osiris.autoplug.client.utils.UtilsConfig;
import com.osiris.autoplug.client.utils.UtilsJar;
import com.osiris.autoplug.client.utils.UtilsLists;
import com.osiris.autoplug.client.utils.UtilsNative;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.MessageFormatter;
import com.osiris.jprocesses2.ProcessUtils;

public class Main {
    // Do not init fields directly here, but instead in main() after logger was initialised
    //public static NonBlockingPipedInputStream PIPED_IN;
    public static ConMain CON;
    public static SSHManager sshManager;

    public static UpdateCheckerThread UPDATE_CHECKER_THREAD = null;

    /**
     * @param _args arguments separated by spaces. <br>
     *              - AutoPlug console commands <br>
     *              - skipSystemCheck: skips the first system check <br>
     *              - test: enables test mode <br>
     */
    public static void main(String[] _args) {

        List<String> args = new ArrayList<>();
        if (_args != null)
            Collections.addAll(args, _args);
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

            if (!args.contains("skipSystemCheck")) {
                SystemChecker system = new SystemChecker();
                system.checkReadWritePermissions();
                system.checkInternetAccess();
                system.addShutDownHook();
            }

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
            MessageFormatter.dtf_small = MessageFormatter.dtf_long;
            AL.start(autoplug_label.asString(),
                    debug.asBoolean(), // must be a new Yaml and not the LoggerConfig
                    GD.AP_LATEST_LOG,
                    true,
                    force_ansi.asBoolean()
            );
            AL.mirrorSystemStreams(GD.FILE_OUT, GD.FILE_ERR_OUT);

            try {
                for (String arg : args) {
                    if (Objects.equals(arg, "test")) {
                        GD.IS_TEST_MODE = true;
                        GD.OFFICIAL_WEBSITE = "http://localhost/";
                        AL.warn("RUNNING IN TEST-MODE!");
                        break;
                    }
                }
            } catch (Exception e) {
                AL.warn(e, "Failed to determine test-mode.");
            }
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            AL.debug(Main.class, "!!!IMPORTANT!!! -> THIS LOG-FILE CONTAINS SENSITIVE INFORMATION <- !!!IMPORTANT!!!");
            String command = "";
            try {
                command = new ProcessUtils().getThis().command;
            } catch (Throwable e) {
                command = e.getMessage();
            }
            AL.debug(Main.class, "START COMMAND: " + command);
            AL.debug(Main.class, "JAR: " + new UtilsJar().getThisJar());
            AL.debug(Main.class, "ARGS: " + (args != null ? new UtilsLists().toString(args) : ""));
            AL.debug(Main.class, "SYSTEM OS: " + System.getProperty("os.name"));
            AL.debug(Main.class, "SYSTEM OS ARCH: " + System.getProperty("os.arch"));
            AL.debug(Main.class, "SYSTEM VERSION: " + System.getProperty("os.version"));
            AL.debug(Main.class, "JAVA VERSION: " + System.getProperty("java.version"));
            AL.debug(Main.class, "JAVA VENDOR: " + System.getProperty("java.vendor") + " " + System.getProperty("java.vendor.url"));
            AL.debug(Main.class, "JAVA DIR: " + System.getProperty("java.home"));
            AL.debug(Main.class, "WORKING DIR: " + WORKING_DIR);
            AL.debug(Main.class, "TEST-MODE: " + GD.IS_TEST_MODE);
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
            GD.determineTarget(generalConfig);
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

            SSHConfig sshConfig = new SSHConfig();
            utilsConfig.checkForDeprecatedSections(sshConfig);
            allModules.addAll(sshConfig.getAllInEdit());

            PluginsConfig pluginsConfig = new PluginsConfig();
            ModsConfig modsConfig = new ModsConfig();

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
                    new UtilsNative().enableStartOnBootIfNeeded(new UtilsJar().getThisJar());
                else new UtilsNative().disableStartOnBootIfNeeded();
            } catch (Exception e) {
                AL.warn(e);
            }

            try {
                if (updaterConfig.global_recurring_checks.asBoolean()) {
                    now = System.currentTimeMillis();
                    UPDATE_CHECKER_THREAD = new UpdateCheckerThread();
                    UPDATE_CHECKER_THREAD.start();
                    AL.info("Started update-checker thread with " + updaterConfig.global_recurring_checks_intervall.asString() + "h intervall, took " + (System.currentTimeMillis() - now) + "ms");
                }
            } catch (Exception e) {
                AL.warn(e);
            }
            
            SSHManager.start(false);

            CON = new ConMain();
            CON.open();

            AL.info("Initialised successfully.");
            AL.info("| ------------------------------------------- |");
            AL.info("Enter .help for a list of all commands.");

            if (GD.TARGET != Target.MINECRAFT_CLIENT)
                new ConPluginCommandReceive();

            new ThreadUserInput().start();

            if (GD.TARGET != Target.MINECRAFT_CLIENT && generalConfig.server_auto_start.asBoolean())
                Server.start();

            // Execute arguments as commands if existing
            String argsString = "";
            for (String arg : args) {
                argsString += arg + " ";
            }
            if (argsString.contains(".")) {
                String[] commands = argsString.split("\\."); // Split by dots
                for (String c : commands) {
                    Commands.execute("." + c);
                }
            }

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
