/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.server;

import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Scanner;

import static com.osiris.autoplug.client.utils.AutoPlugLogger.error;
import static com.osiris.autoplug.client.utils.AutoPlugLogger.info;

public class UserInput {

    public UserInput(){
        AutoPlugLogger.newClassDebug("UserInput");
    }

    private static Scanner scanner = new Scanner(System.in);

    public static void keyboard(){
        //New thread for user input
        new Thread(new Runnable() {
            public void run() {
                while (true) {

                    String user_input = scanner.nextLine();
                    if (ClientCommands.isCommand(user_input)){
                        //Do nothing else if it is a client command, just save it to log file
                        try {
                            Files.write(GD.LATEST_LOG.toPath(), user_input.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                            error(e.getMessage(), "Failed to update latest log file.");
                        }

                    } else if (Server.isRunning()){
                        Server.submitServerCommand(user_input);
                    } else{
                        info("Enter .help for all available commands!");
                    }

                }

            }
        }).start();

    }

}
