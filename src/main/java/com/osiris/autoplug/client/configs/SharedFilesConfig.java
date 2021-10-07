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

        enable = put(name, "enable").setDefValues("false").setComments("Enable/Disable sharing folders from this server to other servers.\n" +
                "NOTE: CHANGES TO THIS FILE REQUIRE A AUTOPLUG RESTART TO TAKE AFFECT!\n");

        copy_from = put(name, "copy-from").setDefValues("./plugins", "./server.jar")
                .setComments("List of folders to watch. Once a file event happens, that event/change gets shared/sent to the servers in the 'send-to' list.\n" +
                        "All folders must be sub-folders of the server root and thus start with './' (the servers root directory).\n" +
                        "If you want to watch all files from your server simply add './' below.\n" +
                        "Note that sub-folders of the added folders below are also watched. \n" +
                        //TODO "More control is in TODO.\n" +
                        ""
                );

        send_to = put(name, "send-to").addDefValueWithComment("C:\\User\\Peter\\servers\\my-second-server", "Example for absolute path")
                .addDefValueWithComment("./servers/another-server", "Example for relative path")
                .setComments("List of server root folders of the servers which receive the file changes.\n" +
                        // TODO "NOT WORKING/IN TODO: Note that if the server is not on the same machine (you enter an ip), then you will need to have AutoPlug installed on that server too.\n" +
                        // "Otherwise if the server is on the same machine (you enter its path to its server root directory), then you don't need AutoPlug installed on it.\n" +
                        //TODO "Note that if you enter a folder that contains multiple servers, AutoPlug detects those individual servers automatically.\n" +
                        "Note that absolute and relative paths" +
                        //TODO ", as well as ipv4 and ipv6 addresses" +
                        " are supported.\n");

        save();
        unlockFile();
    }
}
