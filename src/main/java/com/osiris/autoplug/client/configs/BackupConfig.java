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
    public DYModule backup_server_files_cool_down;
    public DYModule backup_server_files_upload;
    public DYModule backup_server_files_upload_host;
    public DYModule backup_server_files_upload_port;
    public DYModule backup_server_files_upload_user;
    public DYModule backup_server_files_upload_password;
    public DYModule backup_server_files_upload_path;
    public DYModule backup_server_files_upload_rsa;

    public DYModule backup_worlds;
    public DYModule backup_worlds_max_days;
    public DYModule backup_worlds_cool_down;
    public DYModule backup_worlds_upload;
    public DYModule backup_worlds_upload_host;
    public DYModule backup_worlds_upload_port;
    public DYModule backup_worlds_upload_user;
    public DYModule backup_worlds_upload_password;
    public DYModule backup_worlds_upload_path;
    public DYModule backup_worlds_upload_rsa;

    public DYModule backup_plugins;
    public DYModule backup_plugins_max_days;
    public DYModule backup_plugins_cool_down;
    public DYModule backup_plugins_upload;
    public DYModule backup_plugins_upload_host;
    public DYModule backup_plugins_upload_port;
    public DYModule backup_plugins_upload_user;
    public DYModule backup_plugins_upload_password;
    public DYModule backup_plugins_upload_path;
    public DYModule backup_plugins_upload_rsa;


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

            backup_server_files = add(name, "server-files-backup", "enable").setDefValue("false").setComment(
                    "Backups all files in root (except folders) to /autoplug-backups/server/...zip.");
            backup_server_files_max_days = add(name, "server-files-backup", "max-days").setDefValue("7").setComment(
                    "Set max-days to 0 if you want to keep your backups forever.");
            backup_server_files_cool_down = add(name, "server-files-backup", "cool-down").setDefValue("60").setComments(
                    "The cool-down for this task in minutes.",
                    "If you restart your server multiple times in a short amount of time,",
                    "you probably won't want to create backups each time you restart your server.",
                    "The cool-down prevents exactly that from happening, saves you storage space and time.",
                    "Set to 0 to disable."
            );
            backup_server_files_upload = add(name, "server-files-backup", "upload").setDefValue("false").setComment(
                    "Upload backup to FTPS/SFTP server.");
            backup_server_files_upload_host = add(name, "server-files-backup", "upload", "host").setComment(
                    "Set hostname of FTPS/SFTP server.");
            backup_server_files_upload_port = add(name, "server-files-backup", "upload", "port").setComment(
                    "Set port of FTPS/SFTP server.");
            backup_server_files_upload_user = add(name, "server-files-backup", "upload", "username");
            backup_server_files_upload_password = add(name, "server-files-backup", "upload", "password");
            backup_server_files_upload_path = add(name, "server-files-backup", "upload", "path").setComment(
                    "Set the folder, in which the backup should be stored.");
            backup_server_files_upload_rsa = add(name, "server-files-backup", "upload", "rsa-key").setComment(
                    "Leave this field blank when using FTPS.");

            backup_worlds = add(name, "worlds-backup", "enable").setDefValue("false").setComment(
                    "Backups all folders starting with \"world\" to /autoplug-backups/worlds/...zip");
            backup_worlds_max_days = add(name, "worlds-backup", "max-days").setDefValue("7");
            backup_worlds_cool_down = add(name, "worlds-backup", "cool-down").setDefValue("60");
            backup_worlds_upload = add(name, "worlds-backup", "upload").setDefValue("false");
            backup_worlds_upload_host = add(name, "worlds-backup", "upload", "host");
            backup_worlds_upload_port = add(name, "worlds-backup", "upload", "port");
            backup_worlds_upload_user = add(name, "worlds-backup", "upload", "username");
            backup_worlds_upload_password = add(name, "worlds-backup", "upload", "password");
            backup_worlds_upload_path = add(name, "worlds-backup", "upload", "path");
            backup_worlds_upload_rsa = add(name, "worlds-backup", "upload", "rsa-key");

            backup_plugins = add(name, "plugins-backup", "enable").setDefValue("true").setComment(
                    "Backups your plugins folder to /autoplug-backups/plugins/...zip");
            backup_plugins_max_days = add(name, "plugins-backup", "max-days").setDefValue("7");
            backup_plugins_cool_down = add(name, "plugins-backup", "cool-down").setDefValue("60");
            backup_plugins_upload = add(name, "plugins-backup", "upload").setDefValue("false");
            backup_plugins_upload_host = add(name, "plugins-backup", "upload", "host");
            backup_plugins_upload_port = add(name, "plugins-backup", "upload", "port");
            backup_plugins_upload_user = add(name, "plugins-backup", "upload", "username");
            backup_plugins_upload_password = add(name, "plugins-backup", "upload", "password");
            backup_plugins_upload_path = add(name, "plugins-backup", "upload", "path");
            backup_plugins_upload_rsa = add(name, "plugins-backup", "upload", "rsa-key");

            save();

        } catch (Exception e) {
            AL.error(e);
        }
    }
}
