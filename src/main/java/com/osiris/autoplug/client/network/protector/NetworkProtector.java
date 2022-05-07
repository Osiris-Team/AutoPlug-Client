/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.protector;

import com.osiris.autoplug.client.configs.NetworkProtectorConfig;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.exceptions.*;
import com.sun.jna.Platform;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.*;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.util.LinkLayerAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class NetworkProtector {
    public static NetworkProtector GET = null;


    public final String COUNT_KEY = NetworkProtector.class.getName() + ".count";
    public final int COUNT = Integer.getInteger(COUNT_KEY, -1);
    public final String READ_TIMEOUT_KEY = NetworkProtector.class.getName() + ".readTimeout";
    public final int READ_TIMEOUT = Integer.getInteger(READ_TIMEOUT_KEY, 10); // [ms]
    public final String SNAPLEN_KEY = NetworkProtector.class.getName() + ".snaplen";
    public final int SNAPLEN = Integer.getInteger(SNAPLEN_KEY, 65536); // [bytes]
    public Thread LISTENER_THREAD;
    private final List<User> recentUsers = new ArrayList<>();
    public NetworkProtector() throws PcapNativeException, NotOpenException, IOException, NotLoadedException, YamlReaderException, YamlWriterException, IllegalKeyException, DuplicateKeyException, IllegalListException {
        if (GET == null) GET = this;
        else return;
        NetworkProtectorConfig config = new NetworkProtectorConfig();
        String filter = config.bpf_filter.asString();

        AL.debug(this.getClass(), "Max packet listen count: " + COUNT);
        AL.debug(this.getClass(), "Read timeout in ms: " + READ_TIMEOUT);
        AL.debug(this.getClass(), "Max SNAP length: " + SNAPLEN);
        List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();


        // Update config network interface comment:
        StringBuilder nifListAsString = new StringBuilder(200);
        int nifIdx = 0;
        for (PcapNetworkInterface nif : allDevs) {
            nifListAsString.append("NIF[").append(nifIdx).append("]: ").append(nif.getName()).append("\n");

            if (nif.getDescription() != null) {
                nifListAsString.append("      : description: ").append(nif.getDescription()).append("\n");
            }

            for (LinkLayerAddress addr : nif.getLinkLayerAddresses()) {
                nifListAsString.append("      : link layer address: ").append(addr).append("\n");
            }

            for (PcapAddress addr : nif.getAddresses()) {
                nifListAsString.append("      : address: ").append(addr.getAddress()).append("\n");
            }
            nifListAsString.append("      : isUp/isRunning/isLocal: ").append(nif.isUp()).append(nif.isRunning()).append(nif.isLocal()).append("\n");
            nifIdx++;
        }
        nifListAsString.append("\n");
        config.network_interface.setComments(
                "Do not touch this, unless you really know what you are doing.",
                "If left empty, localhost/loopback will be used as network interface.",
                "The target network interface to listen traffic/packets from.",
                "Enter the id/number of the interface below.",
                "Available options:",
                nifListAsString.toString());
        config.save();

        // Determine network interface:
        PcapNetworkInterface nif = null;
        if (config.network_interface.asString() == null)
            for (PcapNetworkInterface n :
                    allDevs) {
                if (n.isLoopBack()) {
                    nif = n;
                    break;
                }
            }
        else nif = allDevs.get(config.network_interface.asInt());
        if (nif == null) throw new RuntimeException("Failed to find a loopback network interface!");

        // Generate filter, that excludes all packets sent from this network, and
        // only listens for received packets from outside the network
        if (filter == null) {
            filter = "";
            for (PcapNetworkInterface n :
                    allDevs) {
                for (PcapAddress address :
                        n.getAddresses()) {
                    filter += "not (src net " + address.getAddress().getHostAddress() + ") and ";
                }
            }
            // Exclude localhost ip4/6 manually, since those addresses usually don't get detected by pcap
            filter += "not (src net 127.0.0.1) and not (src net ::1)";
            AL.debug(this.getClass(), "Generated filter: " + filter);
        }

        AL.debug(this.getClass(), "Initialised on: " + nif.getName() + "(" + nif.getDescription() + ")");
        final PcapHandle handle = nif.openLive(SNAPLEN, PromiscuousMode.PROMISCUOUS, READ_TIMEOUT);
        if (filter.length() != 0) {
            handle.setFilter(filter, BpfCompileMode.OPTIMIZE);
        }

        LISTENER_THREAD = new Thread(() -> {
            try {
                try {
                    handle.loop(COUNT, packet -> {
                        packet.getTimestamp()
                        AL.info(packet.toString());
                    }, Executors.newCachedThreadPool());
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

    private synchronized void addPacket(PcapPacket packet) {
        for (User u :
                recentUsers) {
            if (u.ip.equals(packet.toString()))
        }
    }
}
