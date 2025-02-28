/*
 * Copyright (c) 2021-2024 Osiris-Team.
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
import com.osiris.autoplug.client.utils.UtilsLists;
import com.osiris.jlib.logger.AL;
import com.osiris.jprocesses2.JProcess;
import com.osiris.jprocesses2.ProcessUtils;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Authenticates this client to the AutoPlug-Web server.
 * Must be extended by each connection.
 */
public class DefaultConnection implements AutoCloseable {
    public static final String NO_KEY = "NO_KEY";
    public final byte conType;
    public byte errorCode = 0;
    public Socket socket;
    public InputStream input;
    public OutputStream output;
    public DataInputStream in;
    public DataOutputStream out;
    public AtomicBoolean isClosing = new AtomicBoolean(false);
    private Thread thread;


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
    public DefaultConnection(byte con_type) {
        this.conType = con_type;
    }

    /**
     *
     */
    public void setSmartRunnable(RunnableWithException runnable) {

    }

    /**
     * Interrupts the old thread and sets and starts a new thread.
     * The provided runnable is encapsulated in another one inside a try/catch
     * that catches and ignores Exceptions caused by {@link #close()}.
     */
    public synchronized void setAndStartAsync(RunnableWithException runnable) {
        if (this.thread != null)
            this.thread.interrupt();
        // Save instances to make sure NOT to close the wrong ones later.
        Socket _socket = this.socket;
        InputStream _in = this.in;
        OutputStream _out = this.out;
        this.thread = new Thread(() -> {
            try {
                runnable.run();
            } catch (Exception e) { // Exceptions caused by close() are ignored
                if (!isClosing.get()) AL.warn(e);
                try {
                    _close(Thread.currentThread(), _in, _out, _socket);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        this.thread.start();
    }

    public synchronized boolean open() throws Exception {
        AL.debug(this.getClass(), "open()");
        _open();
        if (errorCode == 2) { // Retry in 10 seconds because it might be
            // that we just reconnected (there is a timeout of 5 seconds for the old connection until it gets closed)
            Thread.sleep(10000); // at least 5 seconds
            _open();
        }
        throwError();
        return errorCode == 0;
    }

    private synchronized int _open() throws Exception {
        isClosing.set(false);
        errorCode = 0;
        close();
        isClosing.set(false);
        String serverKey = new GeneralConfig().server_key.asString();
        if (serverKey == null || serverKey.equals("INSERT_KEY_HERE") ||
                serverKey.equals("null") ||
                serverKey.equals(NO_KEY))
            throw new InvalidKeyException("No valid key provided." +
                    " Register your server at " + GD.OFFICIAL_WEBSITE + ", get your server-key and add it to the /autoplug/general.yml config file." +
                    " Enter '.con reload' to retry.");
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
            int punishment = in.readInt();
            if (punishment == 0) {
                AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Connected to AutoPlug-Web successfully!");
                break;
            }

            AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Connection to AutoPlug-Web throttled! Retrying in " + punishment / 1000 + " second(s).");
            Thread.sleep(punishment + 250); // + 250ms, just to be safe
        }

        AL.debug(this.getClass(), "[CON_TYPE: " + conType + "] Authenticating server with Server-Key...");
        socket.setSoTimeout(60000);
        out.writeUTF(serverKey); // Send server key
        out.writeByte(conType); // Send connection type
        this.errorCode = in.readByte(); // Get response
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
                List<JProcess> list = new ArrayList<>();
                Exception ex = null;
                try {
                    for (JProcess p : new ProcessUtils().getProcesses()) {
                        if (p.name != null && p.name.toLowerCase().contains("autoplug")) {
                            list.add(p);
                        } else if (p.command != null && p.command.toLowerCase().contains("autoplug")) {
                            list.add(p);
                        }
                    }
                } catch (Exception e) {
                    ex = e;
                }

                StringBuilder sb = new StringBuilder();
                if (!list.isEmpty()) {
                    sb.append("Running processes (").append(list.size()).append(") with \"autoplug\" in name or start command: \n");
                    for (JProcess p : list) {
                        sb.append(p.name).append(" pid: ").append(p.pid).append(" command: ").append(p.command).append("\n");
                    }
                    sb.append("Make sure each of this processes has its own, unique server-key.");
                }
                if (ex != null) {
                    sb.append("There was an error retrieving running process details: " + ex.getMessage() + " " + new UtilsLists().toString(Arrays.asList(ex.getStackTrace())));
                }
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): Another client with this server key is already connected!" +
                        " Close that connection and restart AutoPlug. " + sb);
            case 3:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): Make sure that the primary connection is established before all the secondary connections!");
            case 4:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): Unknown connection type! Make sure that AutoPlug is up-to-date!");
            case 5:
                throw new Exception("[CON_TYPE: " + conType + "] Authentication failed (code:" + errorCode + "): No user account found for the provided server key!");
            case 6:
                String ip = in.readUTF();
                String hostname = in.readUTF();
                int port = in.readInt();
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
            throw new Exception("SSLSession is not valid! An SSLSession may not be valid if the SSL/TLS connection" +
                    " has been closed or if there has been an error during the SSL/TLS handshake process." +
                    " It could also be invalid if the SSLSession has timed out due to inactivity. ");

        AL.debug(DefaultConnection.class, "Valid SSL session created for con_type " + conType + ". Details: " + session);

        input = socket.getInputStream();
        output = socket.getOutputStream();
        in = new DataInputStream(input);
        out = new DataOutputStream(output);
    }

    public void createInsecureConnection(String host, int port) throws Exception {
        AL.warn("Creating unencrypted connection, transmitted data can be read by a third-party.");
        socket = new Socket(host, port);
        input = socket.getInputStream();
        output = socket.getOutputStream();
        in = new DataInputStream(input);
        out = new DataOutputStream(output);
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

    public DataInputStream getIn() {
        return in;
    }

    public DataOutputStream getOut() {
        return out;
    }

    @Override
    public synchronized void close() throws Exception {
        isClosing.set(true);
        _close(thread, in, out, socket);
    }

    private void _close(Thread thread, InputStream in, OutputStream out, Socket socket) throws Exception {
        AL.debug(this.getClass(), "_close()");
        if (in != null) in.close();
        if (out != null) out.close();
        if (socket != null) socket.close();
        if (thread != null) thread.interrupt(); // Close thread last, since it might be the current thread
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "ssl=" + (socket != null && socket instanceof SSLSocket ? "true" : "false") +
                ", isAlive=" + isAlive() +
                ", errorCode=" + errorCode +
                ", socket=" + socket +
                ", threadRunning=" + (thread != null && !thread.isInterrupted() && thread.isAlive()) +
                '}';
    }

    public boolean isInterrupted() {
        return thread != null && thread.isInterrupted();
    }
}
