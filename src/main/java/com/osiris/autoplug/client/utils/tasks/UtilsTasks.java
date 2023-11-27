/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.tasks;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.tasks.SerialBThreadPrinter;
import com.osiris.autoplug.client.utils.DefaultBThreadPrinter;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;
import com.osiris.betterthread.BWarning;
import com.osiris.betterthread.exceptions.JLineLinkException;
import com.osiris.betterthread.modules.BThreadModulesBuilder;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.LogFileWriter;
import com.osiris.jlib.logger.Message;
import com.osiris.jlib.logger.MessageFormatter;
import org.fusesource.jansi.Ansi;

import java.io.IOException;

public class UtilsTasks {

    public MyBThreadManager createManagerAndPrinter() throws YamlWriterException, NotLoadedException, IOException, IllegalKeyException, DuplicateKeyException, YamlReaderException, IllegalListException, JLineLinkException {
        LoggerConfig loggerConfig = new LoggerConfig();
        BThreadManager manager = new BThreadManager();
        BThreadPrinter printer = null;
        if (loggerConfig.live_tasks.asBoolean()) {
            manager = new BThreadManager();
            printer.defaultPrinterModules = new BThreadModulesBuilder()
                    .custom(new MyDate())
                    .text(Ansi.ansi().bg(Ansi.Color.WHITE).fgCyan().a("[" + loggerConfig.autoplug_label.asString() + "]").reset())
                    .text(Ansi.ansi().bg(Ansi.Color.WHITE).fgBlack().a("[TASK]").reset())
                    .space()
                    .spinner()
                    .text(Ansi.ansi().a(" > "))
                    .status().build();
        } else {
            printer = new SerialBThreadPrinter(manager);
            printer.defaultPrinterModules = new BThreadModulesBuilder()
                    .text(Ansi.ansi().bg(Ansi.Color.WHITE).fgBlack().a("[TASK]").reset())
                    .space()
                    .spinner()
                    .text(Ansi.ansi().a(" > "))
                    .status().build();
        }

        //printer.setupPrinterModules(loggerConfig);
        printer.start();

        return new MyBThreadManager(manager, printer);
    }

    public void printResultsWhenDone(BThreadManager manager) {
        try {
            while (!manager.isFinished()) Thread.sleep(500);
            LoggerConfig loggerConfig = new LoggerConfig();
            if (loggerConfig.live_tasks.asBoolean())
                printResultsLiveTask(manager);
            else
                printResults(manager);
        } catch (Exception e) {
            AL.warn(e);
        }
    }

    public void printResultsLiveTask(BThreadManager manager) {
        for (BThread t :
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

        for (BThread t : manager.getAll()) {
            if (!t.getInfoList().isEmpty() || !t.getWarnings().isEmpty()) {
                AL.info(t.getName() + ":");
                for (String s :
                        t.getInfoList()) {
                    AL.info(s);
                }
                for (BWarning w : t.getWarnings()) {
                    AL.warn(w.getExtraInfo(), w.getException());
                }
            }
        }
    }

    public void printResults(BThreadManager manager) {
        if (!manager.isFinished()) return;
        for (BThread t :
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

            AL.info(builder.toString());
        }

        for (BThread t : manager.getAll()) {
            if (!t.getInfoList().isEmpty() || !t.getWarnings().isEmpty()) {
                AL.info(t.getName() + " details:");
                for (String s :
                        t.getInfoList()) {
                    AL.info(s);
                }
                for (BWarning w : t.getWarnings()) {
                    AL.warn(w.getExtraInfo(), w.getException());
                }
            }
        }
    }

}
