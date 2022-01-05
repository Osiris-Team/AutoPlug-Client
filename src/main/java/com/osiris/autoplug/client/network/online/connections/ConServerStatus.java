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
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.IOException;


/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class ConServerStatus extends SecondaryConnection {
    public float cpuSpeed;
    public float cpuMaxSpeed;

    public String host = "127.0.0.1"; // instead of localhost, use directly the resolved loop-back address

    public boolean isRunning;
    public MineStat mineStat;
    public String strippedMotd;
    public String version;
    public int currentPlayers;
    public int maxPlayers;
    public float memAvailable;
    public float memUsed;
    public float memTotal;
    private Thread thread;

    public ConServerStatus() {
        super((byte) 4);  // Each connection has its own auth_id.
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().send_server_status.asBoolean()) {
            super.open();
            getSocket().setSoTimeout(0);
            UFDataOut dos = new UFDataOut(getOut());
            float oneGigaByteInBytes = 1073741824.0f;
            float oneGigaHertzInHertz = 1000000000.0f;
            SystemInfo si = new SystemInfo();
            host = new WebConfig().send_server_status_ip.asString();
            thread = new Thread(() -> {
                try {
                    while (true) {
                        // MC server related info:
                        mineStat = new MineStat(host, Server.PORT);
                        isRunning = mineStat.isServerUp();
                        strippedMotd = mineStat.getStrippedMotd();
                        if (strippedMotd == null)
                            strippedMotd = "-";
                        version = mineStat.getVersion();
                        if (version != null)
                            version = version.replaceAll("[a-zA-Z]", "");
                        else
                            version = "-";
                        currentPlayers = mineStat.getCurrentPlayers();
                        maxPlayers = mineStat.getMaximumPlayers();

                        dos.writeBoolean(isRunning);
                        dos.writeLine(strippedMotd);
                        dos.writeLine(version);
                        dos.writeInt(currentPlayers);
                        dos.writeInt(maxPlayers);

                        // Hardware info:
                        HardwareAbstractionLayer hal = si.getHardware();
                        CentralProcessor cpu = hal.getProcessor();
                        GlobalMemory memory = hal.getMemory();
                        // Calc average frequency in mhz
                        long currentFrq = 0;
                        int i = 0;
                        if (cpu != null) {
                            for (long frq :
                                    cpu.getCurrentFreq()) {
                                currentFrq = currentFrq + frq;
                                i++;
                            }
                            currentFrq = currentFrq / i;
                        }

                        if (cpu != null) {
                            dos.writeFloat((cpuSpeed = (currentFrq / oneGigaHertzInHertz)));
                            dos.writeFloat((cpuMaxSpeed = (cpu.getMaxFreq() / oneGigaHertzInHertz)));
                        } else {
                            dos.writeFloat(0);
                            dos.writeFloat(0);
                        }


                        if (memory != null) {
                            dos.writeFloat((memAvailable = (memory.getAvailable() / oneGigaByteInBytes)));
                            dos.writeFloat((memUsed = ((memory.getTotal() - memory.getAvailable()) / oneGigaByteInBytes)));
                            dos.writeFloat((memTotal = (memory.getTotal() / oneGigaByteInBytes)));
                        } else {
                            dos.writeFloat(0);
                            dos.writeFloat(0);
                            dos.writeFloat(0);
                        }

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
