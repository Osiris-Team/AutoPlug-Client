/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */
package com.osiris.autoplug.client.utils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class ConsoleOutputCapturer {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final PrintStream ps = new PrintStream(baos);
    private final PrintStream oldOut = System.out;
    private final PrintStream oldErr = System.err;

    public void start() {
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                ps.write(b);
                oldOut.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                ps.write(b, off, len);
                oldOut.write(b, off, len);
            }
        }));

        System.setErr(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                ps.write(b);
                oldErr.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                ps.write(b, off, len);
                oldErr.write(b, off, len);
            }
        }));
    }

    public void stop() {
        System.setOut(oldOut);
        System.setErr(oldErr);
    }

    public String getNewOutput() {
        String newOutput = baos.toString(StandardCharsets.UTF_8);
        baos.reset();
        return newOutput;
    }
}
