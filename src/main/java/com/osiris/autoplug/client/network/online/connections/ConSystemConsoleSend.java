/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.terminal.AsyncTerminal;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Date;


/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class ConSystemConsoleSend extends SecondaryConnection {
    public static AsyncTerminal asyncTerminal;
    public static BufferedWriter asyncTerminalLogWriter;

    @Nullable
    private static BufferedWriter out;

    public ConSystemConsoleSend() {
        super((byte) 7);  // Each connection has its own auth_id.
    }

    public static void send(@NotNull String message) {
        try {
            asyncTerminalLogWriter.write(message + "\n");
            asyncTerminalLogWriter.flush();
        } catch (Exception e) {
            AL.warn("Failed to write to " + GD.SYSTEM_LATEST_LOG, e);
        }
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
        if (new WebConfig().online_system_console.asBoolean()) {
            super.open();
            getSocket().setSoTimeout(0);
            out = new BufferedWriter(new OutputStreamWriter(getOut()));

            if (asyncTerminal != null) asyncTerminal.close();
            if (asyncTerminalLogWriter != null) asyncTerminalLogWriter.close();
            if (GD.SYSTEM_LATEST_LOG.exists()) GD.SYSTEM_LATEST_LOG.delete();
            GD.SYSTEM_LATEST_LOG.getParentFile().mkdirs();
            GD.SYSTEM_LATEST_LOG.createNewFile();
            asyncTerminalLogWriter = new BufferedWriter(new FileWriter(GD.SYSTEM_LATEST_LOG));
            send("Connected to AutoPlug-Web at " + new Date());
            send("Current working directory: " + GD.WORKING_DIR);
            asyncTerminal = new AsyncTerminal(null, line -> {
                try {
                    send(line);
                } catch (Exception e) {
                    if (!ConMain.isUserActive.get()) return; // Ignore after logout
                    AL.warn("Failed to send message to online console!", e);
                }
            }, errLine -> {
                try {
                    send(errLine);
                } catch (Exception e) {
                    if (!ConMain.isUserActive.get()) return; // Ignore after logout
                    AL.warn("Failed to send message to online console!", e);
                }
            });

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
            if (asyncTerminal != null) asyncTerminal.close();
            if (asyncTerminalLogWriter != null) asyncTerminalLogWriter.close();
        } catch (Exception ignored) {
        }

        try {
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }
    }
}
