/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class NetworkProtectorConfig extends Yaml {

    public YamlSection enable;
    public YamlSection bpf_filter;
    public YamlSection network_interface;

    public NetworkProtectorConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/network-protector.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Network-Protector-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################\n");

        enable = put(name, "enable").setDefValues("false").setComments(
                "Protect your network from DoS/DDoS attacks and prevent it from crashing.",
                "IMPORTANT: For this to work you must install either Npcap (if on Windows) or libpcap (if on Unix system, like Linux or MacOS).",
                "- Npcap: https://npcap.com/#download (CHECK THE '... in WinPCap API-compatible Mode' BOX) ",
                "- libpcap: (probably already installed on your system) https://www.google.com/search?q=install+libpcap+on");
        bpf_filter = put(name, "bpf-filter").setDefValues("ip and not net localnet").setComments(
                "Berkeley Packet Filter, to ignore specific packets from being checked by AutoPlug and thus increase performance drastically.",
                "Example: 'ip and not net localnet' to select IPv4 traffic neither sourced from nor destined for local hosts (if you gateway to one other net, this stuff should never make it onto your local net).",
                "This filter gets optimized. More details here:",
                "https://wikipedia.org/wiki/Berkeley_Packet_Filter",
                "https://www.tcpdump.org/manpages/pcap-filter.7.html");
        network_interface = put(name, "network-interface").setDefValues("localhost"); // Comment gets set by network protector

        save();
        unlockFile();
    }


}
