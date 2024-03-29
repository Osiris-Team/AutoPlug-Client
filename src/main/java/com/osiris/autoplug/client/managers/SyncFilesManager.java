/*
 * Copyright (c) 2022-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.managers;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.configs.SharedFilesConfig;
import com.osiris.dyml.watcher.DirWatcher;
import com.osiris.dyml.watcher.FileEvent;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.osiris.autoplug.client.utils.GD.WORKING_DIR;

public class SyncFilesManager {
    private static List<File> lastFoldersToWatch = new ArrayList<>();

    public SyncFilesManager(SharedFilesConfig sharedFilesConfig) throws Exception {
        clearPreviousWatchers();
        List<File> foldersToWatch = initializeFoldersToWatch(sharedFilesConfig);
        List<File> filesToSendTo = initializeFilesToSendTo(sharedFilesConfig);
        Consumer<FileEvent> onFileChangeEvent = defineFileChangeEventHandler(filesToSendTo);
        setUpDirectoryWatchers(foldersToWatch, onFileChangeEvent);
    }

    private void clearPreviousWatchers() throws Exception {
        for (File folder : lastFoldersToWatch) {
            DirWatcher dirWatcher = DirWatcher.get(folder, false);
            dirWatcher.removeAllListeners(true);
            dirWatcher.close();
        }
    }

    private List<File> initializeFoldersToWatch(SharedFilesConfig sharedFilesConfig) throws Exception {
        List<File> foldersToWatch = new ArrayList<>();
        for (String pathAsString : sharedFilesConfig.copy_from.asStringList()) {
            if (pathAsString.startsWith("./"))
                foldersToWatch.add(FileManager.convertRelativeToAbsolutePath(pathAsString));
            else
                throw new Exception("Wrongly formatted or absolute path: " + pathAsString);
        }
        return foldersToWatch;
    }

    private List<File> initializeFilesToSendTo(SharedFilesConfig sharedFilesConfig) throws Exception {
        List<File> filesToSendTo = new ArrayList<>();
        for (String value : sharedFilesConfig.send_to.asStringList()) {
            if (value.startsWith("./"))
                filesToSendTo.add(FileManager.convertRelativeToAbsolutePath(value));
            else if (value.contains("/") || value.contains("\\"))
                filesToSendTo.add(new File(value));
            else
                throw new Exception("Failed to determine if '" + value + "' is an absolute/relative path address.");
        }
        return filesToSendTo;
    }

    private Consumer<FileEvent> defineFileChangeEventHandler(List<File> filesToSendTo) {
        return event -> {
            String relPath = event.file.getAbsolutePath().replace(WORKING_DIR.getAbsolutePath(), "");
            handleFileChange(event, filesToSendTo, relPath);
        };
    }

    private void handleFileChange(FileEvent event, List<File> filesToSendTo, String relPath) {
        if (event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
            filesToSendTo.forEach(receivingServerRootDir -> new File(receivingServerRootDir + relPath).delete());
        } else if (event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_MODIFY)
                || event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
            filesToSendTo.forEach(receivingServerRootDir -> {
                try {
                    File f = new File(receivingServerRootDir + relPath);
                    if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
                    if (!f.exists()) f.createNewFile();
                    Files.copy(event.path, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    AL.warn(e);
                }
            });
        } else {
            AL.warn("Unhandled event type: " + event.getWatchEventKind().name() + " for file: " + event.file);
        }
    }

    private void setUpDirectoryWatchers(List<File> foldersToWatch, Consumer<FileEvent> onFileChangeEvent) {
        foldersToWatch.forEach(folder -> {
            try {
                DirWatcher.get(folder, true).addListeners(onFileChangeEvent);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            AL.debug(Main.class, "Watching 'copy-from' folder and sub-folders from: " + folder);
        });
        lastFoldersToWatch = foldersToWatch;
    }
}
