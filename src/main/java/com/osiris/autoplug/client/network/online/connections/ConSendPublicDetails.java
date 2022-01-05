/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.client.utils.MineStat;
import com.osiris.autoplug.client.utils.UFDataOut;
import com.osiris.autoplug.core.logger.AL;

import java.io.IOException;


/**
 * Sends public details to AutoPlug-Web like
 * if the server is running/online, or the player count.
 * This connection should always stay active.
 */
public class ConSendPublicDetails extends SecondaryConnection {
    public String host = "127.0.0.1"; // instead of localhost, use directly the resolved loop-back address

    public boolean isRunning;
    public MineStat mineStat;
    public String version;
    public int currentPlayers;
    public int maxPlayers;
    private Thread thread;

    public ConSendPublicDetails() {
        super((byte) 4);  // Each connection has its own auth_id.
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().send_public_details.asBoolean()) {
            super.open();
            getSocket().setSoTimeout(0);
            UFDataOut dos = new UFDataOut(getOut());
            host = new WebConfig().send_server_status_ip.asString();
            thread = new Thread(() -> {
                try {
                    while (true) {
                        // MC server related info:
                        mineStat = new MineStat(host, Server.PORT);
                        isRunning = mineStat.isServerUp();
                        version = mineStat.getVersion();
                        if (version != null)
                            version = version.replaceAll("[a-zA-Z]", "").trim();
                        else
                            version = "-";
                        currentPlayers = mineStat.getCurrentPlayers();
                        maxPlayers = mineStat.getMaximumPlayers();

                        dos.writeBoolean(isRunning);
                        dos.writeLine(version);
                        dos.writeInt(currentPlayers);
                        dos.writeInt(maxPlayers);
                        Thread.sleep(5000);
                    }
                } catch (Exception e) {
                    AL.warn(e);
                }
            });
            thread.start();
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
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }

        try {
            if (thread != null && !thread.isInterrupted()) thread.interrupt();
        } catch (Exception e) {
            AL.warn("Failed to stop thread.", e);
        }
        thread = null;
    }
}
