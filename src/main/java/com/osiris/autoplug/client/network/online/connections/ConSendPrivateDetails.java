package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.jlib.logger.AL;
import io.netty.buffer.ByteBuf;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.concurrent.TimeUnit;

public class ConSendPrivateDetails extends DefaultConnection {
    public float cpuSpeed;
    public float cpuMaxSpeed;
    public byte cpuUsage;
    public float memAvailable;
    public float memUsed;
    public float memTotal;

    public ConSendPrivateDetails() {
        super((byte) 6);
    }

    @Override
    public boolean open() throws Exception {
        if (!new WebConfig().send_private_details.asBoolean()) {
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' not connected, because not enabled in the web-config.");
            return false;
        }

        super.open();

        float oneGigaByteInBytes = 1073741824.0f;
        float oneGigaHertzInHertz = 1000000000.0f;
        SystemInfo si = new SystemInfo();

        channel.eventLoop().scheduleAtFixedRate(() -> {
            if (!isConnected()) return;

            HardwareAbstractionLayer hal = si.getHardware();
            CentralProcessor cpu = hal.getProcessor();
            GlobalMemory memory = hal.getMemory();

            long currentFrq = 0;
            int i = 0;
            if (cpu != null) {
                for (long frq : cpu.getCurrentFreq()) {
                    currentFrq += frq;
                    i++;
                }
                if (i > 0) currentFrq /= i;
            }

            ByteBuf dos = channel.alloc().buffer();

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

            channel.writeAndFlush(dos);

        }, 0, 5, TimeUnit.SECONDS);

        AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' connected.");
        return true;
    }
}