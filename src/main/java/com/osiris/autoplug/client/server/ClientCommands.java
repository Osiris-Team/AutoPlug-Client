/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.server;

import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.core.logger.AL;

/**
 * Listens for input started with .
 * List the server with .help
 */
public final class ClientCommands {

    public static boolean isCommand(String command) {

        String first = ".";
        try{
            first = Character.toString(command.charAt(0));
        }catch (Exception e){
            AL.info("Command not found! Enter .help for all available server!");
        }

        if (first.equals(".")){
            try{

                if (command.equals(".help") || command.equals(".h")){
                    AL.info("");
                    AL.info("[All AutoPlug-Console server]");
                    AL.info(".help - prints out this (if you didn't notice)");
                    AL.info(".start - starts the server (Shortcut: .s)");
                    AL.info(".restart - restarts the server (Shortcut: .r)");
                    AL.info(".stop - stops and saves the server (Shortcut: .st)");
                    AL.info(".close - stops, saves your server and closes AutoPlug safely (Shortcut: .c)");
                    AL.info(".kill - kills the server without saving and closes AutoPlug (Shortcut: .k)");
                    AL.info("");
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
                    // All the stuff that needs to be done before shutdown is done by the ShutdownHook.
                    System.exit(0);
                    return true;
                }

                else if (command.equals(".kill") || command.equals(".k")){
                    Server.kill();
                    AL.info("Killing AutoPlug-Client and MC-Server! Ahhhh!");
                    AL.info("Achievement unlocked: double kill!");
                    System.exit(0);
                    return true;
                }

                else{
                    AL.info("Command not found! Enter .help for all available server!");
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
