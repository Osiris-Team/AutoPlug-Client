/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.utils;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Authenticates this clients to the AutoPlug server.
 * Must be extended by each connection.
 */
public class ClientAuthenticationAtServer {
    private boolean success = false;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    /**
     * Creates a secured connection to the AutoPlug server.
     * Needs a connection type.
     * @param con_type
     */
    public ClientAuthenticationAtServer(byte con_type) throws Exception{
        SSLConnection connection = null;
        int counter = 0;
        while(counter < 10){
            try {
                counter++;
                AL.debug(this.getClass(), "Connecting to AutoPlug-Web...");
                connection = new SSLConnection(GD.OFFICIAL_WEBSITE_IP,35555);
                break;
            } catch (Exception ex) {
                //ex.printStackTrace();
                AL.warn("Error connecting to the AutoPlug-Web("+counter+"/10). Retrying in 5 seconds...", ex);
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    AL.error(e);
                }
            }
        }

        if (connection==null)
            throw new Exception("Failed to connect to the online server after retrying 10 times. Please try again later.");

        socket = connection.getSocket();
        in = connection.getInput();
        out = connection.getOutput();
        DataInputStream dis = new DataInputStream(in);
        DataOutputStream dos = new DataOutputStream(out);

        AL.debug(this.getClass(),"Authenticating server-key from con-type: "+con_type);
        dos.writeUTF(new GeneralConfig().server_key.asString()); // Send server key
        dos.writeByte(con_type); // Send connection type

        byte response = dis.readByte(); // Get response
        switch (response) {
            case 0:
                AL.debug(this.getClass(), "Authentication succeeded!");
                success = true;
                break;
            case 1:
                throw new Exception("Authentication failed (code:"+response+"): No matching server key found! Register your server at " + GD.OFFICIAL_WEBSITE + " and get your server-key. Restart AutoPlug when done.");
            case 2:
                throw new Exception("Authentication failed (code:"+response+"): Another client with this server key is already connected! Close that connection and restart AutoPlug.");
            case 3:
                throw new Exception("Authentication failed (code:"+response+"): Make sure that the primary connection is established before all the secondary connections!");
            case 4:
                throw new Exception("Authentication failed (code:"+response+"): Unknown connection type! Make sure that AutoPlug is up-to-date!");
            case 5:
                throw new Exception("Authentication failed (code:"+response+"): No user account found for the provided server key!");
            default:
                throw new Exception("Authentication failed (code:"+response+"): Unknown error code " + response + ". Make sure that AutoPlug is up-to-date!");
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public Socket getSocket() {
        return socket;
    }

    public InputStream getIn() {
        return in;
    }

    public OutputStream getOut() {
        return out;
    }
}
