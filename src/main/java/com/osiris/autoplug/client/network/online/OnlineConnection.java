/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class OnlineConnection {

    public OnlineConnection(Socket local_socket, DataInputStream local_dis, DataOutputStream local_dos,
                            String[] pl_names, String[] pl_authors, String[] pl_versions, int amount){

        Thread newThread = new Thread(() -> {

            try {
                AutoPlugLogger.info("Connecting to online server...");
                Socket online_socket = new Socket("144.91.78.158",35555);
                DataInputStream online_dis = new DataInputStream(online_socket.getInputStream());
                DataOutputStream online_dos = new DataOutputStream(online_socket.getOutputStream());
                AutoPlugLogger.info("Connected to the AutoPlug-Server successfully!");

                new OnlineTaskUpdatePlugins(
                        online_socket, online_dis, online_dos,
                        local_socket, local_dis, local_dos,
                        pl_names, pl_authors, pl_versions,
                        amount);
            } catch (Exception ex) {
                ex.printStackTrace();
                AutoPlugLogger.warn(" [!] Error connecting to the Online-Server at "+ GD.OFFICIAL_WEBSITE+" [!]");
                AutoPlugLogger.warn(" [!] In most cases we are just performing updates and the website is up again after 2min [!]");
                AutoPlugLogger.warn(" [!] So please wait and try again later. If you still get this error, notify our Team [!]");
            }

        });
        newThread.start();

    }

}
