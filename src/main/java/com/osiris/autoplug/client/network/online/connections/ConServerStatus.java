/*
 * Copyright (c) 2021 Osiris-Team.
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
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.Nullable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.DataOutputStream;
import java.io.IOException;


/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class ConServerStatus extends SecondaryConnection {
    public String host = "localhost";
    public boolean isRunning;
    public String strippedMotd;
    public String version;
    public int currentPlayers;
    public int maxPlayers;
    public String cpuSpeed;
    public String cpuMaxSpeed;
    public String memAvailable;
    public String memUsed;
    public String memTotal;

    @Nullable
    private DataOutputStream dos;
    private Thread thread;


    public ConServerStatus() {
        super((byte) 4);  // Each connection has its own auth_id.
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().send_server_status.asBoolean()) {
            super.open();
            if (dos == null) {
                getSocket().setSoTimeout(0);
                dos = getDataOut();
                int oneGigaByteInBytes = 1073741824;
                int oneGigaHertzInHertz = 1000000000;
                SystemInfo si = new SystemInfo();

                thread = new Thread(() -> {
                    try {
                        while (true) {
                            MineStat mineStat = new MineStat(host, Server.PORT);
                            dos.writeBoolean((isRunning = mineStat.isServerUp()));
                            dos.writeUTF((strippedMotd = "" + mineStat.getStrippedMotd()));
                            dos.writeUTF((version = "" + mineStat.getVersion()));
                            dos.writeInt((currentPlayers = mineStat.getCurrentPlayers()));
                            dos.writeInt((maxPlayers = mineStat.getMaximumPlayers()));

                            // Send hardware info
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
                                dos.writeUTF((cpuSpeed = "" + currentFrq / oneGigaHertzInHertz));
                                dos.writeUTF((cpuMaxSpeed = "" + cpu.getMaxFreq() / oneGigaHertzInHertz));
                            } else {
                                dos.writeUTF("0");
                                dos.writeUTF("0");
                            }


                            if (memory != null) {
                                dos.writeUTF((memAvailable = "" + memory.getAvailable() / oneGigaByteInBytes));
                                dos.writeUTF((memUsed = "" + (memory.getTotal() - memory.getAvailable()) / oneGigaByteInBytes));
                                dos.writeUTF((memTotal = "" + memory.getTotal() / oneGigaByteInBytes));
                            } else {
                                dos.writeUTF("0");
                                dos.writeUTF("0");
                                dos.writeUTF("0");
                            }

                            Thread.sleep(5000);
                        }
                    } catch (Exception e) {
                        AL.warn(e);
                    }
                });
                thread.start();
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
            if (dos != null)
                dos.close();
        } catch (Exception e) {
            AL.warn("Failed to close writer.", e);
        }
        dos = null;

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
