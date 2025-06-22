/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.utils.io.UFDataOut;
import com.osiris.jlib.logger.AL;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;


/**
 * Sends private details to AutoPlug-Web like
 * CPU speeds and memory used/total. Should only
 * be active when user is logged in.
 */
public class ConSendPrivateDetails extends DefaultConnection {
    public float cpuSpeed;
    public float cpuMaxSpeed;
    /**
     * Value between 0 and 100%.
     */
    public byte cpuUsage;
    public float memAvailable;
    public float memUsed;
    public float memTotal;
    private Thread thread;

    public ConSendPrivateDetails() {
        super((byte) 6);  // Each connection has its own auth_id.
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().send_private_details.asBoolean()) {
            super.open();
            getSocket().setSoTimeout(0);
            UFDataOut dos = new UFDataOut(getOut());
            float oneGigaByteInBytes = 1073741824.0f;
            float oneGigaHertzInHertz = 1000000000.0f;
            SystemInfo si = new SystemInfo();
            setAndStartAsync(() -> {
                while (true) {
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
                        cpuMaxSpeed = (cpu.getMaxFreq() / oneGigaHertzInHertz);
                        dos.writeFloat(Math.max(cpuMaxSpeed, cpuSpeed)); // Support for overclocking
                        dos.writeByte((cpuUsage = (byte) Math.round(cpu.getSystemCpuLoad(1000) * 100)));
                    } else {
                        dos.writeFloat(0);
                        dos.writeFloat(0);
                        dos.writeByte((byte) 0);
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
            });
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' connected.");
            return true;
        } else {
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' not connected, because not enabled in the web-config.");
            return false;
        }
    }
}
