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

public class SharedFilesConfig extends DreamYaml {

    public DYModule enable;
    public DYModule copy_from;
    public DYModule send_to;


    public SharedFilesConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, NotLoadedException, IllegalKeyException, DYWriterException {
        super(System.getProperty("user.dir") + "/autoplug/shared-files-config.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Shared-Files-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        enable = put(name, "enable").setDefValues("false").setComments("Enable/Disable sharing files from this server to other servers.\n" +
                "Once enabled AutoPlug listens for file events in your server root (and its sub-folders). \n" +
                "If a file event happens and that file/directory is in the 'copy-from' list, then those changes will get sent to the servers in the 'send-to' list.\n" +
                "NOTE: CHANGES TO THIS FILE REQUIRE A AUTOPLUG RESTART TO TAKE AFFECT!\n");

        copy_from = put(name, "copy-from").setDefValues("./plugins", "./server.jar")
                .setComments("List of files/directories to send to the servers in the 'send-to' list.\n" +
                        "All file paths must start with './' which represents the servers root directory.\n" +
                        "If you want to share all files from your server simply add './' below.\n");

        send_to = put(name, "send-to").setDefValues("C:\\this\\is\\an\\absolute\\path", "0.0.0.0", "./this/is/a/relative/path").setComments("List of directories/ips of the servers which receive the file changes.\n" +
                "Note that if the server is not on the same machine (you enter an ip), then you will need to have AutoPlug installed on that server too.\n" +
                "Otherwise if the server is on the same machine (you enter its path to its server root directory), then u don't need AutoPlug installed on it.\n" +
                "Note that if you enter a directory that contains multiple servers, AutoPlug detects those individual servers automatically.\n" +
                "Note that absolute and relative paths, as well as ipv4 and ipv6 addresses are supported.\n");

        save();
        unlockFile();
    }
}
