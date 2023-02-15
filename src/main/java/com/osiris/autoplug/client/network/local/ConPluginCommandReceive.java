/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.local;


import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.console.AutoPlugConsole;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Listens on a port for commands sent by the AutoPlug-Plugin. <br>
 * Each received command gets validated, before being executed. <br>
 */
public class ConPluginCommandReceive {

    /**
     * Class execution order is like this:
     * LocalListener ->
     * LocalConnectionValidator ->
     * LocalTaskReceivePlugins ->
     * OnlineConnection ->
     * OnlineTaskUpdatePlugins
     */
    public ConPluginCommandReceive() {

        Thread newThread = new Thread(() -> {

            try {
                ServerSocket local_server_socket = null;
                //Search for a free port starting at 35565 and connect to it
                int port = 35565;
                while (local_server_socket == null) {
                    try {
                        AL.debug(this.getClass(), "Binding on localhost:" + port + " for AutoPlug-Plugin...");
                        local_server_socket = new ServerSocket(port);
                        AL.debug(this.getClass(), "Success!");
                    } catch (IOException e) {
                        AL.debug(this.getClass(), "Failed to bind on port " + port + "! " + e.getMessage());
                        local_server_socket = null;
                        port++;
                    }
                    if (port == 35665) {
                        AL.warn("Failed to bind on a port. Tried 100 ports between 35565 and 35665.");
                        return;
                    }
                }

                while (true) {

                    //This blocks the thread till a client connects
                    AL.debug(this.getClass(), "Waiting for AutoPlug-Plugin to connect...");
                    Socket socket = local_server_socket.accept();

                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                    String key = new SystemConfig().autoplug_plugin_key.asString();
                    dos.writeUTF(key);
                    if (!dis.readUTF().equals(key)) {
                        socket.close();
                    } else {
                        socket.setSoTimeout(0);
                        AL.info("AutoPlug-Plugin with matching private plugin key connected.");

                        Thread thread = new Thread(() -> {
                            try {
                                InputStream in = socket.getInputStream();
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                                    String line;
                                    while (!socket.isClosed() && (line = reader.readLine()) != null) {
                                        AL.info("Received Plugin-Command: " + line);
                                        if (!AutoPlugConsole.executeCommand(line))
                                            Server.submitCommand(line);
                                    }
                                }
                            } catch (Exception e) {
                                AL.warn(this.getClass(), e);
                            }

                        });
                        thread.start();
                    }
                }
            } catch (IOException | YamlWriterException | NotLoadedException | IllegalKeyException |
                     DuplicateKeyException | YamlReaderException | IllegalListException e) {
                e.printStackTrace();
            }

        });
        newThread.start();

    }


}
