/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleSend;
import com.osiris.autoplug.client.tasks.BeforeServerStartupTasks;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.NonBlockingPipedInputStream;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.exceptions.*;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.lang.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


public final class Server {

    public static int PORT = 0;

    static {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(GD.WORKING_DIR + "/server.properties"));
            PORT = Integer.parseInt(properties.getProperty("server-port"));
        } catch (IOException e) {
            AL.warn(e);
        }
    }

    @Nullable
    public static NonBlockingPipedInputStream NB_SERVER_IN;
    private static Process process;
    private static Thread threadServerAliveChecker;
    private static Thread threadReadOutputStream;

    public static void start() {

        try {
            if (isRunning()) {
                AL.warn("Server already running!");
            } else {

                // Update PORT
                Properties properties = new Properties();
                try {
                    properties.load(new FileInputStream(GD.WORKING_DIR + "/server.properties"));
                    PORT = Integer.parseInt(properties.getProperty("server-port"));
                } catch (IOException e) {
                    AL.warn(e);
                }

                // Runs all processes before starting the server
                new BeforeServerStartupTasks();

                // Find server jar
                GeneralConfig generalConfig = new GeneralConfig();
                FileManager fileManager = new FileManager();
                String jar = generalConfig.server_jar.asString();
                if (!jar.equals("auto-find")) {
                    if (jar.contains("/") || jar.contains("\\")) {
                        if (jar.startsWith("./"))
                            GD.SERVER_JAR = FileManager.convertRelativeToAbsolutePath(jar);
                        else
                            GD.SERVER_JAR = new File(jar);
                    } else
                        GD.SERVER_JAR = fileManager.serverJar(jar);
                } else
                    GD.SERVER_JAR = fileManager.serverJar();
                if (GD.SERVER_JAR == null || !GD.SERVER_JAR.exists())
                    throw new Exception("Failed to find your server jar! " +
                            "Please check your config, you may need to specify the jars name/path! " +
                            "Searched dir: '" + GD.WORKING_DIR + "'");

                AL.info("Note: AutoPlug has some own console commands (enter .help or .h).");
                AL.info("Starting server jar: " + GD.SERVER_JAR.getName());
                createProcess(GD.SERVER_JAR.toPath().toString());
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
    public static void stop() throws IOException, InterruptedException, DYWriterException, NotLoadedException, IllegalKeyException, DuplicateKeyException, DYReaderException, IllegalListException {

        AL.info("Stopping server...");

        if (isRunning()) {
            submitCommand(new GeneralConfig().server_stop_command.asString());
            while (Server.isRunning())
                Thread.sleep(1000);
            NB_SERVER_IN = null;
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

    private static void createProcess(String path) throws Exception {
        GeneralConfig config = new GeneralConfig();
        List<String> commands = new ArrayList<>();

        // 1. Which java version should be used
        UpdaterConfig updaterConfig = new UpdaterConfig();
        if (updaterConfig.java_updater.asBoolean()
                && (updaterConfig.java_updater_profile.asString().equals("AUTOMATIC") || updaterConfig.java_updater_profile.asString().equals("MANUAL"))) {
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

            // To ensure that russian and other chars in the file path/name are read correctly
            // and don't prevent the jar from starting we do the following:
            commands.add(new String(javaFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        } else { // Means that the java updater is disabled or set to NOTIFY
            if (!config.server_java_path.asString().equals("java")) {
                // To ensure that russian and other chars in the file path/name are read correctly
                // and don't prevent the jar from starting we do the following:
                commands.add(new String(config.server_java_path.asString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            } else {
                commands.add("java");
            }
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
        // To ensure that russian and other chars in the file path/name are read correctly
        // and don't prevent the jar from starting we do the following:
        commands.add(new String(path.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        // 4. Add all arguments
        if (config.server_arguments_enabled.asBoolean()) {
            List<String> list = config.server_arguments_list.asStringList();
            for (String s : list) {
                commands.add("" + s);
            }
        }

        // 5. Check if the jar has jline installed and enable colors if it has
        boolean supportsColors = false;
        try {
            //TODO supportsColors = hasColorSupport(path);
            if (supportsColors)
                commands.add("-Dorg.jline.terminal.dumb.color=true");
        } catch (Exception e) {
            AL.warn("Your server jar does not contain the required dependency to enable colors.", e);
        }


        // The stuff below fixes https://github.com/Osiris-Team/AutoPlug-Client/issues/32
        // but messes input up, because there are 2 scanners on the same stream.
        // That's why we pause the current Terminal, which disables the user from entering console commands.
        // If AutoPlug-Plugin is installed the user can executed AutoPlug commands through in-game or console.
        AL.debug(Server.class, "Starting server with commands: " + commands);
        //TERMINAL.pause(true);
        ProcessBuilder processBuilder = new ProcessBuilder(commands); // The commands list contains all we need.
        processBuilder.redirectErrorStream(true);
        //processBuilder.inheritIO(); // BACK TO PIPED, BECAUSE OF MASSIVE ERRORS LIKE COMMANDS NOT BEEING EXECUTED, which affects the restarter
        processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        process = processBuilder.start();

        if (threadReadOutputStream == null) {
            threadReadOutputStream = new Thread(() -> {
                try {
                    boolean isRunningOld = false; // So the stuff in the while loop below only happens once and not every time
                    InputStream serverIn = null;
                    while (true) {
                        if (isRunningOld != isRunning()) {
                            isRunningOld = isRunning();
                            serverIn = process.getInputStream();
                            if (isRunning() && serverIn != null) {
                                // Get Servers OutputStream, and forward it to a NonBlockingInputStream.
                                // From there multiple listeners can be attached.
                                NB_SERVER_IN = new NonBlockingPipedInputStream();
                                OutputStream pipedOut = new PipedOutputStream(NB_SERVER_IN);
                                NB_SERVER_IN.actionsOnWriteLineEvent.add(line -> {
                                    System.out.println(line);
                                });
                                if (!NB_SERVER_IN.actionsOnWriteLineEvent.contains(ConOnlineConsoleSend.actionOnServerLineWriteEvent))
                                    NB_SERVER_IN.actionsOnWriteLineEvent.add(ConOnlineConsoleSend.actionOnServerLineWriteEvent);
                                int b = -1;
                                while ((b = serverIn.read()) != -1) {
                                    pipedOut.write(b);
                                }

                            } else { // The server is not running, so we check the exit code and restart depending on that
                                if (process.exitValue() != 0) {
                                    AL.warn("Server crash was detected! Exit-Code should be 0, but is '" + process.exitValue() + "'!");
                                    if (new GeneralConfig().server_restart_on_crash.asBoolean()) {
                                        AL.info("Restart on crash is enabled, thus the server is restarting...");
                                        Server.start();
                                    }
                                }

                            }
                        }
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    AL.warn(e);
                }
            });
            threadReadOutputStream.start();
        }

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
                            AL.info("Server was stopped.");
                            if (new GeneralConfig().server_autoplug_stop.asBoolean()) {
                                AL.info("Stopping AutoPlug too, since 'autoplug-stop' is enabled.");
                                System.exit(0);
                            } else {
                                AL.info("To stop AutoPlug too, enter '.stop both'.");
                            }
                            //TERMINAL.resume();
                            try {
                                if (NB_SERVER_IN != null) NB_SERVER_IN.close();
                            } catch (Exception e) {
                                AL.warn(e);
                            }
                            NB_SERVER_IN = null;
                        }
                        lastIsRunningCheck = currentIsRunningCheck;
                    }
                } catch (Exception e) {
                    AL.warn("Thread for checking if Server is alive was stopped due to an error.", e);
                }
            });
            threadServerAliveChecker.start();
        }
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
        if (isRunning()) {
            OutputStream os = process.getOutputStream();
            // Since the command won't be executed if it doesn't end with a new line char we do the below:
            if (command.contains(System.lineSeparator()))
                os.write(command.getBytes(StandardCharsets.UTF_8));//TERMINAL.writer().write(command);
            else
                os.write((command + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));//TERMINAL.writer().write(command + System.lineSeparator());
            os.flush();
        } else {
            AL.warn("Failed to submit command '" + command + "' because server is not running!");
        }
    }

}
