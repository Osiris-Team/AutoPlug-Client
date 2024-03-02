/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilsLoggerTest {

    @BeforeEach
    void setUp() {
        utilsLogger = new UtilsLogger();
    }

    @Test
    void animatedPrintln() throws InterruptedException, IOException {
        UtilsLogger uLog = new UtilsLogger();
        uLog.animatedPrintln("Thank you for installing AutoPlug!");
        // DOESNT WORK IN INTELLIJ CONSOLE
    }


    private UtilsLogger utilsLogger;
    @Test
    void testExpectInput() throws Exception{
        String expectedInput = "test";
        InputStream sysInBackup = System.in;
        try {
            String input = expectedInput + "\n";
            InputStream in = new ByteArrayInputStream(input.getBytes());
            System.setIn(in);

            String userInput = utilsLogger.expectInput(expectedInput);

            assertEquals(expectedInput, userInput);
        } finally {
            System.setIn(sysInBackup);
        }
    }

}