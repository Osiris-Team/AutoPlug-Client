/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.local;

import com.osiris.autoplug.client.configs.ServerConfig;
import com.osiris.autoplug.client.utils.AutoPlugLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class LocalConnectionValidator {


    LocalConnectionValidator(Socket local_socket, DataInputStream local_dis, DataOutputStream local_dos){

        Thread newThread = new Thread(() -> {

            try {

                AutoPlugLogger.info("Validating current AutoPlugPlugin connection...");

                boolean matches = local_dis.readUTF().equals(ServerConfig.server_key);

                if (matches){
                    local_dos.writeUTF("true");
                    AutoPlugLogger.info("Keys match!");

                    new LocalTaskReceivePlugins(local_socket, local_dis, local_dos);
                }
                else{
                    local_dos.writeUTF("false");
                    AutoPlugLogger.info("Wrong AutoPlugPlugin! Validation failed!");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        });
        newThread.start();
    }


}
