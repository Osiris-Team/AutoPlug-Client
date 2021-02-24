/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.network.utils;

import com.osiris.autoplug.core.logger.AL;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Establish a connection to a server with SSL protection.
 */
public class SSLConnection {
    private Socket socket;
    private InputStream input;
    private OutputStream output;

    public SSLConnection(String host, int port) throws Exception {
        connect(host, port);
    }

    /**
     * Connects to a server with SSL.
     * After this you can use the get methods.
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
                new String[] {"TLSv1.2"});
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
