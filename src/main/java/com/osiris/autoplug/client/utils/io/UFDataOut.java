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
import java.util.Arrays;
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

    /**
     * @param file read data from this file and send it.
     */
    public void writeFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            writeStream(in);
        }
    }

    /**
     * @param in read data from this stream and send it.
     */
    public void writeStream(InputStream in) throws IOException {
        /*
         * Handling Non-Text Data: If the input stream
         *  contains binary data (like images or other non-text files),
         *  Base64 encoding allows this data to be represented as text,
         *  which can then be safely written using writeUTF.
         * It converts binary data into a set of 64 characters that are safe for text-based transmission.
         * By using Base64 encoding, we ensure that the binary data is first converted
         *  to a string representation that can be safely written using writeUTF.
         */
        Base64.Encoder encoder = Base64.getEncoder();
        long totalCount = 0;
        int count;
        byte[] buffer = new byte[8192]; // or 4096, or more
        while ((count = in.read(buffer)) > 0) {
            if (count == buffer.length) writeUTF(new String(encoder.encode(buffer), StandardCharsets.UTF_8));
            else writeUTF(new String(encoder.encode(Arrays.copyOf(buffer, count)), StandardCharsets.UTF_8));
            flush();
            totalCount += count;
        }
        writeUTF(EOF); // Write since not included above
        AL.debug(this.getClass(), "Bytes sent: " + totalCount);
        flush();
    }
}
