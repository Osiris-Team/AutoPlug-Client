/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import java.io.*;

/**
 * TODO
 * ULTRA FAST DATA OUTPUTSTREAM!
 */
public class UFDataOut {
    private final OutputStream outputStream;
    private final BufferedWriter writer;

    public UFDataOut(OutputStream outputStream) {
        this.outputStream = outputStream;
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream));
    }

    public void write(String val) throws IOException {
        writer.write(val);
        writer.flush();
    }

    public void writeLine(String val) throws IOException {
        write(val + "\n");
    }

    public void write(int val) throws IOException {
        writer.write(val);
        writer.flush();
    }

    public void writeBoolean(boolean val) throws IOException {
        write(val ? 1 : 0);
    }

    public void writeFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                writeLine(line);
            }
            write("\u001a\n"); // EOF
        }
    }

    public void writeByte(byte val) throws IOException {
        writeLine("" + val);
    }

    public void writeShort(short val) throws IOException {
        writeLine("" + val);
    }

    public void writeInt(int val) throws IOException {
        writeLine("" + val);
    }

    public void writeLong(long val) throws IOException {
        writeLine("" + val);
    }

    public void writeFloat(float val) throws IOException {
        writeLine("" + val);
    }
}
