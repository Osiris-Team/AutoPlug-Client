/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class AsyncInputStream {
    private final InputStream inputStream;
    private final Thread thread;
    public List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public AsyncInputStream(InputStream inputStream) {
        this.inputStream = inputStream;

        Object o = this;
        thread = new Thread(() -> {
            String line = "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                while ((line = br.readLine()) != null) {
                    for (Consumer<String> listener :
                            listeners) {
                        listener.accept(line);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error in thread for object '" + o + "' Details:");
                e.printStackTrace();
            }
        });
        thread.start();
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public Thread getThread() {
        return thread;
    }

    /**
     * Returns the list of listeners. <br>
     * Each listener listens for write line events. <br>
     */
    public List<Consumer<String>> getListeners() {
        return listeners;
    }
}
