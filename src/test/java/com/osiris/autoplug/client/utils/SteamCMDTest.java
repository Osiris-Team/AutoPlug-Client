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

class SteamCMDTest {

    @Test
    void installSteamcmd() throws IOException {
        UtilsTest.init();
        new SteamCMD().installIfNeeded();
    }
}