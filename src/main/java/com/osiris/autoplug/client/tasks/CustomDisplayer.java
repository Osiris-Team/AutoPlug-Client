/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.exceptions.JLineLinkException;
import org.jline.utils.AttributedString;

import java.util.ArrayList;
import java.util.List;

import static org.fusesource.jansi.Ansi.Color.*;
import static org.fusesource.jansi.Ansi.ansi;

public class CustomDisplayer extends Thread {
    private final BetterThreadManager manager;
    public int timeoutMs = 3000;
    private byte anim;

    public CustomDisplayer(BetterThreadManager manager) throws JLineLinkException {
        this.manager = manager;
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

        // Fill this list with threads details and update the console after this
        List<AttributedString> list = new ArrayList<>();
        manager.getAll().forEach(thread -> {
            // Display the information for each thread.
            // Each thread gets one line.
            StringBuilder builder = new StringBuilder();

            //Format the output for a single process

            //Add the loading animation

            if (thread.isFinished()) {
                if (thread.isSkipped()) {
                    builder.append(ansi()
                            .fg(WHITE).a(" [#] ")
                            .reset());
                } else if (!thread.getWarnList().isEmpty()) {
                    builder.append(ansi()
                            .fg(YELLOW).a(" [" + thread.getWarnList().size() + "] ")
                            .reset());
                } else if (thread.isSuccess()) {
                    builder.append(ansi()
                            .fg(GREEN).a(" [#] ")
                            .reset());
                } else {
                    builder.append(ansi()
                            .fg(RED).a(" [#] ")
                            .reset());
                }
            } else {
                switch (anim) {
                    case 1:
                        builder.append(ansi().a(" [\\] "));
                        break;
                    case 2:
                        builder.append(ansi().a(" [|] "));
                        break;
                    case 3:
                        builder.append(ansi().a(" [/] "));
                        break;
                    default:
                        anim = 0;
                        builder.append(ansi().a(" [-] "));
                }
            }


            // Add the actual process details and finish the line
            final String name = thread.getName();
            final long now = thread.getNow();
            final long max = thread.getMax();
            final byte percent = thread.getPercent();
            final String status = thread.getStatus();

            if (now > 0) {
                if (thread.isSkipped())
                    builder.append(ansi()
                            .a("> [" + name + "] " + status));
                else
                    builder.append(ansi()
                            .a("> [" + name + "][" + percent + "%] " + status));
            } else {
                builder.append(ansi()
                        .a("> [" + name + "] " + status));
            }
            builder.append(ansi().reset());

            // Add this message to the list
            list.add(AttributedString.fromAnsi(builder.toString()));
        });

        // This must be done outside the for loop otherwise the animation wont work
        anim++;

        if (manager.getAll().isEmpty()) {
            list.add(AttributedString.fromAnsi("No threads! Waiting..."));
        } else {
            // This means we finished and should stop looping
            // We print the last warnings message and stop.
            if (manager.isFinished()) {
                AL.info(" ");
                for (AttributedString s :
                        list) {
                    AL.info(s.toAnsi());
                } // Update one last time
                return false;
            }
        }

        AL.info(" ");
        for (AttributedString s :
                list) {
            AL.info(s.toAnsi());
        }
        return true;
    }


}
