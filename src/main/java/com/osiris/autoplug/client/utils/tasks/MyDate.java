/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.tasks;

import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BThreadPrinter;
import com.osiris.betterthread.modules.Date;
import org.fusesource.jansi.Ansi;

import java.time.LocalDateTime;

import static org.fusesource.jansi.Ansi.ansi;

public class MyDate extends Date {
    @Override
    public void append(BThreadManager manager, BThreadPrinter printer, BThread thread, StringBuilder line) {
        line.append(ansi().bg(Ansi.Color.WHITE).fgBlack().a("[" + dateFormatter.format(LocalDateTime.now()) + "]")
                .reset());
    }
}
