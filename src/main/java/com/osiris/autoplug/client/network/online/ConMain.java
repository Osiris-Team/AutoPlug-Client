/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.network.online.connections.*;
import com.osiris.jlib.logger.AL;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is the main connection to AutoPlugs online server/website.
 * It stays active all the time and sends/receives true/false values, regarding the users login status.
 * When the user is logged in, it creates the secondary connections and holds them until the user logs out.
 * The main connection authenticates using the server_key.
 * If it receives a true boolean it means that the user is logged in and opens new connections.
 */
public class ConMain extends DefaultConnection {
    public final ConSendPublicDetails CON_PUBLIC_DETAILS = new ConSendPublicDetails();


    // Secondary connections:
    public final ConAutoPlugConsoleReceive CON_CONSOLE_RECEIVE = new ConAutoPlugConsoleReceive();
    public final ConAutoPlugConsoleSend CON_CONSOLE_SEND = new ConAutoPlugConsoleSend();
    public final ConSystemConsoleSend CON_SYSTEM_CONSOLE_SEND = new ConSystemConsoleSend();
    public final ConSystemConsoleReceive CON_SYSTEM_CONSOLE_RECEIVE = new ConSystemConsoleReceive();
    public final ConSendPrivateDetails CON_PRIVATE_DETAILS = new ConSendPrivateDetails();
    public final ConFileManager CON_FILE_MANAGER = new ConFileManager();
    public boolean isDone = false; // So that the log isn't a mess because of the processes which start right after this.
    public AtomicBoolean isUserActive = new AtomicBoolean(false);
    public boolean isUserActiveOld = false; // Local variable that holds the auth boolean before the current one

    public int msUntilRetry = 30000;

    public ConMain() {
        super((byte) 0);
    }

    @Override
    public boolean open() {
        try {
            AL.info("Authenticating server...");
            super.open();
            AL.info("Authentication success!");
            socket.setSoTimeout(60000); // 60 seconds for when AP-Web is overloaded
            CON_PUBLIC_DETAILS.open();
            isDone = true;
        } catch (Exception e) {
            AL.warn(e);
            isDone = true;
            if (e instanceof InvalidKeyException)
                return false; // Abort directly since no valid key exists
            // else we continue below and retry the connection
        }
        super.setAndStartAsync(() -> {
            try {
                if (!super.isConnected()) {
                    AL.info("Authenticating server...");
                    super.open();
                    AL.info("Authentication success!");
                    CON_PUBLIC_DETAILS.open();
                    msUntilRetry = 30000;
                }

                isDone = true;
                while (true) {
                    isUserActive.set(super.in.readBoolean()); // Ping
                    super.out.writeBoolean(true); // Pong true/false doesn't matter

                    if (isUserActive.get()) {
                        if (!isUserActiveOld) {
                            AL.debug(this.getClass(), "Owner/Staff is online/active.");
                            // User is online, so open secondary connections if they weren't already
                            if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close();
                            CON_CONSOLE_RECEIVE.open();
                            if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close();
                            CON_CONSOLE_SEND.open();
                            if (CON_SYSTEM_CONSOLE_RECEIVE.isConnected()) CON_SYSTEM_CONSOLE_RECEIVE.close();
                            CON_SYSTEM_CONSOLE_RECEIVE.open();
                            if (CON_SYSTEM_CONSOLE_SEND.isConnected()) CON_SYSTEM_CONSOLE_SEND.close();
                            CON_SYSTEM_CONSOLE_SEND.open();
                            if (CON_FILE_MANAGER.isConnected()) CON_FILE_MANAGER.close();
                            CON_FILE_MANAGER.open();
                            if (CON_PRIVATE_DETAILS.isConnected()) CON_PRIVATE_DETAILS.close();
                            CON_PRIVATE_DETAILS.open();
                            //if (!CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.open(); Only is used at restarts!
                        }
                    } else {
                        if (isUserActiveOld) {
                            AL.debug(this.getClass(), "Owner/Staff is offline/inactive.");
                            // Close secondary connections when user is offline/logged out
                            if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close();
                            if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close();
                            if (CON_SYSTEM_CONSOLE_RECEIVE.isConnected()) CON_SYSTEM_CONSOLE_RECEIVE.close();
                            if (CON_SYSTEM_CONSOLE_SEND.isConnected()) CON_SYSTEM_CONSOLE_SEND.close();
                            if (CON_FILE_MANAGER.isConnected()) CON_FILE_MANAGER.close();
                            if (CON_PRIVATE_DETAILS.isConnected()) CON_PRIVATE_DETAILS.close();
                            //if (CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.close(); Only is used at restarts!
                        }
                    }
                    isUserActiveOld = isUserActive.get();
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                isDone = true;
                if (isClosing.get()) {
                    AL.info("Closed main connection to AutoPlug-Web.");
                    return;
                }

                // Since we didn't meant to close, create a new thread that tries to reconnect
                new Thread(() -> {
                    try {
                        while (true) {
                            AL.warn("Connection problems! Reconnecting in " + msUntilRetry / 1000 + " seconds...", e);
                            Thread.sleep(msUntilRetry);
                            try {
                                if (open())
                                    break;
                                else
                                    AL.warn("Failed to reconnect!");
                            } catch (Exception ex) {
                                AL.warn("Failed to reconnect!", ex);
                            }
                            msUntilRetry += 30000;
                        }
                    } catch (Exception exception) {
                        AL.warn("Connection problems, unexpected error! Reconnect manually by entering '.con reload'.", exception);
                    }
                }).start();

                close();
            }
        });
        return true; // Success
    }

    @Override
    public void close() {
        closeTempCons();
        closePermCons();
    }

    /**
     * Close connections that are alive all the time.
     */
    public void closePermCons() {
        try {
            // Reset booleans
            isUserActiveOld = false;
            isUserActive.set(false);
            super.close();
        } catch (Exception e) {
            AL.warn(e);
        }

        try {
            if (CON_PUBLIC_DETAILS.isConnected())
                CON_PUBLIC_DETAILS.close();
        } catch (Exception e1) {
            AL.warn(e1);
        }
    }

    /**
     * Close connections that are online alive when the user is logged in.
     */
    public void closeTempCons() {
        try {
            if (CON_CONSOLE_RECEIVE.isConnected())
                CON_CONSOLE_RECEIVE.close();
        } catch (Exception e1) {
            AL.warn(e1);
        }

        try {
            if (CON_CONSOLE_SEND.isConnected())
                CON_CONSOLE_SEND.close();
        } catch (IOException e1) {
            AL.warn(e1);
        }

        try {
            if (CON_PRIVATE_DETAILS.isConnected())
                CON_PRIVATE_DETAILS.close();
        } catch (Exception e1) {
            AL.warn(e1);
        }

        try {
            if (CON_FILE_MANAGER.isConnected())
                CON_FILE_MANAGER.close();
        } catch (Exception e1) {
            AL.warn(e1);
        }
    }

}
