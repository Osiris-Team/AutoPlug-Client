/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.tasks;

import com.osiris.autoplug.client.tasks.MinimalBThreadPrinter;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;


public class MyBThreadManager {
    public BThreadManager manager;
    public BThreadPrinter printer;
    public MinimalBThreadPrinter minimalBThreadPrinter;

    public MyBThreadManager(BThreadManager manager, BThreadPrinter printer) {
        this.manager = manager;
        this.printer = printer;
    }

    public MyBThreadManager(BThreadManager manager, BThreadPrinter printer, MinimalBThreadPrinter minimalBThreadPrinter) {
        this.manager = manager;
        this.printer = printer;
        this.minimalBThreadPrinter = minimalBThreadPrinter;
    }

}
