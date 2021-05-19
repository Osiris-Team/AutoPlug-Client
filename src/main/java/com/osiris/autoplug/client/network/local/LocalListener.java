/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.local;


import com.osiris.autoplug.core.logger.AL;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

@Deprecated
public class LocalListener {

    /**
     * Class execution order is like this:
     * LocalListener ->
     * LocalConnectionValidator ->
     * LocalTaskReceivePlugins ->
     * OnlineConnection ->
     * OnlineTaskUpdatePlugins
     */
    public LocalListener() {

        Thread newThread = new Thread(() -> {

            try {
                ServerSocket local_server_socket = null;
                //Search for a free port starting at 35565 and connect to it
                int port = 35565;
                while (local_server_socket == null) {

                    try {
                        AL.debug(this.getClass(), "Binding on port " + port + "...");
                        local_server_socket = new ServerSocket(port);
                        AL.debug(this.getClass(), "Success!");
                    } catch (IOException e) {
                        local_server_socket = null;
                        port++;
                        AL.warn("Failed to bind on port " + port + "! " + e.getMessage());
                    }
                }

                while (true) {

                    //This blocks the thread till a client connects
                    AL.info("Waiting for AutoPlugPlugin to connect...");
                    Socket pre_local_connection = local_server_socket.accept();

                    DataInputStream pre_local_dis = new DataInputStream(pre_local_connection.getInputStream());
                    DataOutputStream pre_local_dos = new DataOutputStream(pre_local_connection.getOutputStream());
                    AL.info("AutoPlugPlugin connected!");

                    new LocalConnectionValidator(pre_local_connection, pre_local_dis, pre_local_dos);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
        newThread.start();

    }


}
