/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleReceive;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleSend;
import com.osiris.autoplug.client.network.online.connections.ConServerStatus;
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

    // Secondary connections:
    public static ConOnlineConsoleReceive CON_CONSOLE_RECEIVE;
    public static ConOnlineConsoleSend CON_CONSOLE_SEND;
    public static ConServerStatus CON_SERVER_STATUS;
    //public static PluginsUpdateResultConnection CON_PLUGINS_UPDATER;

    public static boolean isDone = false; // So that the log isn't a mess because of the processes which start right after this.

    @Override
    public void run() {
        try {
            super.run();
            AL.info("Authenticating server...");
            SecuredConnection auth = new SecuredConnection((byte) 0);
            AL.info("Authentication success!");
            //Socket socket = auth.getSocket();
            DataInputStream dis = new DataInputStream(auth.getInput());
            //DataOutputStream dos = new DataOutputStream(auth.getOut());
            boolean isUserAuthenticated;

             /*
                Create child connection objects, after main connection was established successfully.
                Note: To establish a connection to the server, the open() method
                must have been called before.
                 */
            CON_CONSOLE_RECEIVE = new ConOnlineConsoleReceive();
            CON_CONSOLE_SEND = new ConOnlineConsoleSend();
            CON_SERVER_STATUS = new ConServerStatus();
            //CON_PLUGINS_UPDATER = new PluginsUpdateResultConnection();

            boolean sendStatus = new WebConfig().send_server_status.asBoolean();

            isDone = true;
            boolean oldAuth = false; // Local variable that holds the auth boolean before the current one
            while (true) {
                // It can happen that we don't get a response because the web server is offline
                // In that case see the catch statement
                try {
                    while (true) {
                        isUserAuthenticated = dis.readBoolean();
                        if (sendStatus && !CON_SERVER_STATUS.isConnected()) CON_SERVER_STATUS.open();

                        if (isUserAuthenticated) {
                            if (!oldAuth) {
                                oldAuth = true;
                                AL.debug(this.getClass(), "User is online.");
                                // User is online, so open secondary connections if they weren't already
                                if (!CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.open();
                                if (!CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.open();
                                //if (!CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.open(); Only is used at restarts!
                            }
                        } else {
                            if (oldAuth) {
                                oldAuth = false;
                                AL.debug(this.getClass(), "User is offline.");
                                // Close secondary connections when user is offline/logged out
                                if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close();
                                if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close();
                                //if (CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.close(); Only is used at restarts!
                            }
                        }
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    AL.warn("Lost connection to AutoPlug-Web! Reconnecting in 30 seconds...", e);

                    // Reset booleans
                    oldAuth = false;
                    isUserAuthenticated = false;

                    // Make sure socket is really closed
                    try {
                        if (auth.getSocket() != null && !auth.getSocket().isClosed())
                            auth.getSocket().close();
                    } catch (IOException ioException) {
                        AL.warn(ioException);
                    }

                    // Close child connections
                    try {
                        if (CON_CONSOLE_RECEIVE != null && CON_CONSOLE_RECEIVE.isConnected())
                            CON_CONSOLE_RECEIVE.close();
                    } catch (IOException e1) {
                        AL.warn(e1);
                    }

                    try {
                        if (CON_CONSOLE_SEND != null && CON_SERVER_STATUS.isConnected())
                            CON_CONSOLE_SEND.close();
                    } catch (IOException e1) {
                        AL.warn(e1);
                    }

                    try {
                        if (CON_SERVER_STATUS != null && CON_SERVER_STATUS.isConnected())
                            CON_SERVER_STATUS.close();
                    } catch (IOException e1) {
                        AL.warn(e1);
                    }


                    Thread.sleep(30000);
                    try {
                        AL.info("Authenticating server...");
                        auth = new SecuredConnection((byte) 0);
                        dis = new DataInputStream(auth.getInput());
                        AL.info("Authentication success!");
                    } catch (Exception exception) {
                        AL.warn(e);
                    }
                }
            }
        } catch (Exception e) {
            AL.warn(this.getClass(), e, "Connection issues!");
            isDone = true;
        }
    }
}
