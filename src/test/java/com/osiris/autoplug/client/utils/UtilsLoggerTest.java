/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class UtilsLoggerTest {

    @Test
    void animatedPrintln() throws InterruptedException, IOException {
        UtilsLogger uLog = new UtilsLogger();
        uLog.animatedPrintln("Thank you for installing AutoPlug!");
        // DOESNT WORK IN INTELLIJ CONSOLE
    }
}