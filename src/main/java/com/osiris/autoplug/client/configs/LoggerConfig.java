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

public class LoggerConfig extends Yaml {
    public YamlSection debug;
    public YamlSection autoplug_label;
    public YamlSection force_ansi;
    public YamlSection color_server_log;

    // Tasks
    public YamlSection live_tasks;
    public YamlSection refresh_interval;

    public YamlSection show_warnings;
    public YamlSection show_detailed_warnings;

    public LoggerConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/logger.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Logger-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions here: https://autoplug.one/installer\n" +
                        "If there are any questions or you just want chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        debug = put(name, "debug").setDefValues("false").setComments(
                "Writes the debug output to console.\n" +
                        "The log file contains the debug output by default and this option wont affect that.\n" +
                        "This is the only setting that needs a restart to work.");

        autoplug_label = put(name, "autoplug-label").setDefValues("AP");
        force_ansi = put(name, "force-ANSI").setDefValues("false").setComments(
                "Forces the terminal to use ANSI. Note that this may fail."
        );
        color_server_log = put(name, "color-server-log").setDefValues("true").setComments(
                "Checks the received line from the server process for specific words like 'warn' or 'exception' etc. and colors it accordingly."
        );

        put(name, "tasks").setCountTopLineBreaks(1);
        live_tasks = put(name, "tasks", "live-tasks", "enable").setDefValues("false").setComments(
                "Enable this to view the detailed progress of a task. Supported platforms: Windows, Linux, OS X, Solaris and FreeBSD.\n" +
                        "Enabling this on unsupported platform will result in console spam.");
        refresh_interval = put(name, "tasks", "live-tasks", "refresh-interval").setDefValues("500").setComments(
                "Refresh interval in milliseconds.\n" +
                        "How often a task should get refreshed and update its information. Default is: 500ms");

        put(name, "tasks", "show-warnings").setCountTopLineBreaks(1);
        show_warnings = put(name, "tasks", "show-warnings").setDefValues("true").setComments(
                "If the tasks produced warnings, these get shown.",
                "Its recommended to keep this option enabled.");

        show_detailed_warnings = put(name, "tasks", "show-detailed-warnings").setDefValues("false").setComments(
                "Shows additional, debugging information related to the warning.",
                "If the warning is an exception, its stack trace gets printed.",
                "The same information is available in the log file.",
                "Note that the 'show-warnings' option above must be enabled too.");

        save();
        unlockFile();
    }


}
