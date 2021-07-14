/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.network.online.connections.OnlineConsoleReceiveConnection;
import com.osiris.autoplug.client.network.online.connections.OnlineConsoleSendConnection;
import com.osiris.autoplug.client.network.online.connections.PluginsUpdateResultConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.NotNull;

import javax.net.SocketFactory;
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
    private final byte conType;
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;


    /**
     * Creates a new secured connection to the AutoPlug server.
     * Needs a connection type.
     *
     * @param con_type 0 = {@link MainConnection}; <br>
     *                 1 = {@link OnlineConsoleReceiveConnection}; <br>
     *                 2 = {@link OnlineConsoleSendConnection}; <br>
     *                 3 = {@link PluginsUpdateResultConnection}; <br>
     * @throws Exception if authentication fails. Details are in the message.
     */
    public SecuredConnection(byte con_type) throws Exception {
        this.conType = con_type;
        while (true) {
            AL.debug(this.getClass(), "[CON_TYPE: " + con_type + "] Connecting to AutoPlug-Web...");
            connect(GD.OFFICIAL_WEBSITE_IP, 35555);

            // DDOS protection
            int punishment = dataIn.readInt();
            if (punishment == 0) {
                AL.debug(this.getClass(), "[CON_TYPE: " + con_type + "] Connected to AutoPlug-Web successfully!");
                break;
            }

            AL.debug(this.getClass(), "[CON_TYPE: " + con_type + "] Connection to AutoPlug-Web throttled! Retrying in " + punishment / 1000 + " second(s).");
            Thread.sleep(punishment + 250); // + 250ms, just to be safe
        }

        DataInputStream dis = new DataInputStream(input);
        DataOutputStream dos = new DataOutputStream(output);

        AL.debug(this.getClass(), "[CON_TYPE: " + con_type + "] Authenticating server with Server-Key...");
        dos.writeUTF(new GeneralConfig().server_key.asString()); // Send server key
        dos.writeByte(con_type); // Send connection type

        byte response = dis.readByte(); // Get response
        switch (response) {
            case 0:
                AL.debug(this.getClass(), "[CON_TYPE: " + con_type + "] Authenticated server successfully!");
                break;
            case 1:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + response + "): No matching server key found! Register your server at " + GD.OFFICIAL_WEBSITE + " and get your server-key. Restart AutoPlug when done.");
            case 2:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + response + "): Another client with this server key is already connected! Close that connection and restart AutoPlug.");
            case 3:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + response + "): Make sure that the primary connection is established before all the secondary connections!");
            case 4:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + response + "): Unknown connection type! Make sure that AutoPlug is up-to-date!");
            case 5:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + response + "): No user account found for the provided server key!");
            case 6:
                String ip = dis.readUTF();
                String hostname = dis.readUTF();
                int port = dis.readInt();
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + response + "):" +
                        " An already existing, registered, public server was found with the same ip and port! This server was set to private." +
                        " Details: ip=" + ip + " hostname=" + hostname + " port=" + port);
            case 7:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + response + "):" +
                        " A severe error occurred at AutoPlug-Web. Please notify the developers!");
            default:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + response + "): Unknown error code " + response + ". Make sure that AutoPlug is up-to-date!");
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
        dataIn = new DataInputStream(input);
        dataOut = new DataOutputStream(output);
    }

    private void registerHandshakeCallback(@NotNull Socket socket) {
        ((SSLSocket) socket).addHandshakeCompletedListener(event -> {
                    AL.debug(SecuredConnection.class, "[CON_TYPE: " + conType + "] Handshake finished!");
                    AL.debug(SecuredConnection.class, "[CON_TYPE: " + conType + "] CipherSuite:" + event.getCipherSuite());
                    AL.debug(SecuredConnection.class, "[CON_TYPE: " + conType + "] SessionId " + event.getSession());
                    AL.debug(SecuredConnection.class, "[CON_TYPE: " + conType + "] PeerHost " + event.getSession().getPeerHost());
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

    public DataInputStream getDataIn() {
        return dataIn;
    }

    public DataOutputStream getDataOut() {
        return dataOut;
    }
}
