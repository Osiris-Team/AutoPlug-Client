/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.jlib.logger.AL;

import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;


public class UtilsLogger {

    public void animatedPrintln(String s) throws InterruptedException {
        System.out.print(" > ");
        AtomicBoolean skip = new AtomicBoolean(false);
        Scanner scanner = new Scanner(System.in);
        Thread t = new Thread(() -> {
            scanner.nextLine();
            skip.set(true);
        });
        t.start();

        for (int i = 0; i < s.length(); i++) {
            if (skip.get()) {
                for (int j = i; j < s.length(); j++) {
                    System.out.print(s.charAt(j));
                }
                System.out.flush();
                break;
            }
            System.out.print(s.charAt(i));
            System.out.flush();
            Thread.sleep(50);
        }
        System.out.println();
        t.interrupt();
    }

    public String expectInput(String... expectedInput) {
        String line;
        Scanner scanner = new Scanner(System.in);
        while (true) {
            line = scanner.nextLine();
            if (expectedInput == null || expectedInput.length == 0) {
                return line;
            }
            boolean equals = false;
            for (String s :
                    expectedInput) {
                if (line.equals(s)) {
                    equals = true;
                    break;
                }
            }
            if (equals) {
                return line;
            } else AL.warn("Your input was wrong. Please try again. Expected: " + Arrays.toString(expectedInput));
        }
    }
}
