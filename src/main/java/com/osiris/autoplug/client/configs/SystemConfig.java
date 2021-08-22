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

public class SystemConfig extends DreamYaml {

    public DYModule timestamp_last_server_files_backup_task;
    public DYModule timestamp_last_worlds_backup_task;
    public DYModule timestamp_last_plugins_backup_task;

    public DYModule timestamp_last_updater_tasks; // Only matters if global cooldown for updaters is enabled

    public SystemConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug/system/config.yml");
        lockAndLoad();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ System-Config\n" +
                        "DO NOT CHANGE ANYTHING IN HERE, UNLESS YOU KNOW WHAT YOU ARE DOING!\n" +
                        "\n" +
                        "#######################################################################################################################");

        timestamp_last_updater_tasks = put(name, "timestamp-last-updater-tasks");

        timestamp_last_server_files_backup_task = put(name, "timestamp-last-server-files-backup-task");
        timestamp_last_worlds_backup_task = put(name, "timestamp-last-worlds-backup-task");
        timestamp_last_plugins_backup_task = put(name, "timestamp-last-plugins-backup-task");


        saveAndUnlock();
    }
}
