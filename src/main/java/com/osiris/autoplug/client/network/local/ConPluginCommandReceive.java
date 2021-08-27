/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.local;


import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.console.AutoPlugConsole;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.exceptions.*;

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
                        AL.debug(this.getClass(), "Binding on port " + port + "...");
                        local_server_socket = new ServerSocket(port);
                        AL.debug(this.getClass(), "Success!");
                    } catch (IOException e) {
                        AL.debug(this.getClass(), "Failed to bind on port " + port + "! " + e.getMessage());
                        local_server_socket = null;
                        port++;
                    }
                }

                while (true) {

                    //This blocks the thread till a client connects
                    AL.debug(this.getClass(), "Waiting for AutoPlug-Plugin to connect...");
                    Socket socket = local_server_socket.accept();

                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                    GeneralConfig config = new GeneralConfig();
                    dos.writeUTF(config.server_key.asString());
                    if (!dis.readUTF().equals(config.server_key.asString())) {
                        socket.close();
                    } else {
                        socket.setSoTimeout(0);
                        AL.info("AutoPlug-Plugin with matching Server-Key connected.");

                        Thread thread = new Thread(() -> {
                            try {
                                InputStream in = socket.getInputStream();
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                                    String line;
                                    while (!socket.isClosed() && (line = reader.readLine()) != null) {
                                        String sKey = line.split(" ")[0];
                                        String command = line.substring(line.indexOf(" ") + 1);

                                        if (sKey == null || !sKey.equals(config.server_key.asString())) {
                                            AL.warn("Received Plugin-Command without Server-Key or the Server-Keys didn't match. Command '" + command + "' was not executed!");
                                        } else {
                                            AL.info("Received Plugin-Command: " + command);
                                            if (!AutoPlugConsole.executeCommand(command))
                                                Server.submitCommand(command);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                AL.warn(this.getClass(), e);
                            }

                        });
                        thread.start();
                    }
                }
            } catch (IOException | DYWriterException | NotLoadedException | IllegalKeyException | DuplicateKeyException | DYReaderException | IllegalListException e) {
                e.printStackTrace();
            }

        });
        newThread.start();

    }


}
