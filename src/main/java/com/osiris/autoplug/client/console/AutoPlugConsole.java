/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.console;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.network.online.connections.ConSendPrivateDetails;
import com.osiris.autoplug.client.network.online.connections.ConSendPublicDetails;
import com.osiris.autoplug.client.tasks.BeforeServerStartupTasks;
import com.osiris.autoplug.client.tasks.backup.TaskBackup;
import com.osiris.autoplug.client.tasks.updater.java.TaskJavaUpdater;
import com.osiris.autoplug.client.tasks.updater.mods.TaskModsUpdater;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.autoplug.client.tasks.updater.self.TaskSelfUpdater;
import com.osiris.autoplug.client.tasks.updater.server.TaskServerUpdater;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.tasks.MyBThreadManager;
import com.osiris.autoplug.client.utils.tasks.UtilsTasks;
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Listens for input started with .
 * List the server with .help
 */
public final class AutoPlugConsole {

    /**
     * Returns true if the provided String is a AutoPlug command.
     *
     * @param command An AutoPlug command like .help for example.
     */
    public static boolean executeCommand(@NotNull String command) {

        String first = "";
        try {
            Objects.requireNonNull(command);
            command = command.trim();
            first = Character.toString(command.charAt(0));
        } catch (Exception e) {
            AL.warn("Failed to read command '" + command + "'! Enter .help for all available commands!", e);
            return false;
        }

        if (first.equals(".")) {
            try {
                if (command.equals(".help") || command.equals(".h")) {
                    AL.info("");
                    AL.info(".help | Prints out this (Shortcut: .h)");
                    AL.info(".run tasks | Runs the 'before server startup tasks' without starting the server (.rt)");
                    AL.info(".con info | Shows details about AutoPlugs network connections (.ci)");
                    AL.info(".con reload | Closes and reconnects all connections (.cr)");
                    AL.info(".backup | Ignores cool-down and does an backup (.b)");
                    AL.info("");
                    AL.info("Server related commands:");
                    AL.info(".start | Starts the server (.s)");
                    AL.info(".restart | Restarts the server (.r)");
                    AL.info(".stop | Stops and saves the server (.st)");
                    AL.info(".stop both | Stops, saves your server and closes AutoPlug safely (.stb)");
                    AL.info(".kill | Kills the server without saving (.k)");
                    AL.info(".kill both | Kills the server without saving and closes AutoPlug (.kb)");
                    AL.info(".server info | Shows details about this server (.si)");
                    AL.info("");
                    AL.info("Update checking commands: (note that all the checks below");
                    AL.info("ignore the cool-down and behave according to the selected profile)");
                    AL.info(".check | Checks for AutoPlug updates (.c)");
                    AL.info(".check java | Checks for Java updates (.cj)");
                    AL.info(".check server | Checks for server updates (.cs)");
                    AL.info(".check plugins | Checks for plugins updates (.cp)");
                    AL.info(".check mods | Checks for mods updates (.cm)");
                    AL.info("");
                    return true;
                } else if (command.equals(".start") || command.equals(".s")) {
                    Server.start();
                    return true;
                } else if (command.equals(".restart") || command.equals(".r")) {
                    Server.restart();
                    return true;
                } else if (command.equals(".stop") || command.equals(".st")) {
                    Server.stop();
                    return true;
                } else if (command.equals(".stop both") || command.equals(".stb")) {
                    // All the stuff that needs to be done before shutdown is done by the ShutdownHook.
                    // See SystemChecker.addShutdownHook() for details.
                    System.exit(0);
                    return true;
                } else if (command.equals(".kill") || command.equals(".k")) {
                    Server.kill();
                    return true;
                } else if (command.equals(".kill both") || command.equals(".kb")) {
                    Server.kill();
                    AL.info("Killing AutoPlug-Client and Server! Ahhhh!");
                    AL.info("Achievement unlocked: Double kill!");
                    Thread.sleep(3000);
                    System.exit(0);
                    return true;
                } else if (command.equals(".run tasks") || command.equals(".rt")) {
                    new BeforeServerStartupTasks();
                    return true;
                } else if (command.equals(".con info") || command.equals(".ci")) {
                    AL.info("Main connection: connected=" + Main.CON_MAIN.isConnected() + " interrupted=" + Main.CON_MAIN.isInterrupted() + " user-auth=" + ConMain.isLoggedIn);
                    AL.info(ConMain.CON_PUBLIC_DETAILS.getClass().getName() + " connected=" + ConMain.CON_PUBLIC_DETAILS.isConnected());
                    AL.info(ConMain.CON_PRIVATE_DETAILS.getClass().getName() + " connected=" + ConMain.CON_PRIVATE_DETAILS.isConnected());
                    AL.info(ConMain.CON_CONSOLE_SEND.getClass().getName() + " connected=" + ConMain.CON_CONSOLE_SEND.isConnected());
                    AL.info(ConMain.CON_CONSOLE_RECEIVE.getClass().getName() + " connected=" + ConMain.CON_CONSOLE_RECEIVE.isConnected());
                    AL.info(ConMain.CON_FILE_MANAGER.getClass().getName() + " connected=" + ConMain.CON_FILE_MANAGER.isConnected());
                    return true;
                } else if (command.equals(".con reload") || command.equals(".cr")) {
                    Main.CON_MAIN.msUntilRetry = 1000;
                    Main.CON_MAIN.closeAll();
                    Main.CON_MAIN.interrupt();
                    Main.CON_MAIN = new ConMain();
                    Main.CON_MAIN.start();
                    return true;
                } else if (command.equals(".server info") || command.equals(".si")) {
                    AL.info("AutoPlug-Version: " + GD.VERSION);
                    ConSendPublicDetails conPublic = ConMain.CON_PUBLIC_DETAILS;
                    ConSendPrivateDetails conPrivate = ConMain.CON_PRIVATE_DETAILS;
                    AL.info("Running: " + Server.isRunning());
                    if (!conPublic.isConnected()) {
                        AL.info(conPublic.getClass().getSimpleName() + " is not active, thus more information cannot be retrieved!");
                    } else {
                        AL.info("Details from " + conPublic.getClass().getSimpleName() + ":");
                        AL.info("Host: " + conPublic.host + ":" + conPublic.port);
                        AL.info("Running: " + conPublic.isRunning);
                        AL.info("Version: " + conPublic.version);
                        AL.info("Players: " + conPublic.currentPlayers);
                        if (conPublic.mineStat != null) {
                            AL.info("Ping result: " + conPublic.mineStat.pingResult.name());
                        } else
                            AL.info("Ping result: -");
                        AL.info("Details from " + conPrivate.getClass().getSimpleName() + ":");
                        AL.info("CPU usage: " + conPrivate.cpuUsage + "%");
                        AL.info("CPU current: " + conPrivate.cpuSpeed + " GHz");
                        AL.info("CPU max: " + conPrivate.cpuMaxSpeed + " GHz");
                        AL.info("MEM free: " + conPrivate.memAvailable + " Gb");
                        AL.info("MEM used: " + conPrivate.memUsed + " Gb");
                        AL.info("MEM total: " + conPrivate.memTotal + " Gb");
                    }
                    return true;
                } else if (command.equals(".check") || command.equals(".c")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerWithDisplayer();
                    new TaskSelfUpdater("SelfUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".check java") || command.equals(".cj")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerWithDisplayer();
                    new TaskJavaUpdater("JavaUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".check server") || command.equals(".cs")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerWithDisplayer();
                    new TaskServerUpdater("ServerUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".check plugins") || command.equals(".cp")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerWithDisplayer();
                    new TaskPluginsUpdater("PluginsUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".check mods") || command.equals(".cm")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerWithDisplayer();
                    new TaskModsUpdater("ModsUpdater", myManager.manager).start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else if (command.equals(".backup") || command.equals(".b")) {
                    MyBThreadManager myManager = new UtilsTasks().createManagerWithDisplayer();
                    TaskBackup backupTask = new TaskBackup("BackupTask", myManager.manager);
                    backupTask.ignoreCooldown = true;
                    backupTask.start();
                    new UtilsTasks().printResultsWhenDone(myManager.manager);
                    return true;
                } else {
                    AL.info("Command '" + command + "' not found! Enter .help or .h for all available commands!");
                    return true;
                }
            } catch (Exception e) {
                AL.warn("Error at execution of '" + command + "' command!", e);
                return true;
            }
        } else
            return false;
    }


}
