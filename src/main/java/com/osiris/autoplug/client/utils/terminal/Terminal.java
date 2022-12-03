/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.terminal;

import org.jline.utils.OSUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Terminal {
    public Process process;

    public Terminal(File workingDir, String... commands) throws IOException {
        if (workingDir == null) workingDir = new File(System.getProperty("user.dir"));
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
        OutputStream out = process.getOutputStream();
        if (commands != null && commands.length != 0)
            for (String command :
                    commands) {
                out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
    }
}
