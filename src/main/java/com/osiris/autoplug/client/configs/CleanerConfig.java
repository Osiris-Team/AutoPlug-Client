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

public class CleanerConfig extends DreamYaml {

    public DYModule logs_cleaner;
    public DYModule logs_cleaner_max;
    public DYModule logs_cleaner_custom_dir;

    public DYModule mc_logs_cleaner;
    public DYModule mc_logs_cleaner_max;
    public DYModule mc_logs_cleaner_custom_dir;

    public DYModule downloads_cleaner;
    public DYModule downloads_cleaner_max;
    public DYModule downloads_cleaner_custom_dir;


    public CleanerConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug/cleaner-config.yml");
        lockAndLoad();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Cleaner-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        logs_cleaner = put(name, "logs-cleaner", "enable").setDefValues("false")
                .setComments("Deletes log files from /autoplug-logs/...");
        logs_cleaner_max = put(name, "logs-cleaner", "max-days").setDefValues("7")
                .setComments("If a file is older than this amount of days, it gets deleted. \nMinimum value is 1.");
        logs_cleaner_custom_dir = put(name, "logs-cleaner", "custom-dir")
                .setComments("Enter a custom directory path (linux and windows formats are supported).\nLeave empty if default path should be used.");

        mc_logs_cleaner = put(name, "mc-logs-cleaner", "enable").setDefValues("false")
                .setComments("Deletes log files from /logs/...");
        mc_logs_cleaner_max = put(name, "mc-logs-cleaner", "max-days").setDefValues("7");
        mc_logs_cleaner_custom_dir = put(name, "mc-logs-cleaner", "custom-dir");

        downloads_cleaner = put(name, "downloads-cleaner", "enable").setDefValues("false")
                .setComments("Deletes files from /autoplug/downloads/...");
        downloads_cleaner_max = put(name, "downloads-cleaner", "max-days").setDefValues("7");
        downloads_cleaner_custom_dir = put(name, "downloads-cleaner", "custom-dir");

        saveAndUnlock();

    }
}
