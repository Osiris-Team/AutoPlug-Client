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

import java.io.*;
import java.net.Socket;

import static com.osiris.autoplug.client.utils.GD.MC_SERVER_IN;

/**
 * Read the InputStreams of AutoPlug and the Minecraft server and
 * send it to the AutoPlug server when the user is online.
 * Note that
 */
public class OnlineConsoleConnection extends SecondaryConnection {
    private static BufferedWriter bw;
    private static Thread thread;

    public OnlineConsoleConnection() {
        super((byte) 2);  // Each connection has its own auth_id.
    }

    @Override
    public boolean open() throws Exception {
        super.open();
        try{
            if (bw==null){
                Socket socket = getSocket();
                socket.setSoTimeout(0);
                bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            }
        } catch (Exception e) {
            AL.warn(e);
        }

        if (thread==null){
            thread = new Thread(()->{
                try{
                    while(true){
                        if (MC_SERVER_IN!=null){
                            byte counter = 0;
                            InputStreamReader isr = new InputStreamReader(MC_SERVER_IN);
                            BufferedReader br = new BufferedReader(isr);
                            while(true)
                                try {
                                    send(br.readLine());
                                } catch (Exception e) {
                                    counter++;
                                    if (counter<3)
                                        AL.warn("Failed to send message to online console!", e);
                                }
                        }
                        else {
                            AL.debug(this.getClass(), "Error while trying to fetch Minecraft servers InputStream: It's null. Retrying in 10 seconds...");
                            Thread.sleep(10000);
                        }

                    }

                } catch (Exception e) {
                    AL.warn(e);
                }


            });
            thread.setName("MinecraftServer-InputStreamReader-Thread");
            thread.start();
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        if(thread.isAlive() && !thread.isInterrupted()) {
            thread.interrupt();
            thread = null;
            try{bw.close();} catch (Exception ignored){};
            bw = null;
        }
        super.close();
    }

    public synchronized void send(String message) throws Exception{
        bw.write(message);
        bw.flush();
        AL.info("SENT LINE: "+message);
    }
}
