/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.events.MessageEvent;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.Message;
import com.osiris.jlib.logger.MessageFormatter;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;


/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class ConAutoPlugConsoleSend extends DefaultConnection {
    public static boolean isDebug;
    @Nullable
    private static BufferedWriter out;

    static {
        try {
            isDebug = new LoggerConfig().debug.asBoolean();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static final MessageEvent<Message> onMessageEvent = message -> {
        try {
            if (Objects.requireNonNull(message.getType()) == Message.Type.DEBUG) {
                if (isDebug)
                    send(MessageFormatter.formatForAnsiConsole(message));
            } else {
                send(MessageFormatter.formatForAnsiConsole(message));
            }
        } catch (Exception e) {
            AL.warn("Failed to send message to online console!", e);
        }
    };

    public ConAutoPlugConsoleSend() {
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
                if (GD.AP_LATEST_LOG.exists()) {
                    ReversedLinesFileReader object = new ReversedLinesFileReader(GD.FILE_OUT.toPath(), StandardCharsets.UTF_8);
                    String[] lines = new String[10];
                    for (int i = 0; i < 10; i++) { // Read last 10 lines
                        try {
                            lines[i] = object.readLine();
                        } catch (Exception ignored) {
                        }
                    }
                    for (int i = lines.length - 1; i >= 0; i--) {
                        if (lines[i] != null)
                            send(lines[i]);
                    }
                }

                if (!AL.actionsOnMessageEvent.contains(onMessageEvent))
                    AL.actionsOnMessageEvent.add(onMessageEvent);

            } catch (Exception e) {
                if (!Main.CON.isUserActive.get()) return false; // Ignore after logout
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
