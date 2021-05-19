/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.network.online.connections.OnlineConsoleConnection;
import com.osiris.autoplug.client.network.online.connections.OnlineUserInputConnection;
import com.osiris.autoplug.client.network.online.connections.PluginsUpdaterConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Authenticates this client to the AutoPlug-Web server.
 * Must be extended by each connection.
 */
public class SecuredConnection {
    private Socket socket;
    private InputStream input;
    private OutputStream output;

    /**
     * Creates a new secured connection to the AutoPlug server.
     * Needs a connection type.
     *
     * @param con_type 0 = {@link MainConnection};
     *                 1 = {@link OnlineUserInputConnection};
     *                 2 = {@link OnlineConsoleConnection};
     *                 3 = {@link PluginsUpdaterConnection};
     * @throws Exception if authentication fails. Details are in the message.
     */
    public SecuredConnection(byte con_type) throws Exception {
        int counter = 1;
        while (counter < 11) {
            try {
                counter++;
                AL.debug(this.getClass(), "Connecting to AutoPlug-Web...");
                connect(GD.OFFICIAL_WEBSITE_IP, 35555);
                break;
            } catch (Exception ex) {
                //ex.printStackTrace();
                AL.warn("Error connecting to the AutoPlug-Web(" + counter + "/10). Retrying in 5 seconds...", ex);
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    AL.error(e);
                }
            }
        }

        if (socket == null || socket.isClosed() || !socket.isConnected())
            throw new Exception("Failed to connect to the online server after retrying 10 times. Please try again later.");

        DataInputStream dis = new DataInputStream(input);
        DataOutputStream dos = new DataOutputStream(output);

        AL.debug(this.getClass(), "Authenticating server-key from con-type: " + con_type);
        dos.writeUTF(new GeneralConfig().server_key.asString()); // Send server key
        dos.writeByte(con_type); // Send connection type

        byte response = dis.readByte(); // Get response
        switch (response) {
            case 0:
                AL.debug(this.getClass(), "Authentication succeeded!");
                break;
            case 1:
                throw new Exception("Authentication failed (code:" + response + "): No matching server key found! Register your server at " + GD.OFFICIAL_WEBSITE + " and get your server-key. Restart AutoPlug when done.");
            case 2:
                throw new Exception("Authentication failed (code:" + response + "): Another client with this server key is already connected! Close that connection and restart AutoPlug.");
            case 3:
                throw new Exception("Authentication failed (code:" + response + "): Make sure that the primary connection is established before all the secondary connections!");
            case 4:
                throw new Exception("Authentication failed (code:" + response + "): Unknown connection type! Make sure that AutoPlug is up-to-date!");
            case 5:
                throw new Exception("Authentication failed (code:" + response + "): No user account found for the provided server key!");
            default:
                throw new Exception("Authentication failed (code:" + response + "): Unknown error code " + response + ". Make sure that AutoPlug is up-to-date!");
        }
    }

    /**
     * Connects to a server with SSL.
     * After this you can use the get methods.
     *
     * @param host Server ip-address.
     * @param port Server port.
     * @throws Exception
     */
    public void connect(String host, int port) throws Exception {
        //SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        SocketFactory factory = SSLSocketFactory.getDefault();
        socket = factory.createSocket(host, port);

        //System.setProperty("javax.net.debug", "all");
        ((SSLSocket) socket).setEnabledProtocols(
                new String[]{"TLSv1.2"});
        ((SSLSocket) socket).getSSLParameters().setEndpointIdentificationAlgorithm("HTTPS");

        registerHandshakeCallback(socket);

        ((SSLSocket) socket).startHandshake();

        socket.setSoTimeout(30000);
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    private void registerHandshakeCallback(Socket socket) {
        ((SSLSocket) socket).addHandshakeCompletedListener(
                new HandshakeCompletedListener() {
                    public void handshakeCompleted(
                            HandshakeCompletedEvent event) {
                        AL.debug(this.getClass(),
                                "Handshake finished!");
                        AL.debug(this.getClass(),
                                "CipherSuite:" + event.getCipherSuite());
                        AL.debug(this.getClass(),
                                "SessionId " + event.getSession());
                        AL.debug(this.getClass(),
                                "PeerHost " + event.getSession().getPeerHost());
                    }
                }
        );
    }

    public Socket getSocket() {
        return socket;
    }

    public InputStream getInput() {
        return input;
    }

    public OutputStream getOutput() {
        return output;
    }
}
