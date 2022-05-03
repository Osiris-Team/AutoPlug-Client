/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;
import com.osiris.betterthread.exceptions.JLineLinkException;
import com.osiris.betterthread.modules.BThreadPrinterModule;
import org.jline.utils.AttributedString;

import java.util.ArrayList;
import java.util.List;

public class CustomBThreadPrinter extends Thread {
    private final BThreadManager manager;
    private final List<BThreadPrinterModule> defaultPrinterModules;
    public int timeoutMs = 3000;
    private byte anim;

    public CustomBThreadPrinter(BThreadManager manager) throws JLineLinkException {
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
        List<AttributedString> linesToPrint = new ArrayList<>();
        manager.getAll().forEach(thread -> { // Build a line for each thread.
            StringBuilder builder = new StringBuilder();
            if (thread.printerModules == null || thread.printerModules.isEmpty())
                thread.printerModules = defaultPrinterModules;
            for (BThreadPrinterModule m : thread.printerModules) {
                m.append(manager, null, thread, builder);
            }
            linesToPrint.add(AttributedString.fromAnsi(builder.toString()));
        });

        if (manager.getAll().isEmpty()) {
            linesToPrint.add(AttributedString.fromAnsi("No threads! Waiting..."));
        } else {
            // This means we finished and should stop looping
            // We print the last warnings message and stop.
            if (manager.isFinished()) {
                AL.info(" ");
                for (AttributedString s :
                        linesToPrint) {
                    AL.info(s.toAnsi());
                } // Update one last time
                return false;
            }
        }

        AL.info(" ");
        for (AttributedString s :
                linesToPrint) {
            AL.info(s.toAnsi());
        }
        return true;
    }


}
