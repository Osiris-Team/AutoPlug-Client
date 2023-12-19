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
        for (File folder : lastFoldersToWatch) {
            // TODO does this also clean sub-dir listeners?
            DirWatcher.get(folder, false).removeAllListeners(true);
        }

        List<File> foldersToWatch = new ArrayList<>();
        for (String pathAsString :
                sharedFilesConfig.copy_from.asStringList()) {
            if (pathAsString.startsWith("./"))
                foldersToWatch.add(FileManager.convertRelativeToAbsolutePath(pathAsString));
            else
                throw new Exception("Wrongly formatted or absolute path: " + pathAsString);
        }

        List<File> filesToSendTo = new ArrayList<>();
        //List<String> ipsToSendTo = new ArrayList<>();
        for (String value :
                sharedFilesConfig.send_to.asStringList()) {
            if (value.startsWith("./"))
                filesToSendTo.add(FileManager.convertRelativeToAbsolutePath(value));
            else if (value.contains("/") || value.contains("\\"))
                filesToSendTo.add(new File(value));
                // TODO else if (value.contains("."))
                //    ipsToSendTo.add(value);
            else
                throw new Exception("Failed to determine if '" + value + "' is absolute/relative path address."); //TODO or ipv4/ipv6
        }

        Consumer<FileEvent> onFileChangeEvent = event -> {
            // Determine relative path from file to server root
            // Example: C:/Users/Server/plugins/AutoPlug.jar -> /plugins/AutoPlug.jar
            String relPath = event.file.getAbsolutePath().replace(WORKING_DIR.getAbsolutePath(), "");
            if (event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                for (File receivingServerRootDir :
                        filesToSendTo) {
                    new File(receivingServerRootDir + relPath)
                            .delete();
                }
            } else if (event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_MODIFY)
                    || event.getWatchEventKind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                for (File receivingServerRootDir :
                        filesToSendTo) {
                    try {
                        File f = new File(receivingServerRootDir + relPath);
                        if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
                        if (!f.exists()) f.createNewFile();
                        Files.copy(event.path, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        AL.warn(e);
                    }
                }
            } else
                AL.warn("Failed to execute 'send-to' for event type '" + event.getWatchEventKind().name() + "' for file '" + event.file + "'!");
        };

        for (File folder :
                foldersToWatch) {
            DirWatcher.get(folder, true).addListeners(onFileChangeEvent);
            AL.debug(Main.class, "Watching 'copy-from' folder and sub-folders from: " + folder);
        }
        lastFoldersToWatch = foldersToWatch;
    }
}
