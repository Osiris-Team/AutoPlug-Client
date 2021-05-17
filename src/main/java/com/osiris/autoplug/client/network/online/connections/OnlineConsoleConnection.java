/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.client.utils.NonBlockingPipedInputStream;
import com.osiris.autoplug.core.logger.AL;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;


/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class OnlineConsoleConnection extends SecondaryConnection {
    private static BufferedWriter bw;
    private static final NonBlockingPipedInputStream.WriteLineEvent<String> action = line -> {
        try {
            send(line);
        } catch (Exception e) {
            AL.warn("Failed to send message to online console!", e);
        }
    };

    public OnlineConsoleConnection() {
        super((byte) 2);  // Each connection has its own auth_id.
    }

    public static synchronized void send(String message) throws Exception {
        bw.write(message);
        bw.flush();
        AL.debug(OnlineConsoleConnection.class, "SENT LINE: " + message);
    }

    @Override
    public boolean open() throws Exception {
        super.open();
        try {
            if (bw == null) {
                Socket socket = getSocket();
                socket.setSoTimeout(0);
                bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            }
        } catch (Exception e) {
            AL.warn(e);
        }

        Main.PIPED_IN.actionsOnWriteLineEvent.add(action);
        return true;
    }

    @Override
    public void close() throws IOException {
        Main.PIPED_IN.actionsOnWriteLineEvent.remove(action);

        try {
            bw.close();
        } catch (Exception e) {
            AL.warn("Failed to close writer.", e);
        }

        try {
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }

        bw = null;
    }
}
