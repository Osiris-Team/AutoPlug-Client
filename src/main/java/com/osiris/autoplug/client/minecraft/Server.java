/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.minecraft;

import com.osiris.autoplug.client.tasks.BeforeServerStartupTasks;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static com.osiris.autoplug.client.utils.GD.MC_SERVER_IN;

public final class Server {
    private static Process process;
    private static OutputStream os;

    public static void start(){

        try {
            if (isRunning()) {
                AL.warn("Server already running!");
            } else{
                // Runs all processes before starting the server
                new BeforeServerStartupTasks();

                if (GD.SERVER_PATH==null || !GD.SERVER_PATH.exists())
                    throw new Exception("Failed to find your server jar! " +
                                "Please check your config, you may need to specify the jars name/path! " +
                                "Searched dir: '"+GD.WORKING_DIR+"'");

                AL.info("Starting server jar: "+ GD.SERVER_PATH.getName());
                AL.info("Note: AutoPlug has some own console server. For details enter .help or .h");
                Thread.sleep(1000);
                AL.info("Starting server in 3");
                Thread.sleep(1000);
                AL.info("Starting server in 2");
                Thread.sleep(1000);
                AL.info("Starting server in 1");
                Thread.sleep(1000);
                createProcess(GD.SERVER_PATH.toPath().toString());
                createConsole();
            }

        }
        catch (Exception e){
            AL.warn(e);
        }
    }

    public static void restart(){
        //Before starting make backups and check for updates
        AL.info("Restarting server...");
        try {
            stop();
            start();
        }
        catch (Exception e){
            AL.warn(e);
        }
    }

    public static void stop(){

        AL.info("Stopping server...");
        try {
            String stop_command = "stop\n"; //WARNING: Without the \n user input isn't registered by the server console

            if (isRunning()) {
                os = process.getOutputStream();
                os.write(stop_command.getBytes());
                os.flush();
                AL.info("Stop command executed!");
            } else{
                AL.warn("Server is not running!");
            }

            while(isRunning()){
                Thread.sleep(1000);
            }
            AL.info("Server was stopped!");
            AL.info("To close AutoPlug(this console) enter .close or .kill");
            AL.info("or all server enter .help");


        } catch (IOException | InterruptedException e) {
            AL.warn("Error stopping server!", e);
        }

    }

    public static boolean kill(){

        AL.info("Killing server!");
        try {

            if (isRunning()) {
                process.destroy();
            } else{
                AL.warn("Server is not running!");
            }

            while(isRunning()){
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

    private static void createProcess(String path) throws IOException {
        GeneralConfig config = new GeneralConfig();

        List<String> commands = new ArrayList<>();
        // 1. Which java version should be used
        if (!config.server_java_version.asString().equals("java")){
            commands.add(config.server_java_version.asString());
        }
        else{
            commands.add("java");
        }

        // 2. Add all before-flags
        if (config.server_flags_enabled.asBoolean()){
            List<String> list = config.server_flags_list.asStringList();
            for (String s : list) {
                commands.add("-" + s);
            }
        }

        // 3. Add the -jar command and server jar path
        commands.add("-jar");
        commands.add(path);

        // 4. Add all after-flags
        if (config.server_arguments_enabled.asBoolean()){
            List<String> list = config.server_arguments_list.asStringList();
            for (String s : list) {
                commands.add("" + s);
            }
        }


        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.directory();
        //processBuilder.inheritIO();
        // Fixes https://github.com/Osiris-Team/AutoPlug-Client/issues/32
        // but messes input up, because there are 2 scanners on the same stream
        process = processBuilder.start();
    }

    @Deprecated
    private static void createConsole(){

        // Then retrieve the process streams
        MC_SERVER_IN = process.getInputStream();

        //New thread for input from server console
        Thread thread =
        new Thread(() -> {
            try{
                if (isRunning()){
                    int b = -1;
                    while ( (b =  MC_SERVER_IN.read()) != -1 ) {
                        System.out.write(b);
                    }
                    if (!isRunning()){ MC_SERVER_IN.close(); }
                }

            } catch (IOException e) {
                AL.warn(e);
            }
        });
        thread.setName("McServerInToConsoleOutThread");
        thread.start();
    }

    public static void submitCommand(String command){
        try {
            String final_user_input = command + "\n"; //WARNING: Without the \n user input isn't registered by the server console
            if (isRunning()) {
                os = process.getOutputStream();
                os.write(final_user_input.getBytes());
                os.flush();
            }
            os = null;
        } catch (IOException e) {
            AL.warn(e);
        }

    }

}
