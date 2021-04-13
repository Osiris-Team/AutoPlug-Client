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


    public CleanerConfig() {
        super(System.getProperty("user.dir")+"/autoplug-cleaner-config.yml");
        try{
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment(
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

            logs_cleaner = add(name,"logs-cleaner","enable").setDefValue("false")
                    .setComment("Deletes log files from /autoplug-logs/...");
            logs_cleaner_max = add(name,"logs-cleaner","max-days").setDefValue("7")
                    .setComment("If a file is older than this amount of days, it gets deleted. \nMinimum value is 1.");
            logs_cleaner_custom_dir = add(name,"logs-cleaner","custom-dir")
                    .setComment("Enter a custom directory path (linux and windows formats are supported).\nLeave empty if default path should be used.");

            mc_logs_cleaner = add(name,"mc-logs-cleaner","enable").setDefValue("false")
                    .setComment("Deletes log files from /logs/...");
            mc_logs_cleaner_max = add(name,"mc-logs-cleaner","max-days").setDefValue("7");
            mc_logs_cleaner_custom_dir = add(name,"mc-logs-cleaner","custom-dir");

            downloads_cleaner = add(name,"downloads-cleaner","enable").setDefValue("false")
                    .setComment("Deletes files from /autoplug-downloads/...");
            downloads_cleaner_max = add(name,"downloads-cleaner","max-days").setDefValue("7");
            downloads_cleaner_custom_dir = add(name,"downloads-cleaner","custom-dir");

            save();

        } catch (Exception e) {
            AL.error(e);
        }
    }
}
