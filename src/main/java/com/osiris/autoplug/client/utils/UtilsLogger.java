/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.core.logger.AL;

import java.util.Arrays;
import java.util.Scanner;


public class UtilsLogger {

    public void animatedPrintln(String s) throws InterruptedException {
        System.out.print(" > ");
        for (int i = 0; i < s.length(); i++) {
            System.out.print(s.charAt(i));
            System.out.flush();
            Thread.sleep(50);
        }
        System.out.println();
    }

    public String expectInput(Scanner scanner, String... expectedInput) {
        String line;
        while (true) {
            line = scanner.nextLine();
            boolean equals = false;
            for (String s :
                    expectedInput) {
                if (line.equals(s)) {
                    equals = true;
                    break;
                }
            }
            if (equals) return line;
            else AL.warn("Your input was wrong. Please try again. Expected: " + Arrays.toString(expectedInput));
        }
    }
}
