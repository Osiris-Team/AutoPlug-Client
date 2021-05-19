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

public class TasksConfig extends DreamYaml {

    public DYModule live_tasks;
    public DYModule refresh_interval;

    public TasksConfig() {
        super(System.getProperty("user.dir") + "/autoplug-tasks-config.yml");
        try {
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment(
                    "#######################################################################################################################\n" +
                            "    ___       __       ___  __\n" +
                            "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                            "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                            " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                            "                                /___/ Tasks-Config\n" +
                            "Thank you for using AutoPlug!\n" +
                            "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                            "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                            "\n" +
                            "#######################################################################################################################");

            live_tasks = add(name, "live-tasks", "enable").setDefValue("true").setComment(
                    "Enable this to view the detailed progress of a task. Supported platforms: Windows, Linux, OS X, Solaris and FreeBSD.\n" +
                            "Enabling this on unsupported platform will result in console spam.");
            refresh_interval = add(name, "live-tasks", "refresh-interval").setDefValue("250").setComment(
                    "Refresh interval in milliseconds.\n" +
                            "How often a task should get refreshed and update its information. Default is: 250ms");

            save();

        } catch (Exception e) {
            AL.error(e);
        }
    }
}
