/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

public class InstalledModLoader {
    public boolean isForge, isFabric, isQuilt;

    public InstalledModLoader(boolean isForge, boolean isFabric, boolean isQuilt) {
        this.isForge = isForge;
        this.isFabric = isFabric;
        this.isQuilt = isQuilt;
    }
}
