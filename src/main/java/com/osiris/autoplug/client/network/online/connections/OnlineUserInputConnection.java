/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.core.logger.AL;

import java.io.DataInputStream;
import java.net.Socket;

public class OnlineUserInputConnection extends SecondaryConnection {

    public OnlineUserInputConnection(){
        super((byte) 1);
    }

    @Override
    public boolean open() throws Exception {
        if (super.open()){
            Thread thread = new Thread(()->{
                try{
                    Socket socket = getSocket();
                    socket.setSoTimeout(0);
                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    while(true){
                        String command = dis.readUTF();
                        Server.submitCommand(command);
                        AL.info("Got command: "+command);
                    }
                } catch (Exception e) {
                    AL.warn(this.getClass(), e);
                }

            });
            thread.start();
            return true;
        }
        else
            return false;
    }
}
