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

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.List;


/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class ConOnlineConsoleSend extends SecondaryConnection {
    private static final boolean isDebug;
    @Nullable
    private static BufferedWriter out;
    public static final MessageEvent<Message> onMessageEvent = message -> {
        try {
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

    static {
        try {
            isDebug = new LoggerConfig().debug.asBoolean();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ConOnlineConsoleSend() {
        super((byte) 2);  // Each connection has its own auth_id.
    }

    public static void send(@NotNull String message) {
        try {
            if (out != null) {
                if (!message.contains(System.lineSeparator())) {
                    out.write(message + "\n");
                } else {
                    out.write(message);
                }
            }
            out.flush();
        } catch (Exception e) { // Do not use AL.warn because that would cause an infinite loop
        }
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().online_console.asBoolean()) {
            super.open();
            getSocket().setSoTimeout(0);
            out = new BufferedWriter(new OutputStreamWriter(getOut()));



            // Sending recent server log
            try {
                if (!GD.LOG_FILE.exists())
                    throw new FileNotFoundException("Latest log does not exist, not sending it to online-console: " + GD.LOG_FILE);

                List<String> lines = Files.readAllLines(GD.FILE_OUT.toPath());
                if (lines.size() > 500) {
                    lines = lines.subList(lines.size() - 499, lines.size());
                }
                if (!AL.actionsOnMessageEvent.contains(onMessageEvent))
                    AL.actionsOnMessageEvent.add(onMessageEvent);
                for (String line : lines) {
                    send(line);
                }
            } catch (Exception e) {
                AL.warn(e, "Error during recent log sending.");
            }

            if (!AL.actionsOnMessageEvent.contains(onMessageEvent))
                AL.actionsOnMessageEvent.add(onMessageEvent);

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
            AL.actionsOnMessageEvent.remove(onMessageEvent);
        } catch (Exception ignored) {
        }

        try {
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }
    }
}
