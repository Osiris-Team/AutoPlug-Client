/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import org.fusesource.jansi.AnsiConsole;
import org.junit.jupiter.api.Test;

public class AnsiConsoleTests {
    @Test
    void test() {
        AnsiConsole.systemInstall();
    }
}
