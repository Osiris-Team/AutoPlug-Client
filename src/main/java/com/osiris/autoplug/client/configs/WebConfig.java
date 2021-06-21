/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class WebConfig extends DreamYaml {

    public DYModule online_console_send;
    public DYModule online_console_receive;

    public DYModule send_plugins_updater_results;
    public DYModule send_server_updater_results;
    public DYModule send_self_updater_results;


    public WebConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug-web-config.yml");

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

        online_console_send = put(name, "online-console", "send").setDefValues("false")
                .setComments("Sends the recent log messages (and future messages) to the Online-Console.",
                        "To have as little impact on your server as possible, this only happens when you are logged in.");
        online_console_receive = put(name, "online-console", "receive").setDefValues("false")
                .setComments("Receives messages from the Online-Console and executes them.");

        send_plugins_updater_results = put(name, "updater-results", "send-plugins-updaters-results").setDefValues("true")
                .setComments("Sends the plugins-updaters results to AutoPlug-Web.",
                        "By disabling this, you won't be able to see a summary of the updaters result online anymore.");
        send_server_updater_results = put(name, "updater-results", "send-server-updaters-results").setDefValues("true");
        send_self_updater_results = put(name, "updater-results", "send-self-updaters-results").setDefValues("true");

        save();
    }
}
