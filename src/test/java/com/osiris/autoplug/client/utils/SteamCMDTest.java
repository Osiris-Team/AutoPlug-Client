/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.UtilsTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SteamCMDTest {

    @Test
    void buildsWorkshopItemCommand() {
        String command = SteamCMD.buildWorkshopItemCommand("anonymous", "221100", "1559212036");

        assertEquals("+login anonymous +workshop_download_item 221100 1559212036 validate +quit", command);
    }

    @Test
    void installSteamcmd() throws IOException {
        UtilsTest.init();
        new SteamCMD().installIfNeeded();
    }
}
