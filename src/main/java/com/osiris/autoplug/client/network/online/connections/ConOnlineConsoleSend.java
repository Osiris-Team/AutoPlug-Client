/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.events.MessageEvent;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.logger.Message;
import com.osiris.autoplug.core.logger.MessageFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;


/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class ConOnlineConsoleSend extends SecondaryConnection {
    @Nullable
    private static BufferedWriter bw;
    public static final MessageEvent<Message> actionOnAutoPlugMessageEvent = message -> {
        try {
            boolean isDebug = new LoggerConfig().debug.asBoolean();
            switch (message.getType()) {
                case DEBUG:
                    if (isDebug)
                        send(MessageFormatter.formatForAnsiConsole(message));
                    break;
                default:
                    send(MessageFormatter.formatForAnsiConsole(message));
            }

        } catch (Exception e) {
            AL.warn("Failed to send message to online console!", e);
        }
    };

    public ConOnlineConsoleSend() {
        super((byte) 2);  // Each connection has its own auth_id.
    }

    public static synchronized void send(@NotNull String message) {
        try {
            if (bw != null) {
                if (!message.contains(System.lineSeparator())) {
                    bw.write(message + System.lineSeparator());
                } else {
                    bw.write(message);
                }
                bw.flush();
            }
        } catch (Exception e) { // Do not use AL.warn because that would cause an infinite loop
        }
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().online_console.asBoolean()) {
            super.open();
            getSocket().setSoTimeout(0);
            bw = new BufferedWriter(new OutputStreamWriter(getOut()));

            if (!AL.actionsOnMessageEvent.contains(actionOnAutoPlugMessageEvent))
                AL.actionsOnMessageEvent.add(actionOnAutoPlugMessageEvent);

            // Sending recent server log
            try {
                if (!GD.LOG_FILE.exists())
                    AL.warn("Failed to find latest server log file. Not sending recent server log to console.");
                else {
                    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(GD.FILE_OUT))) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            send(line);
                        }
                    }
                }
            } catch (Exception e) {
                AL.warn(e, "Error during recent log sending.");
            }
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' connected.");
            return true;
        } else {
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' not connected, because not enabled in the web-config.");
            return false;
        }
    }

    @Override
    public void close() throws IOException {

        try {
            AL.actionsOnMessageEvent.remove(actionOnAutoPlugMessageEvent);
        } catch (Exception ignored) {
        }

        try {
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }
    }
}
