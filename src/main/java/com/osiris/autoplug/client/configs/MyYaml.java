/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.Yaml;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import com.osiris.dyml.exceptions.YamlReaderException;
import com.osiris.dyml.exceptions.YamlWriterException;
import com.osiris.dyml.watcher.FileEvent;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class MyYaml extends Yaml {
    public MyYaml(InputStream inputStream, OutputStream outputStream) {
        super(inputStream, outputStream);
    }

    public MyYaml(InputStream inputStream, OutputStream outputStream, boolean isDebugEnabled) {
        super(inputStream, outputStream, isDebugEnabled);
    }

    public MyYaml(InputStream inputStream, OutputStream outputStream, boolean isPostProcessingEnabled, boolean isDebugEnabled) {
        super(inputStream, outputStream, isPostProcessingEnabled, isDebugEnabled);
    }

    public MyYaml(String inString, String outString) {
        super(inString, outString);
    }

    public MyYaml(String inString, String outString, boolean isDebugEnabled) {
        super(inString, outString, isDebugEnabled);
    }

    public MyYaml(String inString, String outString, boolean isPostProcessingEnabled, boolean isDebugEnabled) {
        super(inString, outString, isPostProcessingEnabled, isDebugEnabled);
    }

    public MyYaml(File file) {
        super(file);
    }

    public MyYaml(File file, boolean isDebugEnabled) {
        super(file, isDebugEnabled);
    }

    public MyYaml(File file, boolean isPostProcessingEnabled, boolean isDebugEnabled) {
        super(file, isPostProcessingEnabled, isDebugEnabled);
    }

    public MyYaml(String filePath) {
        super(filePath);
    }

    public MyYaml(String filePath, boolean isDebugEnabled) {
        super(filePath, isDebugEnabled);
    }

    public MyYaml(String filePath, boolean isPostProcessingEnabled, boolean isDebugEnabled) {
        super(filePath, isPostProcessingEnabled, isDebugEnabled);
    }

    /**
     * Pairs of absolute file path and count of pending programmatic save events for that file.
     */
    private static final Map<String, PSave> filesAndPEvents = new HashMap<>();

    private final long msLastEvent = 0;

    /**
     * We can't count correctly bc the received events count is not exactly the same?!
     * Instead of keeping track of the save() and modify event counts we simply check
     * when the last save() was executed and if it was within the last 2 seconds
     * we do NOT execute the listeners for that file.
     *
     * Does nothing if a listener for this config/file was already registered. <br>
     * {@link #load()} is performed before executing the listener to ensure this config has the latest values
     * and {@link #validateValues()} to ensure the input is correct. <br>
     * <p>
     * Also has anti-spam meaning it waits 10 seconds for a newer event and executes the listener then. <br>
     * UPDATE: Not anymore.
     * <p>
     * To be able to call {@link #save(boolean)} in here and prevent a stackoverflow error, events
     * caused by a programmatic save will not execute the listener, thus only user modify events will.
     */
    public Yaml addSingletonConfigFileEventListener(Consumer<FileEvent> listener) throws IOException {
        if (file == null) throw new RuntimeException("file cannot be null.");
        String path = file.getAbsolutePath();

        synchronized (filesAndPEvents) {
            if (filesAndPEvents.containsKey(path)) return this; // Already exists
            PSave pSave = new PSave();
            pSave.listener = listener;
            filesAndPEvents.put(path, pSave);
        }

        AL.debug(this.getClass(), "Listening for changes for " + path);
        super.addFileEventListener(e -> {
            String preInfo = this.file.getName() + " (" + e.getWatchEventKind() + "): ";
            //AL.debug(this.getClass(), preInfo);
            if (e.isDeleteEvent())
                AL.info(preInfo + "Deleted. Thus clean config with defaults will be created once its needed.");
            if (e.isModifyEvent()) {
                synchronized (filesAndPEvents) {
                    PSave p = filesAndPEvents.get(path);
                    //AL.info(preInfo+" pending: "+p.pendingSaveCount);
                    if ((System.currentTimeMillis() - p.msLastSave) < 2000) {
                        //AL.info(preInfo+" IGNORED!");
                        return;
                    }
                }
                try {
                    try {
                        lockFile();
                        load();
                    } catch (Exception ex) {
                        AL.warn(preInfo + "Failed to update internal values for config. Check for syntax errors.", ex);
                        return;
                    } finally {
                        unlockFile();
                    }
                    try {
                        validateValues();
                    } catch (Exception ex) {
                        AL.warn(preInfo + "Failed to update internal values for config. One or multiple values are not valid.", ex);
                        return;
                    }
                    listener.accept(e);
                    AL.info(preInfo + "Internal values updated.");
                } catch (Exception ex) {
                    AL.warn(ex);
                }
            }
        });

        return this;
    }

    public abstract Yaml validateValues();

    @Override
    public Yaml save(boolean overwrite) throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException {
        validateValues();
        synchronized (filesAndPEvents) {
            String path = file.getAbsolutePath();
            if (filesAndPEvents.containsKey(path)) {
                PSave p = filesAndPEvents.get(path);
                p.msLastSave = System.currentTimeMillis();
            }
        }
        return super.save(overwrite);
    }

    /**
     * Programmatic save of a yaml file.
     */
    public static class PSave {
        public long msLastSave = System.currentTimeMillis();
        public Consumer<FileEvent> listener;
    }
}
