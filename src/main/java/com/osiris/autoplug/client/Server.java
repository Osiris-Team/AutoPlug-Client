/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleSend;
import com.osiris.autoplug.client.tasks.BeforeServerStartupTasks;
import com.osiris.autoplug.client.utils.AsyncTerminal;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsJar;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.exceptions.*;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.fusesource.jansi.Ansi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.utils.OSUtils;
import org.jutils.jprocesses.JProcess;
import org.jutils.jprocesses.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public final class Server {
    @Nullable
    public static AsyncTerminal terminal;
    private static JProcess terminalProcess;
    private static JProcess serverProcess;
    private static Thread threadServerAliveChecker;
    private static boolean colorServerLog;
    private static boolean isAlive;

    public static void start() {
        try {
            new ProcessBuilder().start();
            try {
                colorServerLog = new LoggerConfig().color_server_log.asBoolean();
            } catch (Exception e) {
                AL.warn(e);
            }

            if (isRunning()) throw new Exception("Server already running!");

            // Runs all processes before starting the server
            new BeforeServerStartupTasks();

            // Find server jar
            new UtilsJar().determineServerJar();
            if (GD.SERVER_JAR == null || !GD.SERVER_JAR.exists())
                throw new Exception("Failed to find your server jar! " +
                        "Please check your config, you may need to specify the jars name/path! " +
                        "Searched dir: '" + GD.WORKING_DIR + "'");

            AL.info("Note: AutoPlug has some own console commands (enter .help or .h).");
            AL.info("Starting server jar: " + GD.SERVER_JAR.getName());
            String path = GD.SERVER_JAR.toString();
            GeneralConfig config = new GeneralConfig();
            StringBuilder startCommandBuilder = new StringBuilder();

            // 1. Which java version should be used
            UpdaterConfig updaterConfig = new UpdaterConfig();
            if (updaterConfig.java_updater.asBoolean()
                    && (updaterConfig.java_updater_profile.asString().equals("AUTOMATIC"))) {
                try {
                    if (updaterConfig.java_updater_build_id.asInt() == 0) // Throws nullpointer if value if empty
                        throw new Exception();
                } catch (Exception e) {
                    throw new Exception("Java-Updater is enabled, but Java-Installation was not found! Enter '.check java' to install Java.");
                }
                FileManager fileManager = new FileManager();
                File jreFolder = new File(GD.WORKING_DIR + "/autoplug/system/jre");
                List<File> folders = fileManager.getFoldersFrom(jreFolder);
                if (folders.isEmpty())
                    throw new Exception("No Java-Installation was found in '" + jreFolder.getAbsolutePath() + "'!");
                File javaInstallationFolder = folders.get(0);
                File javaBinFolder = null;
                for (File folder :
                        fileManager.getFoldersFrom(javaInstallationFolder)) {// This are the files inside a java installation

                    if (folder.getName().equalsIgnoreCase("Home")) // For macos support
                        for (File folder2 :
                                folder.listFiles()) {
                            if (folder2.getName().equals("bin")) {
                                javaBinFolder = folder;
                                break;
                            }
                        }

                    if (folder.getName().equals("bin")) { // Regular java installations
                        javaBinFolder = folder;
                        break;
                    }
                }
                if (javaBinFolder == null)
                    throw new Exception("No Java 'bin' folder found inside of Java installation at path: '" + jreFolder.getAbsolutePath() + "'");
                File javaFile = null;
                if (SystemUtils.IS_OS_WINDOWS) {
                    for (File file :
                            fileManager.getFilesFrom(javaBinFolder)) {
                        if (file.getName().equals("java.exe")) {
                            javaFile = file;
                            break;
                        }
                    }
                } else {
                    for (File file :
                            fileManager.getFilesFrom(javaBinFolder)) {
                        if (file.getName().equals("java")) {
                            javaFile = file;
                            break;
                        }
                    }
                }

                if (javaFile == null)
                    throw new Exception("No 'java' file found inside of Java installation at path: '" + javaBinFolder.getAbsolutePath() + "'");

                startCommandBuilder.append(javaFile.getAbsolutePath());

            } else { // Means that the java updater is disabled or set to NOTIFY
                if (!config.server_java_path.asString().equals("java")) {
                    startCommandBuilder.append(config.server_java_path.asString());
                } else {
                    startCommandBuilder.append("java");
                }
            }

            // 2. Add all before-flags
            if (config.server_flags_enabled.asBoolean()) {
                List<String> list = config.server_flags_list.asStringList();
                for (String s : list) {
                    startCommandBuilder.append(" -" + s);
                }
            }

            // 3. Add the -jar command and server jar path
            startCommandBuilder.append(" -jar");
            // To ensure that russian and other chars in the file path/name are read correctly
            // and don't prevent the jar from starting we do the following:
            startCommandBuilder.append(" " + path);

            // 4. Add all arguments
            if (config.server_arguments_enabled.asBoolean()) {
                List<String> list = config.server_arguments_list.asStringList();
                for (String arg : list) {
                    startCommandBuilder.append(" " + arg);
                }
            }

            String startCommand = startCommandBuilder.toString();
            AL.debug(Server.class, "Starting server with command: " + startCommand);
            ProcessUtils processUtils = new ProcessUtils();
            if (terminalProcess != null) {
                if (!terminalProcess.stop().isSuccess())
                    AL.warn("Failed to close the old server terminal. Please report this.");
            }
            long msNow = System.currentTimeMillis();
            terminal = new AsyncTerminal(GD.WORKING_DIR, // Create terminal in which we execute the start command
                    line -> {
                        try {
                            Ansi ansi = Ansi.ansi();
                            if (colorServerLog) {
                                if (StringUtils.containsIgnoreCase(line, "error") ||
                                        StringUtils.containsIgnoreCase(line, "critical") ||
                                        StringUtils.containsIgnoreCase(line, "exception")) {
                                    ansi.fgRed().a(line).reset();
                                } else if (StringUtils.containsIgnoreCase(line, "warn") ||
                                        StringUtils.containsIgnoreCase(line, "warning")) {
                                    ansi.fgYellow().a(line).reset();
                                } else if (StringUtils.containsIgnoreCase(line, "debug")) {
                                    ansi.fgCyan().a(line).reset();
                                } else {
                                    ansi.a(line).reset();
                                }
                            } else {
                                ansi.a(line).reset();
                            }
                            System.out.println(ansi);
                            ConOnlineConsoleSend.send("" + ansi);
                        } catch (Exception e) {
                            AL.warn(e);
                        }
                    },
                    errLine -> {
                        try {
                            Ansi ansi = Ansi.ansi().fgRed().a("[!] " + errLine).reset();
                            System.out.println(ansi);
                            ConOnlineConsoleSend.send("" + ansi);
                        } catch (Exception e) {
                            AL.warn(e);
                        }
                    },
                    startCommand
            );
            List<JProcess> processes = processUtils.getProcesses();
            Thread.sleep(2000); // Just to make sure the terminal and server process were started
            List<JProcess> childProcesses = processUtils.getThisProcess(processes).childProcesses;
            if (childProcesses.size() > 1) { // Find the right terminal process
                // If there are multiple child processes of this process, we need to find the process closest to the
                // current start time. That's why we do the below:
                long smallestDifference = 1000000000;
                for (JProcess childProcess :
                        childProcesses) {
                    long difference = msNow - childProcess.getTimestampStart().getTime();
                    if (Math.abs(difference) < smallestDifference) {
                        smallestDifference = difference;
                        terminalProcess = childProcess;
                    }

                }
            } else
                terminalProcess = childProcesses.get(0);
            serverProcess = terminalProcess.childProcesses.get(0);
            AL.debug(Server.class, "Terminal process: " + terminalProcess.name + " " + terminalProcess.pid);
            AL.debug(Server.class, "Server process: " + serverProcess.name + " " + serverProcess.pid);

            // Also create a thread which checks if the server is running or not.
            if (threadServerAliveChecker == null) {
                threadServerAliveChecker = new Thread(() -> {
                    try {
                        boolean lastIsRunningCheck = false;
                        boolean currentIsRunningCheck;
                        while (true) {
                            Thread.sleep(4000);
                            isAlive = serverProcess.getExtraInfo().isAlive;
                            currentIsRunningCheck = Server.isRunning();
                            if (!currentIsRunningCheck && lastIsRunningCheck) {
                                AL.info("Server was stopped.");
                                if (new GeneralConfig().autoplug_auto_stop.asBoolean()) {
                                    AL.info("Stopping AutoPlug too, since 'autoplug-stop' is enabled.");
                                    System.exit(0);
                                } else {
                                    AL.info("To stop AutoPlug too, enter '.stop both'.");
                                }
                                AtomicInteger exitCode = new AtomicInteger(-1);
                                terminal.readerLines.listeners.add(line -> {
                                    if (line.contains("EXIT-CODE"))
                                        exitCode.set(Integer.parseInt(line.substring(line.lastIndexOf("=") + 1, line.length() - 1).trim()));
                                });
                                int index = terminal.readerLines.listeners.size() - 1;
                                if (OSUtils.IS_WINDOWS) terminal.sendCommands("echo EXIT-CODE=$?");
                                else terminal.sendCommands("echo EXIT-CODE=%errorlevel%");
                                AL.debug(Server.class, "Checking exit code...");
                                while (exitCode.get() < 0)
                                    Thread.sleep(200);
                                terminal.readerLines.listeners.remove(index);
                                if (exitCode.get() != 0) {
                                    AL.warn("Server crash was detected! Exit-Code should be 0, but is '" + exitCode.get() + "'!");
                                    if (new GeneralConfig().server_restart_on_crash.asBoolean()) {
                                        AL.info("Restart on crash is enabled, thus the server is restarting...");
                                        Server.start();
                                    }
                                } else
                                    AL.debug(Server.class, "Exit code is 0. Everything is fine!");
                            }
                            lastIsRunningCheck = currentIsRunningCheck;
                        }
                    } catch (Exception e) {
                        AL.error("Thread for checking if Server is alive was stopped due to an error.", e);
                    }
                });
                threadServerAliveChecker.start();
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

    /**
     * Blocks until the server was stopped.
     */
    public static void stop() throws IOException, InterruptedException, YamlWriterException, NotLoadedException, IllegalKeyException, DuplicateKeyException, YamlReaderException, IllegalListException {

        AL.info("Stopping server...");

        if (isRunning()) {
            submitCommand(new GeneralConfig().server_stop_command.asString());
            while (Server.isRunning())
                Thread.sleep(1000);
            terminal = null;
        } else {
            AL.warn("Server not running!");
        }

    }

    /**
     * Blocks until server was killed.
     */
    public static boolean kill() {

        AL.info("Killing server!");
        try {

            if (isRunning()) {
                serverProcess.kill();
                terminalProcess.kill();
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
        return isAlive;
    }

    public static String getFileNameWithoutExt(String fileNameWithExt) throws NotLoadedException {
        return fileNameWithExt.replaceFirst("[.][^.]+$", ""); // Removes the file extension
    }


    private static boolean hasColorSupport(@NotNull String path) throws IOException {
        ZipFile zipFile = new ZipFile(path);
        FileHeader fileHeader = zipFile.getFileHeader("fileNameInZipToRemove");

        if (fileHeader == null) {
            // file does not exist
        }

        zipFile.removeFile(fileHeader);
        return false;
    }

    public static void submitCommand(@NotNull String command) throws IOException {
        if (isRunning() && terminal != null) {
            terminal.sendCommands(command);
        } else {
            AL.warn("Failed to submit command '" + command + "' because server is not running!");
        }
    }

}
