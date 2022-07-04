/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;
import com.osiris.betterthread.exceptions.JLineLinkException;
import com.osiris.betterthread.modules.BThreadPrinterModule;

import java.util.List;

public class MinimalBThreadPrinter extends Thread {
    private final BThreadManager manager;
    private final List<BThreadPrinterModule> defaultPrinterModules;
    public int timeoutMs = 3000;
    private int i = 0;
    private byte anim;

    public MinimalBThreadPrinter(BThreadManager manager) throws JLineLinkException {
        this.manager = manager;
        this.defaultPrinterModules = new BThreadPrinter(manager).defaultPrinterModules;
    }

    @Override
    public void run() {
        try {
            while (printAll()) {
                sleep(timeoutMs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean printAll() {

        if (manager.getAll().isEmpty()) {
            System.out.print("\r");
            System.out.flush();
            System.out.print("No threads! Waiting...");
            System.out.flush();
            return true;
        } else {
            // This means we finished and should stop looping
            // We print the last warnings message and stop.
            if (manager.isFinished()) {
                updateLine();
                return false;
            }
        }

        updateLine();
        return true;
    }

    private void updateLine() {
        System.out.print("\r");
        System.out.flush();
        if (i >= manager.getActive().size()) i = 0;
        if (manager.getActive().isEmpty()) {
            System.out.print("Finished " + manager.getAll().size() + " tasks, with " + manager.getAllWarnings().size() + " warnings.\n");
        } else {
            BThread t = manager.getActive().get(i);
            System.out.print(manager.getActive().size() + " running tasks... (" + t.getName() + ") " + t.getStatus());
        }
        System.out.flush();
        i++;
    }


}
