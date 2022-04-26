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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AutoStart {
    /**
     * Registers platform specific stuff
     * to launch the provided jar at system boot,
     * once the user is logged in.
     * Windows and UNIX like platforms supported.
     */
    void add(File jar) throws IOException, InterruptedException {
        if (OSUtils.IS_WINDOWS) {
            // TODO more research and testing needed
            File startScript = new File(GD.WORKING_DIR + "/autoplug/AutoPlug.bat");
            if (!startScript.exists()) {
                startScript.getParentFile().mkdirs();
                startScript.createNewFile();
            }
            Files.write(startScript.toPath(), ("" +
                    "javaw -jar \"" + jar.getAbsolutePath() + "\"\n" + // javaw to start without terminal
                    "").getBytes(StandardCharsets.UTF_8));
            Process p = new ProcessBuilder().command("REG",
                    "ADD", "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/V", "AutoPlug", "/t", "REG_SZ", "/F", "/D", startScript.getAbsolutePath()).start();  // The name AutoPlug doesnt get set on Win10,
            // but the file name is used, thus we create the AutoPlug.bat
            while (p.isAlive()) {
                Thread.sleep(100);
                if (p.exitValue() != 0) {
                    throw new IOException("Failed to register AutoPlug start on boot in Windows registry (error code: " + p.exitValue() + ")." +
                            " Error stream: \n" + new Streams().read(p.getErrorStream()) + " Regular stream: \n" + new Streams().read(p.getInputStream()));
                }
            }
        } else {
            // TODO
            throw new IOException("Your platform is currently not support for auto-starting AutoPlug on system boot.");
        }
    }

    /**
     * Removes the provided jar from starting at system boot.
     */
    void remove(File jar) {

    }
}
