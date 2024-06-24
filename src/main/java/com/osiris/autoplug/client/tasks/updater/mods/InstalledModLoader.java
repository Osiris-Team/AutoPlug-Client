/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.jlib.logger.AL;

import java.io.File;

public class InstalledModLoader {
    public boolean isForge, isFabric, isQuilt;

    public InstalledModLoader() {
        try {
            for (File f :
                    new File(System.getProperty("user.dir")).listFiles()) {
                if (f.getName().equals(".fabric")) {
                    isFabric = true;
                    break;
                }
                if (f.getName().equals(".quilt")) {
                    isQuilt = true;
                    break;
                }
            }
        } catch (Exception e) {
            AL.warn("Failed to determine installed Minecraft mod loader, fallback to forge.", e);
            isForge = true;
        }
    }
}
