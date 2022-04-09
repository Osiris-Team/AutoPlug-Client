/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.client.utils.io.UFDataOut;
import com.osiris.autoplug.core.logger.AL;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.IOException;


/**
 * Sends private details to AutoPlug-Web like
 * CPU speeds and memory used/total. Should only
 * be active when user is logged in.
 */
public class ConSendPrivateDetails extends SecondaryConnection {
    public float cpuSpeed;
    public float cpuMaxSpeed;
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
            thread = new Thread(() -> {
                try {
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
