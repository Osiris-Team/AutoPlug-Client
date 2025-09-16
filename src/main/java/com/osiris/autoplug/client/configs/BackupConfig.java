/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.network.online.connections.ConAutoPlugConsoleSend;
import com.osiris.autoplug.client.tasks.backup.BackupGoogleDrive;
import com.osiris.autoplug.client.utils.UtilsFile;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackupConfig extends MyYaml {
    private static final boolean isFileListenerRegistered = false;

    public YamlSection backup;
    public YamlSection backup_max_days;
    public YamlSection backup_cool_down;
    public YamlSection backup_path;
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
    public YamlSection backup_upload_alternatives_google_drive;
    public YamlSection backup_upload_alternatives_google_drive_enable;
    public YamlSection backup_upload_alternatives_google_drive_project_id;
    public YamlSection backup_upload_alternatives_google_drive_client_id;
    public YamlSection backup_upload_alternatives_google_drive_client_secret;
    public YamlSection backup_upload_alternatives_google_drive_refresh_token;

    public static final AtomicBoolean isGoogleDriveEnabled = new AtomicBoolean(false);

    public BackupConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException, YamlWriterException {
        super(System.getProperty("user.dir") + "/autoplug/backup.yml");

        addSingletonConfigFileEventListener(e -> {
            boolean newVal = this.backup_upload_alternatives_google_drive_enable.asBoolean();
            if(isGoogleDriveEnabled.get() != newVal && newVal == true){
                // Changed from false to true, meaning enabled, thus check if we need to do credential fetching
                try{
                    BackupGoogleDrive backupGoogleDrive = new BackupGoogleDrive();
                    backupGoogleDrive.getCredentials(this);
                } catch (Exception ex) {
                    AL.warn(ex);
                }
            }
            isGoogleDriveEnabled.set(newVal);
        });

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
        backup_path = put(name, "path").setDefValues("./autoplug/backups").setComments(
                "Where to create your backups.");
        backup_include = put(name, "include", "enable").setDefValues("true").setComments(
                "Add specific files or folders you want to include in the backup, to the list below.",
                "Windows/Linux formats are supported. './' stands for the servers root directory."
        );
        backup_include_list = put(name, "include", "list").setDefValues(
                "./",
                "./server.properties"
        ).setComments(
                "  - ./example/directory",
                "  - ./specific-file.txt",
                "  - C:\\Users\\Example Windows Directory"
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
                "Upload the newly generated backup zip to the FTPS/SFTP server or Google Drive.");
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
        backup_upload_rsa = put(name, "upload", "rsa-key-path").setComments(
                "Leave this field blank when using FTPS.",
                "Otherwise enter a relative or absolute path to the file containing the key.",
                "Usually the path is something like this: /home/username/.ssh/id_rsa");

        backup_upload_alternatives_google_drive = put(name, "upload", "alternatives", "google-drive").setComments("" +
                "How This Works:\n" +
                "1. You create OAuth credentials in your own Google Cloud Console\n" +
                "2. You enter your Client ID and Client Secret in the sections below\n" +
                "3. When enabled, your browser will automatically open for authentication\n" +
                "4. After authentication, a refresh token is saved for future use\n" +
                "\n" +
                "Google-Drive Setup (assuming your server is hosted on a regular PC, meaning your system has a GUI and web-browser):\n" +
                "1. Create a project in Google Cloud Console (or use an existing one) and enter its project_id below: https://developers.google.com/workspace/guides/create-project\n" +
                "2. Enable the Google Drive API: https://console.cloud.google.com/flows/enableapi?apiid=drive.googleapis.com\n" +
                "3. Create OAuth 2.0 login panel: https://console.cloud.google.com/auth/branding\n" +
                "4. Create a Client (type \"Desktop app\"): https://console.cloud.google.com/auth/clients\n" +
                "5. Add http://localhost:8888/Callback as authorized redirect URI\n" +
                "6. Enter your Client ID and Client Secret into the sections below\n" +
                "7. Set 'enable' to 'true' and save this file\n" +
                "8. Your browser will automatically open for authentication, or you can find the URL in the console and open it manually\n" +
                "9. After authentication, backups will be uploaded to your Google Drive\n" +
                "\n" +
                "Google-Drive Setup (assuming your server has no GUI and no web-browser, typical hosted linux server setup):\n" +
                "1. Ensure you have done all the steps above at least once until step 5\n" +
                "2. " +
                "\n" +
                "!!!IMPORTANT!!! This file contains sensitive data - do not share it with anyone!\n");
        backup_upload_alternatives_google_drive_enable = put(name, "upload", "alternatives", "google-drive", "enable").setDefValues("false");
        backup_upload_alternatives_google_drive_project_id = put(name, "upload", "alternatives", "google-drive", "project-id").setComments(
                "Get this from Google Cloud Console after creating your project. You also use an existing project and enter its name/project-id here.").setDefValues("autoplug-client");
        backup_upload_alternatives_google_drive_client_id = put(name, "upload", "alternatives", "google-drive", "client-id").setComments(
                "Get this from Google Cloud Console after creating OAuth credentials.");
        backup_upload_alternatives_google_drive_client_secret = put(name, "upload", "alternatives", "google-drive", "client-secret").setComments(
                "Get this from Google Cloud Console after creating OAuth credentials.");
        backup_upload_alternatives_google_drive_refresh_token = put(name, "upload", "alternatives", "google-drive", "refresh-token").setComments(
                "This will be automatically obtained during first-time setup.");

        save();
        unlockFile();
    }

    public List<File> getExcludedFiles() {
        List<File> files = new ArrayList<>();
        for (String path :
                backup_exclude_list.asStringList()) {
            try {
                files.add(new UtilsFile().pathToFile(path));
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
                files.add(new UtilsFile().pathToFile(path));
            } catch (Exception e) {
                AL.warn(e);
            }
        }
        return files;
    }

    @Override
    public Yaml validateValues() {
        return this;
    }
}
