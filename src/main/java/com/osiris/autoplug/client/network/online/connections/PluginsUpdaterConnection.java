/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThreadManager;

import java.net.Socket;

/**
 * This is a temporary connection, which gets closed after
 * finishing its tasks.
 * It starts a {@link com.osiris.betterthread.BetterThread} which is attached to the given {@link BetterThreadManager} (or creates a new Manager if null).
 */
public class PluginsUpdaterConnection extends SecondaryConnection {

    public PluginsUpdaterConnection(){
        super((byte) 3);
    }

    @Override
    public boolean open() throws Exception {
        if (super.open()){
            try{
                Socket socket = getSocket();
                socket.setSoTimeout(0);
            } catch (Exception e) {
                AL.warn(e);
            }
            return true;
        }
        else
            return false;

    }
}
