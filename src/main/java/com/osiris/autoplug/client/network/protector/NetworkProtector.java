/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.protector;

import com.osiris.autoplug.core.logger.AL;
import com.sun.jna.Platform;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.*;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.util.NifSelector;

import java.io.IOException;

public class NetworkProtector {
    public static NetworkProtector GET = null;
    public final String COUNT_KEY = NetworkProtector.class.getName() + ".count";
    public final int COUNT = Integer.getInteger(COUNT_KEY, 5);
    public final String READ_TIMEOUT_KEY = NetworkProtector.class.getName() + ".readTimeout";
    public final int READ_TIMEOUT = Integer.getInteger(READ_TIMEOUT_KEY, 10); // [ms]
    public final String SNAPLEN_KEY = NetworkProtector.class.getName() + ".snaplen";
    public final int SNAPLEN = Integer.getInteger(SNAPLEN_KEY, 65536); // [bytes]
    public Thread LISTENER_THREAD;

    public NetworkProtector() throws PcapNativeException, NotOpenException {
        if (GET == null) GET = this;
        else return;

        String filter = ""; // TODO

        AL.info(COUNT_KEY + ": " + COUNT);
        AL.info(READ_TIMEOUT_KEY + ": " + READ_TIMEOUT);
        AL.info(SNAPLEN_KEY + ": " + SNAPLEN);
        AL.info("\n");

        PcapNetworkInterface nif;
        try {
            nif = new NifSelector().selectNetworkInterface();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (nif == null) {
            return;
        }

        AL.info(nif.getName() + "(" + nif.getDescription() + ")");

        final PcapHandle handle = nif.openLive(SNAPLEN, PromiscuousMode.PROMISCUOUS, READ_TIMEOUT);

        if (filter.length() != 0) {
            handle.setFilter(filter, BpfCompileMode.OPTIMIZE);
        }

        LISTENER_THREAD = new Thread(() -> {
            try {
                try {
                    handle.loop(COUNT, packet -> {
                        AL.info(packet.toString());
                    });
                } catch (InterruptedException e) {
                    AL.warn(e);
                }

                PcapStat ps = handle.getStats();
                AL.info("ps_recv: " + ps.getNumPacketsReceived());
                AL.info("ps_drop: " + ps.getNumPacketsDropped());
                AL.info("ps_ifdrop: " + ps.getNumPacketsDroppedByIf());
                if (Platform.isWindows()) {
                    AL.info("bs_capt: " + ps.getNumPacketsCaptured());
                }

                handle.close();
            } catch (Exception e) {
                AL.warn(e);
            }
        });
        LISTENER_THREAD.start();
    }
}
