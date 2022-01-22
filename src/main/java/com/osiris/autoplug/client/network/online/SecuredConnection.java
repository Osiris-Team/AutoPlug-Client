/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleReceive;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleSend;
import com.osiris.autoplug.client.network.online.connections.ConPluginsUpdateResult;
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
    public byte errorCode = 0;
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;


    /**
     * Creates a new secured connection to the AutoPlug server.
     * Needs a connection type.
     *
     * @param con_type 0 = {@link ConMain}; <br>
     *                 1 = {@link ConOnlineConsoleReceive}; <br>
     *                 2 = {@link ConOnlineConsoleSend}; <br>
     *                 3 = {@link ConPluginsUpdateResult}; <br>
     * @throws Exception if authentication fails. Details are in the message.
     */
    public SecuredConnection(byte con_type) throws Exception {
        this.conType = con_type;
        while (true) {
            SystemConfig systemConfig = new SystemConfig();
            String ip = systemConfig.autoplug_web_ip.asString();
            int port = systemConfig.autoplug_web_port.asInt();
            AL.debug(this.getClass(), "[CON_TYPE: " + con_type + "] Connecting to AutoPlug-Web (" + ip + ":" + port + ")...");
            if (systemConfig.autoplug_web_ssl.asBoolean())
                createSSLConnection(ip, port);
            else {
                createInsecureConnection(ip, port);
            }

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

        errorCode = dis.readByte(); // Get response
        switch (errorCode) {
            case 0:
                AL.debug(this.getClass(), "[CON_TYPE: " + con_type + "] Authenticated server successfully!");
                break;
            case 1:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + errorCode + "): No matching server key found! Register your server at " + GD.OFFICIAL_WEBSITE + " and get your server-key. Restart AutoPlug when done.");
            case 2:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + errorCode + "): Another client with this server key is already connected! Close that connection and restart AutoPlug.");
            case 3:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + errorCode + "): Make sure that the primary connection is established before all the secondary connections!");
            case 4:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + errorCode + "): Unknown connection type! Make sure that AutoPlug is up-to-date!");
            case 5:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + errorCode + "): No user account found for the provided server key!");
            case 6:
                String ip = dis.readUTF();
                String hostname = dis.readUTF();
                int port = dis.readInt();
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + errorCode + "):" +
                        " An already existing, registered, public server was found with the same ip and port! This server was set to private." +
                        " Details: ip=" + ip + " hostname=" + hostname + " port=" + port);
            case 7:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + errorCode + "):" +
                        " A severe error occurred at AutoPlug-Web. Please notify the developers!");
            default:
                throw new Exception("[CON_TYPE: " + con_type + "] Authentication failed (code:" + errorCode + "): Unknown error code " + errorCode + ". Make sure that AutoPlug is up-to-date!");
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
    public void createSSLConnection(String host, int port) throws Exception {
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

    public void createInsecureConnection(String host, int port) throws Exception {
        socket = new Socket(host, port);
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

    public boolean isAlive() {
        return socket != null && !socket.isClosed();
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
