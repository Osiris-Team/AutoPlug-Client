/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.server;

import com.osiris.autoplug.client.configs.ServerConfig;
import com.osiris.autoplug.client.managers.BackupManager;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class Server {

    public Server(){
        AutoPlugLogger.newClassDebug("Server");
    }

    private static Process process;

    private static OutputStream os;


    public static void start(){

        try {
            if (isRunning()) {
                AutoPlugLogger.warn(" Server already running!");
            } else{
                //Before starting make backups and check for updates
                new BackupManager();
                new ServerUpdater();

                AutoPlugLogger.info(" Found server jar at: "+ GD.SERVER_PATH.toPath().toString() );
                Thread.sleep(1000);
                AutoPlugLogger.info(" Starting server in 5");
                Thread.sleep(1000);
                AutoPlugLogger.info(" Starting server in 4");
                Thread.sleep(1000);
                AutoPlugLogger.info(" Starting server in 3");
                Thread.sleep(1000);
                AutoPlugLogger.info(" Starting server in 2");
                Thread.sleep(1000);
                AutoPlugLogger.info(" Starting server in 1");
                AutoPlugLogger.info(" Note: AutoPlug has its own console commands. For details enter .help or .h");
                Thread.sleep(1000);
                createProcess(GD.SERVER_PATH.toPath().toString());
                createConsole();
            }


        }
        catch (NullPointerException | IOException | InterruptedException ex){
            ex.printStackTrace();
            AutoPlugLogger.warn("[!] Could'nt find your server jar [!]");
            AutoPlugLogger.warn("[!] Searched dir: "+GD.WORKING_DIR+" [!]");
            AutoPlugLogger.warn("[!] Please check your config file [!]");
            AutoPlugLogger.warn("[!] You may need to specify the jars name [!]");
        }
    }

    public static void restart(){
        //Before starting make backups and check for updates
        AutoPlugLogger.info(" Restarting server...");
        try {
            stop();
            start();
        }
        catch (Exception ex){ex.printStackTrace();}
    }

    public static void stop(){

        AutoPlugLogger.info(" Stopping server...");
        try {
            String stop_command = "stop\n"; //WARNING: Without the \n user input isn't registered by the server console

            if (isRunning()) {
                os = process.getOutputStream();
                os.write(stop_command.getBytes());
                os.flush();
                AutoPlugLogger.info(" Stop command executed!");
            } else{
                AutoPlugLogger.warn(" Server is not running!");
            }

            while(isRunning()){
                Thread.sleep(1000);
            }
            AutoPlugLogger.info(" Server was stopped!");
            AutoPlugLogger.info(" To close AutoPlug(this console) enter .close or .kill");
            AutoPlugLogger.info(" For all commands enter .help");


        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            AutoPlugLogger.warn(" [!] Error stopping server: "+e.getMessage()+" [!]");
        }



    }

    public static boolean kill(){

        AutoPlugLogger.info(" Killing server!");
        try {

            if (isRunning()) {
                process.destroy();
            } else{
                AutoPlugLogger.warn(" Server is not running!");
            }

            while(isRunning()){
                Thread.sleep(1000);
            }
            AutoPlugLogger.info(" Server killed!");
            return true;

        } catch (InterruptedException e) {
            e.printStackTrace();
            AutoPlugLogger.warn(" [!] Error killing server: "+e.getMessage()+" [!]");
            return false;

        }
    }

    public static boolean isRunning() {
        return process != null && process.isAlive();
    }

    private static void createProcess(String path) throws IOException {

        List<String> commands = new ArrayList<>();
        //First command
        commands.add("java");

        for (int i = 0; i < ServerConfig.server_flags.size(); i++) {
            commands.add("-"+ServerConfig.server_flags.get(i));
        }

        //Last commands
        commands.add("-jar");
        commands.add(path);

        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.directory();
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();
    }

    private static void createConsole(){

        // Then retrieve the process streams
        InputStream in = process.getInputStream();

        //New thread for input from server console
        new Thread(new Runnable() {
            public void run() {
                try{

                    if (isRunning()){
                        int b = -1;
                        while ( (b =  in.read()) != -1 ) {
                            System.out.write(b);
                        }
                        if (!isRunning()){ in.close(); }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();

    }

    public static void submitServerCommand(String command){

        try {

            String final_user_input = command + "\n"; //WARNING: Without the \n user input isn't registered by the server console

            if (isRunning()) {
                os = process.getOutputStream();
                os.write(final_user_input.getBytes());
                os.flush();
            }
            os = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
