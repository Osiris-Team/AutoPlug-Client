/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.minecraft;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.tasks.BeforeServerStartupTasks;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static com.osiris.betterthread.Constants.TERMINAL;


public final class Server {
    private static Process process;
    private static Thread threadServerAliveChecker;

    public static void start() {

        try {
            if (isRunning()) {
                AL.warn("Server already running!");
            } else {
                // Runs all processes before starting the server
                new BeforeServerStartupTasks();

                if (GD.SERVER_PATH == null || !GD.SERVER_PATH.exists())
                    throw new Exception("Failed to find your server jar! " +
                            "Please check your config, you may need to specify the jars name/path! " +
                            "Searched dir: '" + GD.WORKING_DIR + "'");

                AL.info("Starting server jar: " + GD.SERVER_PATH.getName());
                AL.info("Note: AutoPlug has some own console server. For details enter .help or .h");
                Thread.sleep(1000);
                AL.info("Starting server in 3");
                Thread.sleep(1000);
                AL.info("Starting server in 2");
                Thread.sleep(1000);
                AL.info("Starting server in 1");
                Thread.sleep(1000);
                createProcess(GD.SERVER_PATH.toPath().toString());
            }

        } catch (Exception e) {
            AL.warn(e);
        }
    }

    public static void restart() {
        //Before starting make backups and check for updates
        AL.info("Restarting server...");
        try {
            stop();
            start();
        } catch (Exception e) {
            AL.warn(e);
        }
    }

    public static void stop() {

        AL.info("Stopping server...");

        if (isRunning()) {
            System.out.println("stop");
            AL.info("Stop command executed!");
        } else {
            AL.warn("Server is not running!");
        }

    }

    public static boolean kill() {

        AL.info("Killing server!");
        try {

            if (isRunning()) {
                process.destroy();
            } else {
                AL.warn("Server is not running!");
            }

            while (isRunning()) {
                Thread.sleep(1000);
            }
            AL.info("Server killed!");
            return true;

        } catch (InterruptedException e) {
            AL.warn(e);
            return false;
        }
    }

    public static boolean isRunning() {
        return process != null && process.isAlive();
    }

    private static void createProcess(String path) throws IOException, InterruptedException {
        GeneralConfig config = new GeneralConfig();
        List<String> commands = new ArrayList<>();

        // 1. Which java version should be used
        if (!config.server_java_version.asString().equals("java")) {
            commands.add(config.server_java_version.asString());
        } else {
            commands.add("java");
        }

        // 2. Add all before-flags
        if (config.server_flags_enabled.asBoolean()) {
            List<String> list = config.server_flags_list.asStringList();
            for (String s : list) {
                commands.add("-" + s);
            }
        }

        // 3. Add the -jar command and server jar path
        commands.add("-jar");
        commands.add(path);

        // 4. Add all arguments
        if (config.server_arguments_enabled.asBoolean()) {
            List<String> list = config.server_arguments_list.asStringList();
            for (String s : list) {
                commands.add("" + s);
            }
        }

        // The stuff below fixes https://github.com/Osiris-Team/AutoPlug-Client/issues/32
        // but messes input up, because there are 2 scanners on the same stream.
        // That's why we pause the current Terminal, which disables the user from entering console commands.
        // If AutoPlug-Plugin is installed the user can executed AutoPlug commands through in-game or console.
        AL.debug(Server.class, "Starting server with commands: " + commands);
        TERMINAL.pause(true);
        ProcessBuilder processBuilder = new ProcessBuilder(commands); // The commands list contains all we need.
        processBuilder.inheritIO();
        process = processBuilder.start();

        // Also create a thread which checks if the server is running or not.
        // Resume the terminal if the server stopped running, to allow the use of AutoPlug-Commands
        if (threadServerAliveChecker == null) {
            threadServerAliveChecker = new Thread(() -> {
                try {
                    boolean lastIsRunningCheck = false;
                    boolean currentIsRunningCheck;
                    while (true) {
                        Thread.sleep(2000);
                        currentIsRunningCheck = Server.isRunning();
                        if (!currentIsRunningCheck && lastIsRunningCheck) {
                            AL.info("Minecraft server was stopped.");
                            AL.info("To stop AutoPlug too, enter '.stop both'.");
                            TERMINAL.resume();
                        }
                        lastIsRunningCheck = currentIsRunningCheck;
                    }
                } catch (Exception e) {
                    AL.warn("Thread for checking if MC-Server is alive was stopped due to an error.", e);
                }
            });
            threadServerAliveChecker.start();
        }
    }

    public static void submitCommand(String command) throws IOException {
        if (isRunning()) {
            // Since the command won't be executed if it doesn't end with a new line char we do the below:
            if (command.contains(System.lineSeparator()))
                TERMINAL.writer().write(command);
            else
                TERMINAL.writer().write(command + System.lineSeparator());

        }
    }

    public InputStream getInput() {
        return process.getInputStream();
    }

    public OutputStream getOutput() {
        return process.getOutputStream();
    }

}
