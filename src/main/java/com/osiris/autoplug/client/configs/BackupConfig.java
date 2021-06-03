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

public class BackupConfig extends DreamYaml {

    public DYModule backup_server_files;
    public DYModule backup_server_files_max_days;
    public DYModule backup_server_files_cool_down;
    public DYModule backup_server_files_upload;
    public DYModule backup_server_files_upload_delete_on_complete;
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
    public DYModule backup_worlds_upload_delete_on_complete;
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
    public DYModule backup_plugins_upload_delete_on_complete;
    public DYModule backup_plugins_upload_host;
    public DYModule backup_plugins_upload_port;
    public DYModule backup_plugins_upload_user;
    public DYModule backup_plugins_upload_password;
    public DYModule backup_plugins_upload_path;
    public DYModule backup_plugins_upload_rsa;


    public BackupConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug-backup-config.yml");
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
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

        backup_server_files = put(name, "server-files-backup", "enable").setDefValues("false").setComments(
                "Backups all files in your servers root (except folders) to /autoplug-backups/server/...zip.");
        backup_server_files_max_days = put(name, "server-files-backup", "max-days").setDefValues("7").setComments(
                "Set max-days to 0 if you want to keep your backups forever.");
        backup_server_files_cool_down = put(name, "server-files-backup", "cool-down").setDefValues("60").setComments(
                "The cool-down for this task in minutes.",
                "If you restart your server multiple times in a short amount of time,",
                "you probably won't want to create backups each time you restart your server.",
                "The cool-down prevents exactly that from happening and saves you storage space and time.",
                "Set to 0 to disable."
        );
        backup_server_files_upload = put(name, "server-files-backup", "upload", "enable").setDefValues("false").setComments(
                "Upload the newly generated backup zip to the FTPS/SFTP server.");
        backup_server_files_upload_delete_on_complete = put(name, "server-files-backup", "upload", "delete-on-complete").setDefValues("false").setComments(
                "Deletes the newly generated backup zip, directly after uploading finishes.");
        backup_server_files_upload_host = put(name, "server-files-backup", "upload", "host").setComments(
                "Hostname of the FTPS/SFTP server.");
        backup_server_files_upload_port = put(name, "server-files-backup", "upload", "port").setComments(
                "Port of the FTPS/SFTP server.");
        backup_server_files_upload_user = put(name, "server-files-backup", "upload", "username");
        backup_server_files_upload_password = put(name, "server-files-backup", "upload", "password");
        backup_server_files_upload_path = put(name, "server-files-backup", "upload", "path").setComments(
                "Set the folder, in which the backup should be stored.");
        backup_server_files_upload_rsa = put(name, "server-files-backup", "upload", "rsa-key").setComments(
                "Leave this field blank when using FTPS.");

        backup_worlds = put(name, "worlds-backup", "enable").setDefValues("false").setComments(
                "Backups all folders starting with \"world\" to /autoplug-backups/worlds/...zip");
        backup_worlds_max_days = put(name, "worlds-backup", "max-days").setDefValues("7");
        backup_worlds_cool_down = put(name, "worlds-backup", "cool-down").setDefValues("60");
        backup_worlds_upload = put(name, "worlds-backup", "upload", "enable").setDefValues("false");
        backup_worlds_upload_delete_on_complete = put(name, "worlds-backup", "upload", "delete-on-complete").setDefValues("false");
        backup_worlds_upload_host = put(name, "worlds-backup", "upload", "host");
        backup_worlds_upload_port = put(name, "worlds-backup", "upload", "port");
        backup_worlds_upload_user = put(name, "worlds-backup", "upload", "username");
        backup_worlds_upload_password = put(name, "worlds-backup", "upload", "password");
        backup_worlds_upload_path = put(name, "worlds-backup", "upload", "path");
        backup_worlds_upload_rsa = put(name, "worlds-backup", "upload", "rsa-key");

        backup_plugins = put(name, "plugins-backup", "enable").setDefValues("true").setComments(
                "Backups your plugins folder to /autoplug-backups/plugins/...zip");
        backup_plugins_max_days = put(name, "plugins-backup", "max-days").setDefValues("7");
        backup_plugins_cool_down = put(name, "plugins-backup", "cool-down").setDefValues("60");
        backup_plugins_upload = put(name, "plugins-backup", "upload", "enable").setDefValues("false");
        backup_plugins_upload_delete_on_complete = put(name, "plugins-backup", "upload", "delete-on-complete").setDefValues("false");
        backup_plugins_upload_host = put(name, "plugins-backup", "upload", "host");
        backup_plugins_upload_port = put(name, "plugins-backup", "upload", "port");
        backup_plugins_upload_user = put(name, "plugins-backup", "upload", "username");
        backup_plugins_upload_password = put(name, "plugins-backup", "upload", "password");
        backup_plugins_upload_path = put(name, "plugins-backup", "upload", "path");
        backup_plugins_upload_rsa = put(name, "plugins-backup", "upload", "rsa-key");

        save();
    }
}
