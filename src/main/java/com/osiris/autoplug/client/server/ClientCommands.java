/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.server;

import com.osiris.autoplug.client.scheduler.TaskScheduler;
import com.osiris.autoplug.client.utils.AutoPlugLogger;

/**
 * Listens for input started with .
 * List the commands with .help
 */
public final class ClientCommands {

    public ClientCommands(){
        AutoPlugLogger.newClassDebug("ClientCommands");
    }

    public static boolean isCommand(String command) {

        String first = ".";
        try{
            first = Character.toString(command.charAt(0));
        }catch (Exception e){
            AutoPlugLogger.info(" Command not found! Enter .help for all available commands!");
        }

        if (first.equals(".")){
            try{

                if (command.equals(".help") || command.equals(".h")){
                    AutoPlugLogger.info(" ");
                    AutoPlugLogger.info(" [All AutoPlug-Console commands]");
                    AutoPlugLogger.info(" .help - prints out this (if you didn't notice)");
                    AutoPlugLogger.info(" .start - starts the server (Shortcut: .s)");
                    AutoPlugLogger.info(" .restart - restarts the server (Shortcut: .r)");
                    AutoPlugLogger.info(" .stop - stops and saves the server (Shortcut: .st)");
                    AutoPlugLogger.info(" .close - stops, saves your server and closes AutoPlug safely (Shortcut: .c)");
                    AutoPlugLogger.info(" .kill - kills the server without saving and closes AutoPlug (Shortcut: .k)");
                    AutoPlugLogger.info(" ");
                    return true;
                }
                else if (command.equals(".start") || command.equals(".s")){
                    Server.start();
                    return true;
                }

                else if (command.equals(".restart") || command.equals(".r")){
                    Server.restart();
                    return true;
                }

                else if (command.equals(".stop") || command.equals(".st")){
                    Server.stop();
                    return true;
                }

                else if (command.equals(".close") || command.equals(".c")){
                    Server.stop();
                    while(Server.isRunning()){
                        Thread.sleep(1000);
                    }
                    TaskScheduler.safeShutdown();
                    AutoPlugLogger.info(" See you soon!");
                    AutoPlugLogger.stop();
                    Thread.sleep(1000);
                    System.exit(0);
                    return true;
                }

                else if (command.equals(".kill") || command.equals(".k")){
                    Server.kill();
                    TaskScheduler.safeShutdown();
                    AutoPlugLogger.info(" Killing AutoPlug-Client! Ahhhh!");
                    AutoPlugLogger.info(" Achievement unlocked: double kill!");
                    AutoPlugLogger.stop();
                    Thread.sleep(1000);
                    System.exit(0);
                    return true;
                }

                else{
                    AutoPlugLogger.info(" Command not found! Enter .help for all available commands!");
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
