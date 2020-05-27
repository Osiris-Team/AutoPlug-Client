/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.server;

import com.osiris.autoplug.client.managers.BackupManager;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.Config;
import com.osiris.autoplug.client.utils.GD;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class Server {

    private static AutoPlugLogger logger = new AutoPlugLogger();

    private static Process process;

    private static OutputStream os;


    public static void start(){
        //Before starting make backups and check for updates
        try {
            if (isRunning()) {
                logger.global_warn(" Server already running!");
            } else{
                new BackupManager();
                new ServerUpdater();
                logger.global_info(" Starting server in 10sec...");
                Thread.sleep(7000);
                logger.global_info(" Starting server in 3sec...");
                Thread.sleep(1000);
                logger.global_info(" Starting server in 2sec...");
                Thread.sleep(1000);
                logger.global_info(" Starting server in 1sec...");
                Thread.sleep(1000);
                createProcess(GD.SERVER_PATH.toPath().toString());
                //synchronized (process){}
                createConsole();
            }


        }
        catch (NullPointerException | IOException | InterruptedException ex){
            ex.printStackTrace();
            logger.global_warn("[!] Could'nt find your server jar [!]");
            logger.global_warn("[!] Searched dir: "+GD.WORKING_DIR+" [!]");
            logger.global_warn("[!] Please check your config file [!]");
        }
    }

    public static void restart(){
        //Before starting make backups and check for updates
        logger.global_info(" Restarting server...");
        try {
            stop();
            start();
        }
        catch (Exception ex){ex.printStackTrace();}
    }

    public static void stop(){

        logger.global_info(" Stopping server...");
        try {
            String stop_command = "stop\n"; //WARNING: Without the \n user input isn't registered by the server console

            if (isRunning()) {
                os = process.getOutputStream();
                os.write(stop_command.getBytes());
                os.flush();
                logger.global_info(" Stop command executed!");
            } else{
                logger.global_warn(" Server is not running!");
            }

            while(isRunning()){
                Thread.sleep(1000);
            }
            logger.global_info(" Server was stopped!");
            logger.global_info(" To close AutoPlug(this console) enter .close or .kill");
            logger.global_info(" For all commands enter .help");


        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Error stopping server: "+e.getMessage()+" [!]");
        }



    }

    public static boolean kill(){

        logger.global_info(" Killing server!");
        try {

            if (isRunning()) {
                process.destroy();
            } else{
                logger.global_warn(" Server is not running!");
            }

            while(isRunning()){
                Thread.sleep(1000);
            }
            logger.global_info(" Server killed!");
            return true;

        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Error killing server: "+e.getMessage()+" [!]");
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

        for (int i = 0; i < Config.server_flags.size(); i++) {
            commands.add("-"+Config.server_flags.get(i));
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
