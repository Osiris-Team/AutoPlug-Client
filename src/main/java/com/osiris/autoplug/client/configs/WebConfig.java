/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class WebConfig extends DreamYaml {

    public DYModule online_console;

    public DYModule send_plugins_updater_results;
    public DYModule send_server_updater_results;
    public DYModule send_self_updater_results;

    public DYModule send_server_status;
    public DYModule file_manager;

    public WebConfig() throws NotLoadedException, DYWriterException, IOException, IllegalKeyException, DuplicateKeyException, DYReaderException, IllegalListException {
        this(ConfigPreset.DEFAULT);
    }

    public WebConfig(ConfigPreset preset) throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug/web-config.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Web-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        online_console = put(name, "online-console").setDefValues("false")
                .setComments("Sends recent log messages (and future messages) to the Online-Console and can receive commands from it.",
                        "To have as little impact on your server as possible, this only happens when you are logged in.");
        if (preset.equals(ConfigPreset.FAST)) {
            online_console.setDefValues("true");
        }

        send_plugins_updater_results = put(name, "updater-results", "send-plugins-updaters-results").setDefValues("true")
                .setComments("Sends the plugins-updaters results to AutoPlug-Web.",
                        "By disabling this, you won't be able to see a summary of the updaters result online anymore.");
        send_server_updater_results = put(name, "updater-results", "send-server-updaters-results").setDefValues("true");
        send_self_updater_results = put(name, "updater-results", "send-self-updaters-results").setDefValues("true");


        send_server_status = put(name, "send-server-status").setDefValues("true").setComments(
                "Establishes the connection, once you are logged in to AutoPlug-Web.",
                "Sends following information to the web-server:",
                "Alive status of server; servers MOTD and player count; CPU frequencies and used/total memory sizes.",
                "Restart AutoPlug for this change to take affect.");

        file_manager = put(name, "file-manager").setDefValues("true").setComments("Establishes the connection, once you are logged in to AutoPlug-Web.",
                "Enables you to manage this servers files from AutoPlugs' web panel.");

        save();
        unlockFile();
    }
}
