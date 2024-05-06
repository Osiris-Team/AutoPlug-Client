/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.io;

import com.osiris.jlib.logger.AL;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * ULTRA FAST DATA OUTPUTSTREAM!
 */
public class UFDataOut extends DataOutputStream {

    public UFDataOut(OutputStream outputStream) {
        super(outputStream);
    }

    public void writeLine(String s) throws IOException {
        writeUTF(s);
    }

    public static final String EOF = new String("EOF_MARKER_1714941978".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    public void writeFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            writeStream(in);
        }
    }

    public void writeStream(InputStream in) throws IOException {
        Base64.Encoder encoder = Base64.getEncoder();
        long totalCount = 0;
        int count;
        byte[] buffer = new byte[8192]; // or 4096, or more
        while ((count = in.read(buffer)) > 0) {
            writeUTF(encoder.encodeToString(buffer));
            flush();
            totalCount += count;
        }
        writeUTF(EOF); // Write since not included above
        AL.debug(this.getClass(), "BYTES SENT: " + totalCount);
        flush();
    }
}
