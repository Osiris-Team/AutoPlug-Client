/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilsFileTest {

    @Test
    void getSafeFileName() {
        assertEquals("", new UtilsFile().getValidFileName("/\\\\<>:\"'|*?\u0000\u0001\u0002\u0010\n"));
    }
}