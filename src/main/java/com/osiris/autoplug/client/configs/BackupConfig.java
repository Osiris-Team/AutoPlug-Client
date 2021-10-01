/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BackupConfig extends DreamYaml {

    public DYModule backup_server_files;
    public DYModule backup_server_files_max_days;
    public DYModule backup_server_files_cool_down;
    public DYModule backup_server_files_exclude;
    public DYModule backup_server_files_exclude_list;
    public DYModule backup_server_files_include;
    public DYModule backup_server_files_include_list;
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
    public DYModule backup_worlds_exclude;
    public DYModule backup_worlds_exclude_list;
    public DYModule backup_worlds_include;
    public DYModule backup_worlds_include_list;
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
    public DYModule backup_plugins_exclude;
    public DYModule backup_plugins_exclude_list;
    public DYModule backup_plugins_include;
    public DYModule backup_plugins_include_list;
    public DYModule backup_plugins_upload;
    public DYModule backup_plugins_upload_delete_on_complete;
    public DYModule backup_plugins_upload_host;
    public DYModule backup_plugins_upload_port;
    public DYModule backup_plugins_upload_user;
    public DYModule backup_plugins_upload_password;
    public DYModule backup_plugins_upload_path;
    public DYModule backup_plugins_upload_rsa;

    public BackupConfig() throws NotLoadedException, DYWriterException, IOException, IllegalKeyException, DuplicateKeyException, DYReaderException, IllegalListException {
        this(ConfigPreset.DEFAULT);
    }

    public BackupConfig(ConfigPreset preset) throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug/backup-config.yml");
        lockFile();
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

        put(name, "server-files-backup").setCountTopSpaces(1);
        backup_server_files = put(name, "server-files-backup", "enable").setDefValues("false").setComments(
                "Backups all files in your servers root (except folders) to /autoplug/backups/server/...zip.");
        backup_server_files_max_days = put(name, "server-files-backup", "max-days").setDefValues("7").setComments(
                "Set max-days to 0 if you want to keep your backups forever.");
        backup_server_files_cool_down = put(name, "server-files-backup", "cool-down").setDefValues("500").setComments(
                "The cool-down for this task in minutes.",
                "If you restart your server multiple times in a short amount of time,",
                "you probably won't want to create backups each time you restart your server.",
                "The cool-down prevents exactly that from happening and saves you storage space and time.",
                "Set to 0 to disable."
        );
        backup_server_files_exclude = put(name, "server-files-backup", "exclude", "enable").setDefValues("false").setComments(
                "Add specific files or folders you want to exclude from the backup, to the list below.",
                "Windows/Linux formats are supported. './' stands for the servers root directory."
        );
        backup_server_files_exclude_list = put(name, "server-files-backup", "exclude", "list").setDefValues(
                "./example/directory",
                "./specific-file.txt",
                "C:\\Users\\Example Windows Directory"
        );
        backup_server_files_include = put(name, "server-files-backup", "include", "enable").setDefValues("false").setComments(
                "Add specific files or folders you want to include in the backup, to the list below.",
                "Windows/Linux formats are supported. './' stands for the servers root directory.",
                "Note that, if you enter a file/folder in the include list AND in the exclude list, that file/folder will get included."
        );
        backup_server_files_include_list = put(name, "server-files-backup", "include", "list").setDefValues(
                "./example/directory",
                "./specific-file.txt",
                "C:\\Users\\Example Windows Directory"
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

        put(name, "worlds-backup").setCountTopSpaces(1);
        backup_worlds = put(name, "worlds-backup", "enable").setDefValues("false").setComments(
                "Backups all folders starting with \"world\" to /autoplug/backups/worlds/...zip");
        backup_worlds_max_days = put(name, "worlds-backup", "max-days").setDefValues("7");
        backup_worlds_cool_down = put(name, "worlds-backup", "cool-down").setDefValues("500");
        backup_worlds_exclude = put(name, "worlds-backup", "exclude", "enable").setDefValues("false");
        backup_worlds_exclude_list = put(name, "worlds-backup", "exclude", "list").setDefValues(
                "./example/directory",
                "./specific-file.txt",
                "C:\\Users\\Example Windows Directory"
        );
        backup_worlds_include = put(name, "worlds-backup", "include", "enable").setDefValues("false");
        backup_worlds_include_list = put(name, "worlds-backup", "include", "list").setDefValues(
                "./example/directory",
                "./specific-file.txt",
                "C:\\Users\\Example Windows Directory"
        );
        backup_worlds_upload = put(name, "worlds-backup", "upload", "enable").setDefValues("false");
        backup_worlds_upload_delete_on_complete = put(name, "worlds-backup", "upload", "delete-on-complete").setDefValues("false");
        backup_worlds_upload_host = put(name, "worlds-backup", "upload", "host");
        backup_worlds_upload_port = put(name, "worlds-backup", "upload", "port");
        backup_worlds_upload_user = put(name, "worlds-backup", "upload", "username");
        backup_worlds_upload_password = put(name, "worlds-backup", "upload", "password");
        backup_worlds_upload_path = put(name, "worlds-backup", "upload", "path");
        backup_worlds_upload_rsa = put(name, "worlds-backup", "upload", "rsa-key");

        put(name, "plugins-backup").setCountTopSpaces(1);
        backup_plugins = put(name, "plugins-backup", "enable").setDefValues("true").setComments(
                "Backups your plugins folder to /autoplug/backups/plugins/...zip");
        backup_plugins_max_days = put(name, "plugins-backup", "max-days").setDefValues("7");
        backup_plugins_cool_down = put(name, "plugins-backup", "cool-down").setDefValues("500");
        backup_plugins_exclude = put(name, "plugins-backup", "exclude", "enable").setDefValues("true");
        backup_plugins_exclude_list = put(name, "plugins-backup", "exclude", "list").setDefValues(
                "./plugins/dynmap",
                "./plugins/WorldBorder"
        );
        backup_plugins_include = put(name, "plugins-backup", "include", "enable").setDefValues("false");
        backup_plugins_include_list = put(name, "plugins-backup", "include", "list").setDefValues(
                "./example/directory",
                "./specific-file.txt",
                "C:\\Users\\Example Windows Directory"
        );

        backup_plugins_upload = put(name, "plugins-backup", "upload", "enable").setDefValues("false");
        backup_plugins_upload_delete_on_complete = put(name, "plugins-backup", "upload", "delete-on-complete").setDefValues("false");
        backup_plugins_upload_host = put(name, "plugins-backup", "upload", "host");
        backup_plugins_upload_port = put(name, "plugins-backup", "upload", "port");
        backup_plugins_upload_user = put(name, "plugins-backup", "upload", "username");
        backup_plugins_upload_password = put(name, "plugins-backup", "upload", "password");
        backup_plugins_upload_path = put(name, "plugins-backup", "upload", "path");
        backup_plugins_upload_rsa = put(name, "plugins-backup", "upload", "rsa-key");

        if (preset.equals(ConfigPreset.FAST)) {
            backup_server_files.setDefValues("true");
            backup_worlds.setDefValues("true");
        }

        save();
        unlockFile();
    }

    private File pathToFile(String path) {
        File file = null;
        if (path.contains("./"))
            path = path.replace("./", GD.WORKING_DIR.getAbsolutePath() + File.separator);
        if (!path.contains("/") && !path.contains("\\"))
            path = GD.WORKING_DIR.getAbsolutePath() + File.separator + path;

        return new File(path);
    }

    public List<File> getServerFilesExcluded() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_server_files_exclude_list.asStringList()) {
            try {
                files.add(pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

    public List<File> getServerFilesIncluded() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_server_files_include_list.asStringList()) {
            try {
                files.add(pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

    public List<File> getPluginsExcluded() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_plugins_exclude_list.asStringList()) {
            try {
                files.add(pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

    public List<File> getPluginsIncluded() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_plugins_include_list.asStringList()) {
            try {
                files.add(pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

    public List<File> getWorldsExcluded() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_worlds_exclude_list.asStringList()) {
            try {
                files.add(pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

    public List<File> getWorldsIncluded() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_worlds_include_list.asStringList()) {
            try {
                files.add(pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

}
