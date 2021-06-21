/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.network.online.connections.OnlineConsoleReceiveConnection;
import com.osiris.autoplug.client.network.online.connections.OnlineConsoleSendConnection;
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the main connection to AutoPlugs online server/website.
 * It stays active all the time and sends/receives true/false values, regarding the users login status.
 * When the user is logged in, it creates the secondary connections and holds them until the user logs out.
 * The main connection authenticates using the server_key.
 * If it receives a true boolean it means that the user is logged in and opens new connections.
 */
public class MainConnection extends Thread {

    // Secondary connections:
    public static OnlineConsoleReceiveConnection CON_CONSOLE_RECEIVE;
    public static OnlineConsoleSendConnection CON_CONSOLE_SEND;
    //public static PluginsUpdateResultConnection CON_PLUGINS_UPDATER;
    @NotNull
    public static List<SecondaryConnection> LIST_SECONDARY_CONNECTIONS = new ArrayList<>();

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
            boolean user_online;

                /*
                Create child connection objects, after main connection was established successfully.
                Note: To establish a connection to the server, the open() method
                must have been called before.
                 */
            CON_CONSOLE_RECEIVE = new OnlineConsoleReceiveConnection();
            CON_CONSOLE_SEND = new OnlineConsoleSendConnection();
            //CON_PLUGINS_UPDATER = new PluginsUpdateResultConnection();

            // Add to connections
            LIST_SECONDARY_CONNECTIONS.add(CON_CONSOLE_RECEIVE);
            LIST_SECONDARY_CONNECTIONS.add(CON_CONSOLE_SEND);
            //LIST_SECONDARY_CONNECTIONS.add(CON_PLUGINS_UPDATER);


            isDone = true;
            boolean msgOnline = false; // Was the online message already send to log?
            boolean msgOffline = false;
            while (true) {
                // It can happen that we don't get a response because the web server is offline
                // In that case see the catch statement
                try {
                    while (true) {
                        user_online = dis.readBoolean();
                        if (user_online) {
                            if (!msgOnline) {
                                AL.debug(this.getClass(), "User is online!");
                                msgOnline = true;
                                msgOffline = false;
                            }

                            // User is online, so open secondary connections if they weren't already
                            if (!CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.open();
                            if (!CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.open();
                            //if (!CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.open(); Only is used at restarts!
                        } else {
                            if (!msgOffline) {
                                AL.debug(this.getClass(), "User logged out!");
                                msgOffline = true;
                                msgOnline = false;
                            }

                            // Close secondary connections when user is offline/logged out
                            if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close();
                            if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close();
                            //if (CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.close(); Only is used at restarts!
                        }
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    AL.warn("Lost connection to AutoPlug-Web! Reconnecting in 30 seconds...", e);

                    // Close child connections
                    try {
                        CON_CONSOLE_RECEIVE.close();
                    } catch (IOException e1) {
                        AL.warn(e1);
                    }

                    try {
                        CON_CONSOLE_SEND.close();
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
