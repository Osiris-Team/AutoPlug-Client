package com.osiris.autoplug.client.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NonBlockingPipedInputStream extends PipedInputStream {
    private final Thread thread;
    /**
     * Add actions to this list, which get run after a line has been written.
     * Contains the line as parameter.
     */
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
                e.printStackTrace();
            }
        });
        thread.start();

    }

    public Thread getThread() {
        return thread;
    }

    public interface WriteLineEvent<L> {
        void executeOnEvent(L l);
    }
}
