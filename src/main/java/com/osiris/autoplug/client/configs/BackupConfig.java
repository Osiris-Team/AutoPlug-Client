/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.utils.GD;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BackupConfig extends Yaml {

    public YamlSection backup;
    public YamlSection backup_max_days;
    public YamlSection backup_cool_down;
    public YamlSection backup_exclude;
    public YamlSection backup_exclude_list;
    public YamlSection backup_include;
    public YamlSection backup_include_list;
    public YamlSection backup_upload;
    public YamlSection backup_upload_delete_on_complete;
    public YamlSection backup_upload_host;
    public YamlSection backup_upload_port;
    public YamlSection backup_upload_user;
    public YamlSection backup_upload_password;
    public YamlSection backup_upload_path;
    public YamlSection backup_upload_rsa;


    public BackupConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException, YamlWriterException {
        super(System.getProperty("user.dir") + "/autoplug/backup.yml");
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
                        "You can find detailed installation instructions here: https://autoplug.one/installer\n" +
                        "If there are any questions or you just want chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        put(name).setCountTopLineBreaks(1);
        backup = put(name, "enable").setDefValues("true");
        backup_max_days = put(name, "max-days").setDefValues("7").setComments(
                "Set max-days to 0 if you want to keep your backups forever.");
        backup_cool_down = put(name, "cool-down").setDefValues("500").setComments(
                "The cool-down for this task in minutes.",
                "If you restart your server multiple times in a short amount of time,",
                "you probably won't want to create backups each time you restart your server.",
                "The cool-down prevents exactly that from happening and saves you storage space and time.",
                "Set to 0 to disable."
        );
        backup_include = put(name, "include", "enable").setDefValues("true").setComments(
                "Add specific files or folders you want to include in the backup, to the list below.",
                "Windows/Linux formats are supported. './' stands for the servers root directory."
        );
        backup_include_list = put(name, "include", "list").setDefValues(
                "./",
                "./example/directory",
                "./specific-file.txt",
                "C:\\Users\\Example Windows Directory"
        );
        backup_exclude = put(name, "exclude", "enable").setDefValues("true").setComments(
                "Add specific files or folders you want to exclude from the backup, to the list below.",
                "Windows/Linux formats are supported. './' stands for the servers root directory."
        );
        backup_exclude_list = put(name, "exclude", "list").setDefValues(
                "./autoplug/backups",
                "./autoplug/downloads",
                "./autoplug/system",
                "./autoplug/logs",
                "./plugins/dynmap",
                "./plugins/WorldBorder"
        );
        backup_upload = put(name, "upload", "enable").setDefValues("false").setComments(
                "Upload the newly generated backup zip to the FTPS/SFTP server.");
        backup_upload_delete_on_complete = put(name, "upload", "delete-on-complete").setDefValues("false").setComments(
                "Deletes the newly generated backup zip, directly after uploading finishes.");
        backup_upload_host = put(name, "upload", "host").setComments(
                "Hostname of the FTPS/SFTP server.");
        backup_upload_port = put(name, "upload", "port").setComments(
                "Port of the FTPS/SFTP server.");
        backup_upload_user = put(name, "upload", "username");
        backup_upload_password = put(name, "upload", "password");
        backup_upload_path = put(name, "upload", "path").setComments(
                "Set the folder, in which the backup should be stored.");
        backup_upload_rsa = put(name, "upload", "rsa-key").setComments(
                "Leave this field blank when using FTPS.");

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

    public List<File> getExcludedFiles() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_exclude_list.asStringList()) {
            try {
                files.add(pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

    public List<File> getIncludedFiles() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_include_list.asStringList()) {
            try {
                files.add(pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

}
