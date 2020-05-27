/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.server;

import com.osiris.autoplug.client.utils.AutoPlugLogger;

import java.io.IOException;
import java.util.Scanner;

public class UserInput {

    private static Scanner scanner = new Scanner(System.in);

    public static void keyboard(){
        //New thread for user input
        new Thread(new Runnable() {
            public void run() {
                while (true) {

                    String user_input = scanner.nextLine();
                    if (ClientCommands.isCommand(user_input)){
                        //Do nothing else if it is a client command
                    } else if (Server.isRunning()){
                        Server.submitServerCommand(user_input);
                    } else{
                        AutoPlugLogger logger = new AutoPlugLogger();
                        logger.global_info(" Enter .help for all available commands!");
                    }

                }

            }
        }).start();

    }

}
