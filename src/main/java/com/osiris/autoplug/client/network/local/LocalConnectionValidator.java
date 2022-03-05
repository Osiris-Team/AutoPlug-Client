/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.local;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.exceptions.*;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

@Deprecated
public class LocalConnectionValidator {


    LocalConnectionValidator(Socket local_socket, @NotNull DataInputStream local_dis, @NotNull DataOutputStream local_dos) {

        Thread newThread = new Thread(() -> {

            try {

                AL.info("Validating current AutoPlugPlugin connection...");

                boolean matches = local_dis.readUTF().equals(new GeneralConfig().server_key.asString());

                if (matches) {
                    local_dos.writeUTF("true");
                    AL.info("Keys match!");

                    new LocalTaskReceivePlugins(local_socket, local_dis, local_dos);
                } else {
                    local_dos.writeUTF("false");
                    AL.info("Wrong AutoPlugPlugin! Validation failed!");
                }

            } catch (@NotNull IOException | DuplicateKeyException | YamlReaderException | IllegalListException | YamlWriterException | NotLoadedException | IllegalKeyException e) {
                e.printStackTrace();
            }

        });
        newThread.start();
    }


}
