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
import com.osiris.autoplug.core.logger.LogFileWriter;

import java.util.Scanner;

import static com.osiris.autoplug.core.logger.AL.info;

public class UserInput {
    public static Thread inputListenerThread;

    public static void keyboard() throws Exception{
        //New thread for user input
        if (inputListenerThread == null){
            inputListenerThread = new Thread(()->{

                Scanner scanner = new Scanner(System.in);

                while (true) {

                    // Get user input
                    String user_input = scanner.nextLine();

                    // TODO WORK IN PROGRESS
                    // Send to online console
                    /*
                    if (MainConnection.CON_CONSOLE.isConnected())
                        try{
                            MainConnection.CON_CONSOLE.send(user_input);
                        } catch (Exception e) {
                            AL.warn(e);
                        }

                     */


                    //Check if user input is autoplug command or not
                    if (ClientCommands.isCommand(user_input)){

                        //Do nothing else if it is a client command, just save it to log file
                        try {
                            LogFileWriter.writeToLog(user_input);
                        } catch (Exception e) {
                            AL.warn(e,"Failed to write command to log file.");
                        }

                    } else if (Server.isRunning()){
                        Server.submitCommand(user_input);
                    } else{
                        info("Enter .help for all available server!");
                    }

                }

            });
            inputListenerThread.setName("InputListenerThread");
            inputListenerThread.start();
        }


    }

}
