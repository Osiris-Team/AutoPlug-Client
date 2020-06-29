/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.local;

import com.osiris.autoplug.client.network.online.OnlineConnection;
import com.osiris.autoplug.client.utils.AutoPlugLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class LocalTaskReceivePlugins {


    public LocalTaskReceivePlugins(Socket local_socket, DataInputStream local_dis, DataOutputStream local_dos){


        Thread newThread = new Thread(() -> {

            try{

                AutoPlugLogger.info("Waiting for plugins...");
                int amount = local_dis.readInt();

                String[] pl_names = new String[amount];
                String[] pl_authors = new String[amount];
                String[] pl_versions = new String[amount];

                for (int i = 0; i < amount; i++) {

                    pl_names[i] = local_dis.readUTF();
                    pl_authors[i] = local_dis.readUTF();
                    pl_versions[i] = local_dis.readUTF();

                }
                AutoPlugLogger.info("Received "+ amount +" plugins!");

                new OnlineConnection(local_socket, local_dis, local_dos, pl_names, pl_authors, pl_versions, amount);

            } catch (IOException e) {
                e.printStackTrace();
                AutoPlugLogger.warn(" [!] Error receiving plugin information [!]");
            }

        });
        newThread.start();



    }


}
