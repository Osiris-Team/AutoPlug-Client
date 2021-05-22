/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;

public class WebConfig extends DreamYaml {

    public DYModule online_console_send;
    public DYModule online_console_receive;


    public WebConfig() {
        super(System.getProperty("user.dir") + "/autoplug-web-config.yml");
        try {
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment(
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

            online_console_send = add(name, "online-console", "send").setDefValue("false")
                    .setComments("Sends the recent log messages (and future messages) to the Online-Console.",
                            "To have as little impact on your server as possible, this only happens when you are logged in.");
            online_console_receive = add(name, "online-console", "receive").setDefValue("false")
                    .setComment("Receives messages from the Online-Console and executes them.");

            save();

        } catch (Exception e) {
            AL.error(e);
        }
    }
}
