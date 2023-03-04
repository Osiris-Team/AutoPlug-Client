/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.utils.UtilsRandom;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class SystemConfig extends Yaml {

    public YamlSection timestamp_last_backup;

    public YamlSection timestamp_last_updater_tasks; // Only matters if global cooldown for updaters is enabled
    public YamlSection autoplug_web_ssl;
    public YamlSection autoplug_web_ip;
    public YamlSection autoplug_web_port;
    public YamlSection autoplug_plugin_key;
    public YamlSection is_autostart_registered;


    public SystemConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException, YamlWriterException {
        super(System.getProperty("user.dir") + "/autoplug/system/config.yml");
        lockFile();
        load();
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
        is_autostart_registered = put(name, "is-autostart-registered").setDefValues("false");
        timestamp_last_updater_tasks = put(name, "timestamp-last-updater-tasks");

        timestamp_last_backup = put(name, "timestamp-last-backup-task");

        autoplug_web_ssl = put(name, "autoplug-web-ssl").setDefValues("true").setComments("If localhost is used below, remember to set this to false too!");
        autoplug_web_ip = put(name, "autoplug-web-ip").setDefValues("144.91.78.158").setComments("Set to localhost to test on the local server.");
        autoplug_web_port = put(name, "autoplug-web-port").setDefValues("35555");

        autoplug_plugin_key = put(name, "autoplug-plugin-key");
        if (autoplug_plugin_key.asString() == null)
            autoplug_plugin_key.setValues(new UtilsRandom().generateNewKey(500));

        save();
        unlockFile();
    }
}
