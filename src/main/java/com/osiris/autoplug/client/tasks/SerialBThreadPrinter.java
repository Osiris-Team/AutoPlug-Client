/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;
import com.osiris.betterthread.exceptions.JLineLinkException;
import com.osiris.betterthread.modules.BThreadPrinterModule;
import com.osiris.jlib.logger.AL;
import org.jline.utils.AttributedString;

import java.util.ArrayList;
import java.util.List;

public class SerialBThreadPrinter extends BThreadPrinter {

    public SerialBThreadPrinter(BThreadManager manager) throws JLineLinkException {
        super(manager, null, 5000, false);
        hideRegularOutput = false;
        printMissedRegularOutput = false;
    }

    @Override
    public boolean printAll() {
        List<AttributedString> linesToPrint = new ArrayList<>(); // Fill this list with threads details and update the console after this
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
            if (manager.isFinished()) {
                // Print one last time
                for (AttributedString printString : linesToPrint) {
                    AL.info(printString.toString());
                }
                AL.info("");
                return false;
            }
        }

        for (AttributedString printString : linesToPrint) {
            AL.info(printString.toString());
        }
        AL.info("");
        return true;
    }


}
