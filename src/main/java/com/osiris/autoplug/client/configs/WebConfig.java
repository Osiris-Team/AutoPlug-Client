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

public class WebConfig extends Yaml {

    public YamlSection online_console;
    public YamlSection online_system_console;

    public YamlSection send_plugins_updater_results;
    public YamlSection send_server_updater_results;
    public YamlSection send_self_updater_results;

    public YamlSection send_public_details;
    public YamlSection send_private_details;
    public YamlSection send_server_status_ip;
    public YamlSection send_server_status_port;
    public YamlSection file_manager;


    public WebConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException, YamlWriterException {
        super(System.getProperty("user.dir") + "/autoplug/web.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################",
                "    ___       __       ___  __",
                "   / _ |__ __/ /____  / _ \\/ /_ _____ _",
                "  / __ / // / __/ _ \\/ ___/ / // / _ `/",
                " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /",
                "                                /___/ Web-Config",
                "Thank you for using AutoPlug!",
                "You can find detailed installation instructions here: https://autoplug.one/installer",
                "If there are any questions or you just want chat, join our Discord: https://discord.gg/GGNmtCC",
                " ",
                "#######################################################################################################################",
                "Note: Changes to this file probably require you to enter '.con reload' to have affect.");

        online_console = put(name, "online-console").setDefValues("true")
                .setComments("Sends recent log messages (and future messages) to the Online-Console and can receive commands from it.",
                        "To have as little impact on your server as possible, this only happens when you are logged in.");

        online_system_console = put(name, "online-system-console").setDefValues("true")
                .setComments("Opens the standard system-specific terminal (powershell on Windows, bash/sh on Linux) in the current working directory.",
                        "Makes it possible to interact with it from AutoPlug-Web.",
                        "To have as little impact on your server as possible, this only happens when you are logged in.");

        send_plugins_updater_results = put(name, "updater-results", "send-plugins-updaters-results").setDefValues("true")
                .setComments("Sends the plugins-updaters results to AutoPlug-Web.",
                        "By disabling this, you won't be able to see a summary of the updaters result online anymore.");
        send_server_updater_results = put(name, "updater-results", "send-server-updaters-results").setDefValues("true");
        send_self_updater_results = put(name, "updater-results", "send-self-updaters-results").setDefValues("true");


        send_public_details = put(name, "send-details", "public").setDefValues("true").setComments(
                "Sent information:",
                "- Server status (is it running/online or not)",
                "- Player count",
                "This connection stays always active.");
        send_private_details = put(name, "send-details", "private").setDefValues("true").setComments(
                "Sent information:",
                "- CPU maximum and current speeds",
                "- Memory maximum size and currently used size",
                "This connection is only active when logged in.");
        send_server_status_ip = put(name, "send-details", "ip").setDefValues("127.0.0.1").setComments(
                "The ip-address from where to retrieve server details, like MOTD, player count etc.");
        send_server_status_port = put(name, "send-details", "port").setComments(
                "The port from where to retrieve server details. Gets automatically detected on start. If that fails you get a warning.");

        file_manager = put(name, "file-manager").setDefValues("true").setComments("Establishes the connection, once you are logged in to AutoPlug-Web.",
                "Enables you to manage this servers files from AutoPlugs' web panel.");

        save();
        unlockFile();
    }
}
