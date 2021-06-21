/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.client.utils.NonBlockingPipedInputStream;
import com.osiris.autoplug.core.events.MessageEvent;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.logger.Message;
import com.osiris.autoplug.core.logger.MessageFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;


/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class OnlineConsoleSendConnection extends SecondaryConnection {
    @Nullable
    private static BufferedWriter bw;
    public static final NonBlockingPipedInputStream.WriteLineEvent<String> actionOnServerLineWriteEvent = line -> {
        try {
            send(line);
        } catch (Exception e) {
            AL.warn("Failed to send message to online console!", e);
        }
    };
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

    public OnlineConsoleSendConnection() {
        super((byte) 2);  // Each connection has its own auth_id.
    }

    public static synchronized void send(@NotNull String message) throws Exception {
        if (bw != null) {
            if (!message.contains(System.lineSeparator())) {
                bw.write(message + System.lineSeparator());
            } else {
                bw.write(message);
            }
            bw.flush();
        }
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().online_console_send.asBoolean()) {
            super.open();
            if (bw == null) {
                getSocket().setSoTimeout(0);
                bw = new BufferedWriter(new OutputStreamWriter(getOut()));
                while (Server.NB_SERVER_IN != null && !Server.NB_SERVER_IN.actionsOnWriteLineEvent.contains(actionOnServerLineWriteEvent))
                    Server.NB_SERVER_IN.actionsOnWriteLineEvent.add(actionOnServerLineWriteEvent);

                AL.actionsOnMessageEvent.add(actionOnAutoPlugMessageEvent);
            }
            AL.debug(this.getClass(), "Online-Console-SEND connected.");

        } else {
            AL.debug(this.getClass(), "Online-Console-SEND functionality is disabled.");
            return false;
        }
        return true;
    }

    @Override
    public void close() throws IOException {

        try {
            if (Server.NB_SERVER_IN != null)
                Server.NB_SERVER_IN.actionsOnWriteLineEvent.remove(actionOnServerLineWriteEvent);
        } catch (Exception ignored) {
        }

        try {
            AL.actionsOnMessageEvent.remove(actionOnAutoPlugMessageEvent);
        } catch (Exception ignored) {
        }

        try {
            if (bw != null)
                bw.close();
        } catch (Exception e) {
            AL.warn("Failed to close writer.", e);
        }
        bw = null;

        try {
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }
    }
}
