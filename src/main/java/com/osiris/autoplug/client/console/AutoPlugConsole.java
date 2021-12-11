/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.console;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.network.online.connections.ConServerStatus;
import com.osiris.autoplug.client.tasks.BeforeServerStartupTasks;
import com.osiris.autoplug.client.tasks.updater.java.TaskJavaUpdater;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.autoplug.client.tasks.updater.self.TaskSelfUpdater;
import com.osiris.autoplug.client.tasks.updater.server.TaskServerUpdater;
import com.osiris.autoplug.client.utils.UtilsBetterThread;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThreadManager;
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
            first = Character.toString(command.charAt(0));
        } catch (Exception e) {
            AL.warn("Failed to read command '" + command + "'! Enter .help for all available commands!", e);
            return false;
        }

        if (first.equals(".")) {
            try {
                if (command.equals(".help") || command.equals(".h")) {
                    AL.info("");
                    AL.info("All available AutoPlug-Console commands:");
                    AL.info(".help | Prints out this (Shortcut: .h)");
                    AL.info(".start | Starts the server (.s)");
                    AL.info(".restart | Restarts the server (.r)");
                    AL.info(".stop | Stops and saves the server (.st)");
                    AL.info(".stop both | Stops, saves your server and closes AutoPlug safely (.stb)");
                    AL.info(".kill | Kills the server without saving (.k)");
                    AL.info(".kill both | Kills the server without saving and closes AutoPlug (.kb)");
                    AL.info(".run tasks | Runs the 'before server startup tasks' without starting the server (.rt)");
                    AL.info(".con info | Shows details about AutoPlugs network connections (.ci)");
                    AL.info(".server info | Shows details about this server (.si)");
                    AL.info(".check | Checks for AutoPlug updates and behaves according to the selected profile (.c)");
                    AL.info(".check java | Checks for Java updates and behaves according to the selected profile (.cj)");
                    AL.info(".check server | Checks for server updates and behaves according to the selected profile (.cs)");
                    AL.info(".check plugins | Checks for plugins updates and behaves according to the selected profile (.cp)");
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
                    System.exit(0);
                    return true;
                } else if (command.equals(".run tasks") || command.equals(".rt")) {
                    new BeforeServerStartupTasks();
                    return true;
                } else if (command.equals(".con info") || command.equals(".ci")) {
                    AL.info(Main.CON_MAIN.getName() + " interrupted=" + Main.CON_MAIN.isInterrupted() + " user-auth=" + ConMain.isUserAuthenticated);
                    AL.info(ConMain.CON_SERVER_STATUS.getClass().getName() + " connected=" + ConMain.CON_SERVER_STATUS.isConnected());
                    AL.info(ConMain.CON_CONSOLE_SEND.getClass().getName() + " connected=" + ConMain.CON_CONSOLE_SEND.isConnected());
                    AL.info(ConMain.CON_CONSOLE_RECEIVE.getClass().getName() + " connected=" + ConMain.CON_CONSOLE_RECEIVE.isConnected());
                    AL.info(ConMain.CON_FILE_MANAGER.getClass().getName() + " connected=" + ConMain.CON_FILE_MANAGER.isConnected());
                    return true;
                } else if (command.equals(".server info") || command.equals(".si")) {
                    ConServerStatus con = ConMain.CON_SERVER_STATUS;
                    AL.info("Running: " + Server.isRunning());
                    AL.info("Port: " + Server.PORT);
                    if (!con.isConnected()) {
                        AL.info(con.getClass().getSimpleName() + " is not active, thus more information cannot be retrieved!");
                    } else {
                        AL.info("Details from " + con.getClass().getSimpleName() + ":");
                        AL.info("Host: " + con.host);
                        AL.info("Port: " + Server.PORT);
                        AL.info("Running: " + con.isRunning);
                        AL.info("Motd: " + con.strippedMotd);
                        AL.info("Version: " + con.version);
                        AL.info("Players: " + con.currentPlayers);
                        AL.info("CPU Speed in GHz: " + con.cpuSpeed);
                        AL.info("CPU Max. Speed in GHz: " + con.cpuMaxSpeed);
                        AL.info("MEM available in Gb: " + con.memAvailable);
                        AL.info("MEM used in Gb: " + con.memUsed);
                        AL.info("MEM total in Gb: " + con.memTotal);
                    }
                    return true;
                }
                else if (command.equals(".check") || command.equals(".c")) {
                    BetterThreadManager man = new UtilsBetterThread().createManagerWithDisplayer();
                    new TaskSelfUpdater("SelfUpdater", man);
                    return true;
                }else if (command.equals(".check java") || command.equals(".cj")) {
                    BetterThreadManager man = new UtilsBetterThread().createManagerWithDisplayer();
                    new TaskJavaUpdater("JavaUpdater", man);
                    return true;
                }else if (command.equals(".check server") || command.equals(".cs")) {
                    BetterThreadManager man = new UtilsBetterThread().createManagerWithDisplayer();
                    new TaskServerUpdater("ServerUpdater",man);
                    return true;
                }else if (command.equals(".check plugins") || command.equals(".cp")) {
                    BetterThreadManager man = new UtilsBetterThread().createManagerWithDisplayer();
                    new TaskPluginsUpdater("PluginsUpdater",man);
                    return true;
                }else {
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
