/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.console.AutoPlugConsole;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * The user can send commands through the online console.<br>
 * For that we got this connection, which listens for the user
 * input at the online console and executes it.
 */
public class OnlineConsoleReceiveConnection extends SecondaryConnection {
    @Nullable
    private static Thread thread;

    public OnlineConsoleReceiveConnection() {
        super((byte) 1);
    }

    @Override
    public boolean open() throws Exception {
        super.open();
        if (thread == null)
            thread = new Thread(() -> {
                try {
                    Socket socket = getSocket();
                    socket.setSoTimeout(0);
                    InputStream in = getSocket().getInputStream();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        String line;
                        while (!socket.isClosed() && (line = reader.readLine()) != null) {
                            AutoPlugConsole.executeCommand(line);
                            AL.info("Executed Web-Command: " + line);
                        }
                    }
                } catch (Exception e) {
                    AL.warn(this.getClass(), e);
                }

            });
        thread.start();
        return true;
    }

    @Override
    public void close() throws IOException {
        try {
            if (thread != null && !thread.isInterrupted()) thread.interrupt();
        } catch (Exception e) {
            AL.warn("Failed to stop thread.", e);
        }
        thread = null;

        try {
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }
    }
}
