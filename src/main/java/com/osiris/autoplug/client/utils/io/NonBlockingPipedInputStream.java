/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.io;

import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NonBlockingPipedInputStream extends PipedInputStream { // PipedInputStream
    @NotNull
    private final Thread thread;
    /**
     * Add actions to this list, which get run after a line has been written.
     * Contains the line as parameter.
     */
    @NotNull
    public List<WriteLineEvent<String>> actionsOnWriteLineEvent = new CopyOnWriteArrayList<>();

    /**
     * Creates and starts a new {@link Thread}, that reads the {@link PipedInputStream}
     * and fires an event every time a full line was written to it.
     * To listen for those events, add the action that should be run to the {@link #actionsOnWriteLineEvent} list.
     */
    public NonBlockingPipedInputStream() {
        thread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(this));
                String line;
                while ((line = reader.readLine()) != null) {
                    String finalLine = line;
                    actionsOnWriteLineEvent.forEach(action -> action.executeOnEvent(finalLine));
                }
            } catch (Exception e) {
                AL.warn(e);
            }
        });
        thread.start();

    }

    @NotNull
    public Thread getThread() {
        return thread;
    }

    public interface WriteLineEvent<L> {
        void executeOnEvent(L l);
    }
}
