/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online;

import java.io.*;
import java.net.Socket;

/**
 * This class was built to be extended.
 * Can only be created if the MainConnection already was established.
 * If not the server will reject the connection.
 * Also this connection needs to be authenticated using the server key.
 * Active when the user is logged in on the website.
 */
public class SecondaryConnection {
    private final byte auth_id; // Very important to identify the connection
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;

    public SecondaryConnection(byte auth_id) {
        this.auth_id = auth_id;
    }

    public boolean open() throws Exception {
        SecuredConnection auth = new SecuredConnection(auth_id);
        socket = auth.getSocket();
        in = auth.getInput();
        out = auth.getOutput();
        dataIn = auth.getDataIn();
        dataOut = auth.getDataOut();
        return true;
    }

    public void close() throws IOException {
        if (in != null) in.close();
        if (out != null) out.close();
        if (isConnected()) socket.close();
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    public byte getAuthId() {
        return auth_id;
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

    public DataInputStream getDataIn() {
        return dataIn;
    }

    public DataOutputStream getDataOut() {
        return dataOut;
    }
}
