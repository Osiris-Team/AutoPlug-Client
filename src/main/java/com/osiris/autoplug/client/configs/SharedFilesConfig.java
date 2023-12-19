/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.managers.SyncFilesManager;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;

import java.io.IOException;

public class SharedFilesConfig extends MyYaml {

    public YamlSection enable;
    public YamlSection copy_from;
    public YamlSection send_to;


    public SharedFilesConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException, YamlWriterException {
        super(System.getProperty("user.dir") + "/autoplug/shared-files.yml");

        addSingletonConfigFileEventListener(e -> {
            try {
                new SyncFilesManager(this);
            } catch (Exception ex) {
                AL.warn(ex);
            }
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
                        "                                /___/ Shared-Files-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions here: https://autoplug.one/installer\n" +
                        "If there are any questions or you just want chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        enable = put(name, "enable").setDefValues("false").setComments("Enable/Disable sharing folders from this server to other servers.\n" +
                "NOTE: CHANGES TO THIS FILE REQUIRE A AUTOPLUG RESTART TO TAKE AFFECT!\n");

        //TODO "More control is in TODO.\n" +
        copy_from = put(name, "copy-from").setDefValues("./plugins", "./server.jar")
                .setComments("List of folders to watch. Once a file event happens, that event/change gets shared/sent to the servers in the 'send-to' list.\n" +
                        "All folders must be sub-folders of the server root and thus start with './' (the servers root directory).\n" +
                        "If you want to watch all files from your server simply add './' below.\n" +
                        "Note that sub-folders of the added folders below are also watched. \n"
                );

        send_to = put(name, "send-to").addDefValues("C:\\User\\Peter\\servers\\my-second-server")
                .addDefSideComments("Example for absolute path")
                .addDefValues("./servers/another-server")
                .addDefSideComments("Example for relative path")
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

    @Override
    public Yaml validateValues() {
        return this;
    }
}
