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
    private final OutputStream out;

    public UFDataIn(OutputStream out, InputStream inputStream) {
        this.dis = new DataInputStream(inputStream);
        this.out = out;
    }

    public String readLine() throws IOException {
        out.flush(); // Ensure pending messages are sent before reading
        return dis.readUTF();
    }

    public boolean readBoolean() throws IOException {
        out.flush(); // Ensure pending messages are sent before reading
        return dis.readBoolean();
    }

    /**
     * @param file write receiving data to this file.
     */
    public void readFile(File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            readStream(out);
        }
    }

    /**
     * @param file     write receiving data to this file.
     * @param maxBytes set to -1 if no limit wanted.
     */
    public void readFile(File file, long maxBytes) throws IOException, LimitExceededException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            readStream(out, maxBytes);
        }
    }

    /**
     * @param out write receiving data to this stream.
     */
    public void readStream(OutputStream out) throws IOException {
        try {
            readStream(out, -1);
        } catch (LimitExceededException e) { // Not excepted to happen since no limit
            throw new RuntimeException(e);
        }
    }

    /**
     * @param out write receiving data to this stream.
     * @param maxBytes set to -1 if no limit wanted.
     */
    public void readStream(OutputStream out, long maxBytes) throws IOException, LimitExceededException {
        this.out.flush(); // Ensure pending messages are sent before reading
        /*
         * Handling Non-Text Data: If the input stream
         *  contains binary data (like images or other non-text files),
         *  Base64 encoding allows this data to be represented as text,
         *  which can then be safely written using writeUTF.
         * By using Base64 encoding, we ensure that the binary data is first converted
         *  to a string representation that can be safely written using writeUTF.
         */
        Base64.Decoder decoder = Base64.getDecoder();
        long countBytesRead = 0;
        int count;
        byte[] buffer = new byte[8192]; // or 4096, or more
        String buffer_s = "";
        while (!(buffer_s = dis.readUTF()).equals(UFDataOut.EOF)) {
            buffer = decoder.decode(buffer_s.getBytes(StandardCharsets.UTF_8));
            count = buffer.length;

            countBytesRead += count;
            if (maxBytes >= 0 && countBytesRead > maxBytes) {
                throw new LimitExceededException("Exceeded the maximum allowed bytes: " + maxBytes);
            }
            out.write(buffer, 0, count);
            out.flush();
        }
        AL.debug(this.getClass(), "Bytes read: " + countBytesRead);
        //read("\u001a") // Not needed here since already read above by read()
    }

    public byte readByte() throws IOException {
        out.flush(); // Ensure pending messages are sent before reading
        return dis.readByte();
    }

    public short readShort() throws IOException {
        out.flush(); // Ensure pending messages are sent before reading
        return dis.readShort();
    }

    public int readInt() throws IOException {
        out.flush(); // Ensure pending messages are sent before reading
        return dis.readInt();
    }

    public long readLong() throws IOException {
        out.flush(); // Ensure pending messages are sent before reading
        return dis.readLong();
    }

    public float readFloat() throws IOException {
        out.flush(); // Ensure pending messages are sent before reading
        return dis.readFloat();
    }

}
