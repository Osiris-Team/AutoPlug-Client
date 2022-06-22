/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.network.online.connections.*;
import com.osiris.autoplug.core.logger.AL;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * This is the main connection to AutoPlugs online server/website.
 * It stays active all the time and sends/receives true/false values, regarding the users login status.
 * When the user is logged in, it creates the secondary connections and holds them until the user logs out.
 * The main connection authenticates using the server_key.
 * If it receives a true boolean it means that the user is logged in and opens new connections.
 */
public class ConMain extends Thread {
    public static final ConSendPublicDetails CON_PUBLIC_DETAILS = new ConSendPublicDetails();

    // Secondary connections:
    public static final ConOnlineConsoleReceive CON_CONSOLE_RECEIVE = new ConOnlineConsoleReceive();
    public static final ConOnlineConsoleSend CON_CONSOLE_SEND = new ConOnlineConsoleSend();
    public static final ConSendPrivateDetails CON_PRIVATE_DETAILS = new ConSendPrivateDetails();
    public static final ConFileManager CON_FILE_MANAGER = new ConFileManager();
    public static boolean isDone = false; // So that the log isn't a mess because of the processes which start right after this.
    public static boolean isLoggedIn = false;
    public static boolean isLoggedInOld = false; // Local variable that holds the auth boolean before the current one
    public SecuredConnection con;
    public DataInputStream dis;

    @Override
    public void run() {
        super.run();
        try {
            AL.info("Authenticating server...");
            con = new SecuredConnection((byte) 0);
            AL.info("Authentication success!");
            dis = new DataInputStream(con.getInput());
            CON_PUBLIC_DETAILS.open();
            isDone = true;
        } catch (Exception e) {
            AL.warn(e);
            isDone = true;
            return;
        }
        int msUntilRetry = 30000;
        while (true) {
            try {
                if (con == null || !con.isAlive()) {
                    AL.info("Authenticating server...");
                    con = new SecuredConnection((byte) 0);
                    AL.info("Authentication success!");
                    dis = new DataInputStream(con.getInput());
                    CON_PUBLIC_DETAILS.open();
                    msUntilRetry = 30000;
                }

                isDone = true;
                while (true) {
                    isLoggedIn = dis.readBoolean();
                    if (isLoggedIn) {
                        if (!isLoggedInOld) {
                            AL.debug(this.getClass(), "Owner/Staff is online/active.");
                            // User is online, so open secondary connections if they weren't already
                            if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close();
                            CON_CONSOLE_RECEIVE.open();
                            if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close();
                            CON_CONSOLE_SEND.open();
                            if (CON_FILE_MANAGER.isConnected()) CON_FILE_MANAGER.close();
                            CON_FILE_MANAGER.open();
                            if (CON_PRIVATE_DETAILS.isConnected()) CON_PRIVATE_DETAILS.close();
                            CON_PRIVATE_DETAILS.open();
                            //if (!CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.open(); Only is used at restarts!
                        }
                    } else {
                        if (isLoggedInOld) {
                            AL.debug(this.getClass(), "Owner/Staff is offline/inactive.");
                            // Close secondary connections when user is offline/logged out
                            if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close();
                            if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close();
                            if (CON_FILE_MANAGER.isConnected()) CON_FILE_MANAGER.close();
                            if (CON_PRIVATE_DETAILS.isConnected()) CON_PRIVATE_DETAILS.close();
                            //if (CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.close(); Only is used at restarts!
                        }
                    }
                    isLoggedInOld = isLoggedIn;
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                isDone = true;
                AL.warn(e);

                // Reset booleans
                isLoggedInOld = false;
                isLoggedIn = false;
                closeAll();
                if (con == null || con.errorCode == 0) {
                    AL.warn("Connection problems! Reconnecting in " + msUntilRetry / 1000 + " seconds...");
                    try {
                        Thread.sleep(msUntilRetry);
                        msUntilRetry += 30000;
                    } catch (Exception exception) {
                        AL.warn(exception);
                        AL.warn("Connection problems, unexpected error! Reconnect manually by entering '.con reload'.");
                        break;
                    }
                } else {
                    AL.warn("Connection problems! Reconnect manually by entering '.con reload'.");
                    break;
                }
            }
        }
    }

    public boolean isConnected() {
        return con != null && con.getSocket() != null && !con.getSocket().isClosed() && con.getSocket().isConnected();
    }

    public void closeAll() {
        // Make sure socket is really closed
        try {
            if (con != null && con.getSocket() != null && !con.getSocket().isClosed())
                con.getSocket().close();
        } catch (IOException ioException) {
            AL.warn(ioException);
        }

        // Close child connections
        try {
            if (CON_CONSOLE_RECEIVE.isConnected())
                CON_CONSOLE_RECEIVE.close();
        } catch (IOException e1) {
            AL.warn(e1);
        }

        try {
            if (CON_CONSOLE_SEND.isConnected())
                CON_CONSOLE_SEND.close();
        } catch (IOException e1) {
            AL.warn(e1);
        }

        try {
            if (CON_PUBLIC_DETAILS.isConnected())
                CON_PUBLIC_DETAILS.close();
        } catch (IOException e1) {
            AL.warn(e1);
        }

        try {
            if (CON_PRIVATE_DETAILS.isConnected())
                CON_PRIVATE_DETAILS.close();
        } catch (IOException e1) {
            AL.warn(e1);
        }

        try {
            if (CON_FILE_MANAGER.isConnected())
                CON_FILE_MANAGER.close();
        } catch (IOException e1) {
            AL.warn(e1);
        }
    }

}
