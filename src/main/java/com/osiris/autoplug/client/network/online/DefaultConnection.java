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
import com.osiris.autoplug.client.network.online.connections.ConAutoPlugConsoleReceive;
import com.osiris.autoplug.client.network.online.connections.ConAutoPlugConsoleSend;
import com.osiris.autoplug.client.network.online.connections.ConPluginsUpdateResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSession;
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
public class DefaultConnection implements AutoCloseable {
    public final byte conType;
    public byte errorCode = 0;
    public Socket socket;
    public InputStream input;
    public OutputStream output;
    public DataInputStream dataIn;
    public DataOutputStream dataOut;


    /**
     * Creates a new secured connection to the AutoPlug server.
     * Needs a connection type.
     *
     * @param con_type 0 = {@link ConMain}; <br>
     *                 1 = {@link ConAutoPlugConsoleReceive}; <br>
     *                 2 = {@link ConAutoPlugConsoleSend}; <br>
     *                 3 = {@link ConPluginsUpdateResult}; <br>
     * @throws Exception if authentication fails. Details are in the message.
     */
    public DefaultConnection(byte con_type) throws Exception {
        this.conType = con_type;
        connect();
        if (errorCode == 2) { // Retry in 10 seconds because it might be
            // that we just reconnected (there is a timeout of 5 seconds for the old connection until it gets closed)
            Thread.sleep(10000); // at least 5 seconds
            connect();
        }
        throwError();
    }

    private int connect() throws Exception {
        while (true) {
            SystemConfig systemConfig = new SystemConfig();
            String ip = systemConfig.autoplug_web_ip.asString();
            int port = systemConfig.autoplug_web_port.asInt();
            AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Connecting to AutoPlug-Web (" + ip + ":" + port + ")...");
            if (systemConfig.autoplug_web_ssl.asBoolean())
                createSSLConnection(ip, port);
            else {
                createInsecureConnection(ip, port);
            }

            // DDOS protection
            int punishment = dataIn.readInt();
            if (punishment == 0) {
                AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Connected to AutoPlug-Web successfully!");
                break;
            }

            AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Connection to AutoPlug-Web throttled! Retrying in " + punishment / 1000 + " second(s).");
            Thread.sleep(punishment + 250); // + 250ms, just to be safe
        }

        AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Authenticating server with Server-Key...");
        dataOut.writeUTF(new GeneralConfig().server_key.asString()); // Send server key
        dataOut.writeByte(conType); // Send connection type

        this.errorCode = dataIn.readByte(); // Get response
        return errorCode;
    }

    private void throwError() throws Exception {
        switch (errorCode) {
            case 0:
                AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Authenticated server successfully!");
                break;
            case 1:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): No matching server key found! Register your server at " + GD.OFFICIAL_WEBSITE + ", get your server-key and add it to the /autoplug/general.yml config file. Enter '.con reload' to retry.");
            case 2:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): Another client with this server key is already connected! Close that connection and restart AutoPlug.");
            case 3:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): Make sure that the primary connection is established before all the secondary connections!");
            case 4:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): Unknown connection type! Make sure that AutoPlug is up-to-date!");
            case 5:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): No user account found for the provided server key!");
            case 6:
                String ip = dataIn.readUTF();
                String hostname = dataIn.readUTF();
                int port = dataIn.readInt();
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "):" +
                        " An already existing, registered, public server was found with the same ip and port! This server was set to private." +
                        " Details: ip=" + ip + " hostname=" + hostname + " port=" + port);
            case 7:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "):" +
                        " A severe error occurred at AutoPlug-Web. Please notify the developers!");
            default:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): Unknown error code " + errorCode + ". Make sure that AutoPlug is up-to-date!");
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

        SSLSession session = ((SSLSocket) socket).getSession();
        if (!session.isValid())
            throw new Exception("SSLSession is not valid!");

        AL.debug(DefaultConnection.class, "Valid SSL session created for con_type " + conType + ". Details: " + session);

        input = socket.getInputStream();
        output = socket.getOutputStream();
        dataIn = new DataInputStream(input);
        dataOut = new DataOutputStream(output);
    }

    public void createInsecureConnection(String host, int port) throws Exception {
        AL.warn("Creating unencrypted connection, transmitted data can be read by a third-party.");
        socket = new Socket(host, port);
        input = socket.getInputStream();
        output = socket.getOutputStream();
        dataIn = new DataInputStream(input);
        dataOut = new DataOutputStream(output);
    }

    public boolean isAlive() {
        return socket != null && !socket.isClosed() && socket.isConnected();
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

    @Override
    public void close() throws Exception {
        dataIn.close();
        dataOut.close();
        socket.close();
    }

    @Override
    public String toString() {
        return "DefaultConnection{" +
                "ssl=" + (socket != null && socket instanceof SSLSocket ? "true" : "false") +
                ", errorCode=" + errorCode +
                ", socket=" + socket +
                '}';
    }
}
