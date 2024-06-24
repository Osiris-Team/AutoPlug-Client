/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.io;

import com.osiris.jlib.logger.AL;

import javax.naming.LimitExceededException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * ULTRA FAST DATA INPUTSTREAM!
 */
public class UFDataIn {
    private final DataInputStream dis;

    public UFDataIn(InputStream inputStream) {
        this.dis = new DataInputStream(inputStream);
    }

    public String readLine() throws IOException {
        return dis.readUTF();
    }

    public boolean readBoolean() throws IOException {
        return dis.readBoolean();
    }

    public void readFile(File file, long maxBytes) throws IOException, LimitExceededException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            readStream(out, maxBytes);
        }
    }

    /**
     * Returns a list containing the lines of the file.
     */
    public void readStream(OutputStream out, long maxBytes) throws IOException, LimitExceededException {
        long countBytesRead = 0;
        int count;
        byte[] buffer = new byte[8192]; // or 4096, or more
        String buffer_s = "";
        while (!(buffer_s = dis.readUTF()).equals(UFDataOut.EOF)) {
            buffer = buffer_s.getBytes(StandardCharsets.UTF_8);
            count = buffer.length;

            countBytesRead += count;
            if (countBytesRead > maxBytes) {
                throw new LimitExceededException("Exceeded the maximum allowed bytes: " + maxBytes);
            }
            out.write(buffer, 0, count);
            out.flush();
        }
        //read("\u001a") // Not needed here since already read above by read()
    }

    public void readFile(File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            readStream(out);
        }
    }

    /**
     * Returns a list containing the lines of the file.
     */
    public void readStream(OutputStream out) throws IOException {
        Base64.Decoder decoder = Base64.getDecoder();
        long countBytesRead = 0;
        int count;
        byte[] buffer = new byte[8192]; // or 4096, or more
        String buffer_s = "";
        while (!(buffer_s = dis.readUTF()).equals(UFDataOut.EOF)) {
            buffer = decoder.decode(buffer_s);
            count = buffer.length;

            countBytesRead += count;
            out.write(buffer, 0, count);
            out.flush();
        }
        AL.debug(this.getClass(), "BYTES READ: " + countBytesRead);
        //read("\u001a") // Not needed here since already read above by read()
    }

    public byte readByte() throws IOException {
        return dis.readByte();
    }

    public short readShort() throws IOException {
        return dis.readShort();
    }

    public int readInt() throws IOException {
        return dis.readInt();
    }

    public long readLong() throws IOException {
        return dis.readLong();
    }

    public float readFloat() throws IOException {
        return dis.readFloat();
    }

}
