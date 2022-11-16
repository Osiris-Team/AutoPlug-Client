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
import com.osiris.autoplug.client.utils.MineStat;
import com.osiris.autoplug.client.utils.io.UFDataOut;
import com.osiris.jlib.logger.AL;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


/**
 * Sends public details to AutoPlug-Web like
 * if the server is running/online, or the player count.
 * This connection should always stay active.
 */
public class ConSendPublicDetails extends SecondaryConnection {
    public String host = "127.0.0.1"; // instead of localhost, use directly the resolved loop-back address
    public int port = 0;
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
            WebConfig webConfig = new WebConfig();
            host = webConfig.send_server_status_ip.asString();
            if (webConfig.send_server_status_port.asString() == null) {
                try { // Find port of server
                    String portAsString = null;
                    try {
                        Properties properties = new Properties();
                        properties.load(new FileInputStream(GD.WORKING_DIR + "/server.properties"));
                        portAsString = properties.getProperty("server-port");
                    } catch (Exception ignored) {
                    }
                    // TODO detect port for proxies and other servers.
                    // For bungeecord the port is located inside config.yml.
                    // Problem with that file is that is has yml features unsupported by Yaml at the moment.
                    // Maybe make a more universal approach like getting the port after the server process was started.

                    if (portAsString != null) {
                        port = webConfig.send_server_status_port.setDefValues(portAsString).asInt();
                        webConfig.lockFile();
                        webConfig.save();
                        webConfig.unlockFile();
                    } else
                        throw new Exception("Failed to find the servers' port! Please set it manually inside the '/autoplug/web-config.yml'.");
                } catch (Exception e) {
                    AL.warn(e);
                }
            } else
                port = webConfig.send_server_status_port.asInt();
            thread = new Thread(() -> {
                try {
                    while (true) {
                        // MC server related info:
                        mineStat = new MineStat(host, port);
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
                    if (!ConMain.isUserActive.get()) return; // Ignore after logout
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
