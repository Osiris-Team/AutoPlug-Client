/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.core.logger.AL;

public class UtilsLogger {

    public void animatedPrintln(String s) throws InterruptedException {
        AL.info("");
        for (int i = 0; i < s.length(); i++) {
            Thread.sleep(100);
            System.out.print(s.charAt(i));
            System.out.flush();
        }
    }
}
