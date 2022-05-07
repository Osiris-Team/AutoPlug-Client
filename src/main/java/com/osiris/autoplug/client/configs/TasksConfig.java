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

public class TasksConfig extends Yaml {

    public YamlSection live_tasks;
    public YamlSection refresh_interval;

    public YamlSection show_warnings;
    public YamlSection show_detailed_warnings;

    public TasksConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/tasks.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
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

        put(name, "live-tasks").setCountTopLineBreaks(1);
        live_tasks = put(name, "live-tasks", "enable").setDefValues("true").setComments(
                "Enable this to view the detailed progress of a task. Supported platforms: Windows, Linux, OS X, Solaris and FreeBSD.\n" +
                        "Enabling this on unsupported platform will result in console spam.");
        refresh_interval = put(name, "live-tasks", "refresh-interval").setDefValues("250").setComments(
                "Refresh interval in milliseconds.\n" +
                        "How often a task should get refreshed and update its information. Default is: 250ms");

        put(name, "show-warnings").setCountTopLineBreaks(1);
        show_warnings = put(name, "show-warnings").setDefValues("true").setComments(
                "If the tasks produced warnings, these get shown.",
                "Its recommended to keep this option enabled.");

        show_detailed_warnings = put(name, "show-detailed-warnings").setDefValues("false").setComments(
                "Shows additional, debugging information related to the warning.",
                "If the warning is an exception, its stack trace gets printed.",
                "The same information is available in the log file.",
                "Note that the 'show-warnings' option above must be enabled too.");

        save();
        unlockFile();
    }
}
