/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.tasks;

import com.osiris.autoplug.client.tasks.CustomBThreadPrinter;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;


public class MyBThreadManager {
    public BThreadManager manager;
    public BThreadPrinter printer;
    public CustomBThreadPrinter customBThreadPrinter;

    public MyBThreadManager(BThreadManager manager, BThreadPrinter printer) {
        this.manager = manager;
        this.printer = printer;
    }

    public MyBThreadManager(BThreadManager manager, BThreadPrinter printer, CustomBThreadPrinter customBThreadPrinter) {
        this.manager = manager;
        this.printer = printer;
        this.customBThreadPrinter = customBThreadPrinter;
    }

}
