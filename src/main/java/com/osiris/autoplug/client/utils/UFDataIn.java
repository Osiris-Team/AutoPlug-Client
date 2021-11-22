/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import javax.naming.LimitExceededException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO
 * ULTRA FAST DATA INPUTSTREAM!
 */
public class UFDataIn {
    private final InputStream inputStream;
    private final BufferedReader reader;

    public UFDataIn(InputStream inputStream) {
        this.inputStream = inputStream;
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    public String readLine() throws IOException {
        return reader.readLine();
    }

    public boolean readBoolean() throws IOException {
        return reader.read() != 0;
    }

    /**
     * Returns a list containing the lines of the file.
     */
    public List<String> readFile() throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null && !line.equals("\u001a")) { // Stop at 10 mb
            lines.add(line + "\n");
        }
        return lines;
    }

    /**
     * Returns a list containing the lines of the file. <br>
     *
     * @param maxBytes the maximum amount of bytes to read.
     * @throws LimitExceededException when the provided maximum bytes amount was read.
     */
    public List<String> readFile(long maxBytes) throws IOException, LimitExceededException {
        List<String> lines = new ArrayList<>();
        long bytesRead = 0;
        String line;
        while ((line = reader.readLine()) != null && !line.equals("\u001a")) { // Stop at 10 mb
            lines.add(line + "\n");
            bytesRead = bytesRead + line.getBytes().length;
            if (bytesRead > maxBytes) {
                throw new LimitExceededException("Exceeded the maximum allowed bytes: " + maxBytes);
            }
        }
        return lines;
    }

    /**
     * Returns a list containing the lines of the file.
     */
    public List<String> readStream() throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null && !line.equals("\u001a")) {
            lines.add(line + "\n");
        }
        return lines;
    }

    /**
     * Returns a list containing the lines of the file. <br>
     *
     * @param maxBytes the maximum amount of bytes to read.
     * @throws LimitExceededException when the provided maximum bytes amount was read.
     */
    public List<String> readStream(long maxBytes) throws IOException, LimitExceededException {
        List<String> lines = new ArrayList<>();
        long bytesRead = 0;
        String line;
        while ((line = reader.readLine()) != null && !line.equals("\u001a")) { // Stop at 10 mb
            lines.add(line + "\n");
            bytesRead = bytesRead + line.getBytes().length;
            if (bytesRead > maxBytes) {
                throw new LimitExceededException("Exceeded the maximum allowed bytes: " + maxBytes);
            }
        }
        return lines;
    }

    public byte readByte() throws IOException {
        return Byte.parseByte(readLine());
    }

    public short readShort() throws IOException {
        return Short.parseShort(readLine());
    }

    public int readInt() throws IOException {
        return Integer.parseInt(readLine());
    }

    public long readLong() throws IOException {
        return Long.parseLong(readLine());
    }

    public float readFloat() throws IOException {
        return Float.parseFloat(readLine());
    }

}
