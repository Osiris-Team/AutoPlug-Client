/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.server;

import com.osiris.autoplug.client.server.Server;
import com.osiris.autoplug.client.utils.AutoPlugLogger;

import java.io.IOException;
import java.util.Scanner;

/**
 * Listens for input started with .
 * List the commands with .help
 */
public final class ClientCommands {

    private static AutoPlugLogger logger = new AutoPlugLogger();

    public static boolean isCommand(String command) {
        String first = Character.toString(command.charAt(0));
        if (first.equals(".")){
            try{

                switch (command) {
                    case ".help":
                        logger.global_info(" ");
                        logger.global_info(" [All AutoPlug-Console commands]");
                        logger.global_info(" .help - prints out this (if you didn't notice)");
                        logger.global_info(" .start - starts the server (Shortcut: .s)");
                        logger.global_info(" .restart - restarts the server (Shortcut: .r)");
                        logger.global_info(" .stop - stops and saves the server (Shortcut: .st)");
                        logger.global_info(" .close - stops, saves your server and closes AutoPlug safely (Shortcut: .c)");
                        logger.global_info(" .kill - kills the server without saving and closes AutoPlug (Shortcut: .k)");
                        logger.global_info(" .morerandomcommandscomingsoonmaboi");
                        logger.global_info(" ");
                        return true;
                    case ".h":
                        logger.global_info(" ");
                        logger.global_info(" [All AutoPlug-Console commands]");
                        logger.global_info(" .help - prints out this (if you didn't notice)");
                        logger.global_info(" .start - starts the server (Shortcut: .s)");
                        logger.global_info(" .restart - restarts the server (Shortcut: .r)");
                        logger.global_info(" .stop - stops and saves the server (Shortcut: .st)");
                        logger.global_info(" .close - stops, saves your server and closes AutoPlug safely (Shortcut: .c)");
                        logger.global_info(" .kill - kills the server without saving and closes AutoPlug (Shortcut: .k)");
                        logger.global_info(" ");
                        return true;

                    case ".start":
                        Server.start();
                        return true;
                    case ".s":
                        Server.start();
                        return true;

                    case ".restart":
                        Server.restart();
                        return true;
                    case ".r":
                        Server.restart();
                        return true;

                    case ".stop":
                        Server.stop();
                        return true;
                    case ".st":
                        Server.stop();
                        return true;

                    case ".close":
                        Server.stop();
                        while(Server.isRunning()){
                            Thread.sleep(1000);
                        }
                        logger.global_info(" See you soon!");
                        Thread.sleep(3000);
                        System.exit(0);
                        return true;
                    case ".c":
                        Server.stop();
                        while(Server.isRunning()){
                            Thread.sleep(1000);
                        }
                        logger.global_info(" See you soon!");
                        Thread.sleep(3000);
                        System.exit(0);
                        return true;

                    case ".kill":
                        Server.kill();
                        logger.global_info(" Killing AutoPlug-Client! Ahhhh!");
                        logger.global_info(" Achievement unlocked: double kill!");
                        System.exit(0);
                        return true;
                    case ".k":
                        Server.kill();
                        logger.global_info(" Killing AutoPlug-Client! Ahhhh!");
                        logger.global_info(" Achievement unlocked: double kill!");
                        System.exit(0);
                        return true;

                    default:
                        logger.global_info(" Command not found! Enter .help for all available commands!");
                        return false;
                }

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else{
            return false;
        }
    }


}
