/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.protector;

import org.pcap4j.core.PcapPacket;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class User {
    public InetAddress ip;
    public List<PcapPacket> packets = new ArrayList<>();

    public User(InetAddress ip) {
        this.ip = ip;
    }
}
