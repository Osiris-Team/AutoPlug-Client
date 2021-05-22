/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.console;

import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.network.online.MainConnection;
import com.osiris.autoplug.client.network.online.connections.OnlineConsoleSendConnection;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.logger.LogFileWriter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

import static com.osiris.autoplug.core.logger.AL.info;
import static com.osiris.betterthread.Constants.TERMINAL;

public class UserInput {
    public static Thread inputListenerThread;

    public static void keyboard() throws Exception {
        //New thread for user input
        if (inputListenerThread == null) {
            inputListenerThread = new Thread(() -> {
                try {
                    //Scanner scanner = new Scanner(System.in); // Old
                    LineReader lineReader = LineReaderBuilder.builder()
                            .terminal(TERMINAL)
                            .build();

                    while (true) {

                        // Get user input
                        String user_input = lineReader.readLine(); // scanner.nextLine(); // Old


                        // Send to online console
                        if (MainConnection.CON_CONSOLE_SEND.isConnected())
                            try {
                                OnlineConsoleSendConnection.send(user_input);
                            } catch (Exception e) {
                                AL.warn(e);
                            }


                        //Check if user input is autoplug command or not
                        if (AutoPlugConsole.executeCommand(user_input)) {

                            //Do nothing else if it is a client command, just save it to log file
                            try {
                                LogFileWriter.writeToLog(user_input);
                            } catch (Exception e) {
                                AL.warn(e, "Failed to write command to log file.");
                            }

                        } else if (Server.isRunning()) {
                            Server.submitCommand(user_input);
                        } else {
                            info("Enter .help for all available server!");
                        }

                    }
                } catch (Exception e) {
                    AL.warn(e);
                }
            });
            inputListenerThread.setName("InputListenerThread");
            inputListenerThread.start();
        }


    }

}
