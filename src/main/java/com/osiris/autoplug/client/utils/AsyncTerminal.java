/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import org.jline.utils.OSUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class AsyncTerminal {
    public Process process;

    public AsyncTerminal(File workingDir, Consumer<String> onLineReceived,
                         Consumer<String> onErrorLineReceived, String... commands) throws IOException {
        Process p;
        if (OSUtils.IS_WINDOWS) {
            try {  // Try powershell first, use cmd as fallback
                p = new ProcessBuilder("powershell").directory(workingDir).start();
                if (!p.isAlive()) throw new Exception();
            } catch (Exception e) {
                p = new ProcessBuilder("cmd").directory(workingDir).start();
            }
        } else { // Unix based system, like Linux, Mac etc...
            try {  // Try bash first, use sh as fallback
                p = new ProcessBuilder("/bin/bash").directory(workingDir).start();
                if (!p.isAlive()) throw new Exception();
            } catch (Exception e) {
                p = new ProcessBuilder("/bin/sh").directory(workingDir).start();
            }
        }
        this.process = p;
        InputStream in = process.getInputStream();
        InputStream inErr = process.getErrorStream();
        OutputStream out = process.getOutputStream();
        new AsyncReader(in, onLineReceived);
        new AsyncReader(inErr, onErrorLineReceived);
        if (commands != null && commands.length != 0)
            for (String command :
                    commands) {
                out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
    }
}
