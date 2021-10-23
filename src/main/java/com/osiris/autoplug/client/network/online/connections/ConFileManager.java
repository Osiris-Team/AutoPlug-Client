/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.SecondaryConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.Nullable;

import java.io.*;


public class ConFileManager extends SecondaryConnection {

    @Nullable
    private DataOutputStream dos;
    private DataInputStream dis;
    private BufferedWriter writer;
    private Thread thread;

    public ConFileManager() {
        super((byte) 5);  // Each connection has its own auth_id.
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().file_manager.asBoolean()) {
            super.open();
            getSocket().setSoTimeout(0);
            dos = getDataOut();
            dis = getDataIn();
            writer = new BufferedWriter(new OutputStreamWriter(getOut()));

            thread = new Thread(() -> {
                try {
                    String filePath = null;
                    File requestedFile = null;
                    while (true) {
                        filePath = dis.readUTF(); // Wait until we receive the files path
                        if (filePath.isEmpty()) requestedFile = GD.WORKING_DIR;
                        else requestedFile = new File(filePath);
                        if (!requestedFile.exists()) {
                            dos.writeBoolean(false); // Check if the file actually exists
                        } else {
                            dos.writeBoolean(true);
                            sendFileDetails(requestedFile);
                            if (requestedFile.isDirectory()) {
                                File[] files = requestedFile.listFiles();
                                if (files == null) dos.writeInt(0);
                                else dos.writeInt(files.length);
                                for (File f :
                                        files) {
                                    sendFileDetails(f);
                                }
                            } else { // Is not a dir
                                if (dis.readBoolean()) // Web checks the files size and responds with true if it wants its content
                                    sendFileContent(requestedFile);
                            }
                        }
                    }
                } catch (Exception e) {
                    AL.warn(e);
                }
            });
            thread.start();
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' connected.");
            return true;
        } else {
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' not connected, because not enabled in the web-config.");
            return false;
        }
    }

    private void sendFileContent(File file) throws IOException {
        String exceptionMsg = null;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                writer.write(line + "\n");
            }
            writer.write("\\u001a"); // EOF
        } catch (IOException e) {
            AL.warn(e);
            exceptionMsg = e.getMessage();
        }
        if (exceptionMsg != null) {
            dos.writeBoolean(true);
            dos.writeUTF(exceptionMsg);
        } else {
            dos.writeBoolean(false);
        }
    }

    private void sendFileDetails(File file) throws IOException {
        dos.writeUTF(file.getAbsolutePath());
        dos.writeBoolean(file.isDirectory());
        long length = file.length(); // In bytes
        if (length != 0) length = length / 1048576; // /1mb to convert to mb
        dos.writeLong(length);
        dos.writeUTF(file.getName());
        dos.writeLong(file.lastModified());
        dos.writeBoolean(file.isHidden());
    }

    @Override
    public void close() throws IOException {

        try {
            super.close();
        } catch (Exception e) {
            AL.warn("Failed to close connection.", e);
        }

        try {
            if (thread != null && !thread.isInterrupted()) thread.interrupt();
        } catch (Exception e) {
            AL.warn("Failed to stop thread.", e);
        }
        thread = null;
        dos = null;
        dis = null;
        writer = null;
    }
}
