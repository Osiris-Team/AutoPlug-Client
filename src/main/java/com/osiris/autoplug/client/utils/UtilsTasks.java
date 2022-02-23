/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.logger.LogFileWriter;
import com.osiris.autoplug.core.logger.Message;
import com.osiris.autoplug.core.logger.MessageFormatter;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;

import java.io.PrintWriter;

public class UtilsTasks {

    public void writeAndPrintFinalResultsWhenDone(BetterThreadManager manager, BetterThreadDisplayer displayer) {
        try {
            while (!manager.isFinished())
                Thread.sleep(500);
            writeAndPrintFinalResults(manager, displayer);
        } catch (Exception e) {
            AL.warn(e);
        }
    }

    public void writeAndPrintFinalResults(BetterThreadManager manager, BetterThreadDisplayer displayer) {
        for (BetterThread t :
                manager.getAll()) { // Do this bc file has no colors and thus cannot see the task result (bc it gets shown as red/yellow or red).
            StringBuilder builder = new StringBuilder();
            if (t.isSuccess())
                builder.append("[OK]");
            else if (t.isSkipped())
                builder.append("[SKIPPED]");
            else if (t.getWarnings().isEmpty())
                builder.append("[FAILED]");
            else
                builder.append("[" + t.getWarnings().size() + "x WARN]");

            builder.append("[" + t.getName() + "] ");
            builder.append(t.getStatus());

            LogFileWriter.writeToLog(MessageFormatter.formatForFile(
                    new Message(Message.Type.INFO, builder.toString())));
        }

        // Write results to log file
        // We want the log file to have all the information.
        PrintWriter printWriter = new PrintWriter(LogFileWriter.BUFFERED_WRITER);
        boolean showWarnings = displayer.isShowWarnings();
        boolean showDetailedWarnings = displayer.isShowDetailedWarnings();
        displayer.setShowWarnings(true);
        displayer.setShowDetailedWarnings(true);

        displayer.printAndWriteResults(null, printWriter);

        displayer.setShowWarnings(showWarnings);
        displayer.setShowDetailedWarnings(showDetailedWarnings);
    }

}
