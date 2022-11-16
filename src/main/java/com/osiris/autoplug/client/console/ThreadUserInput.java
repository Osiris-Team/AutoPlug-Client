/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.console;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleSend;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.LogFileWriter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;

import static com.osiris.betterthread.Constants.TERMINAL;

public class ThreadUserInput extends Thread {

    public ThreadUserInput() {
        setName("AutoPlug-UserInputListenerThread");
    }

    @Override
    public void run() {
        super.run();
        try {
            //Scanner scanner = new Scanner(System.in); // Old
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(TERMINAL)
                    .build();

            while (true) {
                String user_input = null;
                try {
                    user_input = lineReader.readLine();

                    // Send to online console
                    if (ConMain.CON_CONSOLE_SEND != null && ConMain.CON_CONSOLE_SEND.isConnected())
                        try {
                            ConOnlineConsoleSend.send(user_input);
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

                    } else
                        Server.submitCommand(user_input);

                } catch (UserInterruptException e) {
                    // Ignore
                } catch (EndOfFileException e) {
                    return;
                }
            }
        } catch (Exception e) {
            AL.warn(e);
        }
    }
}
