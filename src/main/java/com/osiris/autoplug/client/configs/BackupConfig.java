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

public class BackupConfig extends DreamYaml {

    public DYModule backup_server_files;
    public DYModule backup_server_files_max_days;

    public DYModule backup_worlds;
    public DYModule backup_worlds_max_days;

    public DYModule backup_plugins;
    public DYModule backup_plugins_max_days;


    public BackupConfig() {
        super(System.getProperty("user.dir")+"/autoplug-backup-config.yml");
        try{
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment(
                    "#######################################################################################################################\n" +
                            "    ___       __       ___  __\n" +
                            "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                            "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                            " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                            "                                /___/ Backup-Config\n" +
                            "Thank you for using AutoPlug!\n" +
                            "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                            "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                            "\n" +
                            "#######################################################################################################################");

            backup_server_files = add(name,"server-files-backup","enable").setDefValue("false").setComment(
                    "Backups all files in root, except folders to /autoplug-backups/server/...zip.");
            backup_server_files_max_days = add(name,"server-files-backup","max-days").setDefValue("7").setComment(
                    "Set max-days to 0 if you want to keep your backups forever.");

            backup_worlds = add(name,"worlds-backup","enable").setDefValue("false").setComment(
                    "Backups all folders starting with \"world\" to /autoplug-backups/worlds/...zip");
            backup_worlds_max_days = add(name,"worlds-backup","max-days").setDefValue("7").setComment(
                    "Set max-days to 0 if you want to keep your backups forever.");

            backup_plugins = add(name,"plugins-backup","enable").setDefValue("true").setComment(
                    "Backups your plugins folder to /autoplug-backups/plugins/...zip");
            backup_plugins_max_days = add(name,"plugins-backup","max-days").setDefValue("7").setComment(
                    "Set max-days to 0 if you want to keep your backups forever.");

            save();

        } catch (Exception e) {
            AL.error(e);
        }
    }
}
