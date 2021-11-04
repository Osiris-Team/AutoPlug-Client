/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;

import java.io.File;
import java.io.FileWriter;

public class TaskGeneral extends BetterThread {
    private GeneralConfig generalConfig;
    private int countDeletedFiles = 0;

    public TaskGeneral(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        generalConfig = new GeneralConfig();
        if (generalConfig.server_auto_eula.asBoolean()) {
            setStatus("Searching for eula.txt file...");
            File eula = new File(GD.WORKING_DIR + "/eula.txt");
            if (!eula.exists()) eula.createNewFile();
            try (FileWriter fw = new FileWriter(eula)) {
                fw.write("eula=true\n");
                fw.flush();
            }
            setStatus("File 'eula.txt' created successfully.");
        }
        try {
            if (!generalConfig.directory_cleaner.asBoolean()) {
                setStatus("Skipped directory-cleaner, because disabled.");
            } else {
                setStatus("Cleaning selected directories...");
                for (String s :
                        generalConfig.directory_cleaner_files.asStringList()) {
                    boolean cleanSubDirs = s.startsWith("true ");
                    if (cleanSubDirs) {
                        s = s.replaceFirst("true ", "");
                    }
                    File dir = null;
                    if (s.startsWith("./"))
                        dir = FileManager.convertRelativeToAbsolutePath(s);
                    else
                        new File(s);
                    if (!dir.isDirectory()) {
                        addWarning("Provided path '" + s + "' in " + generalConfig.directory_cleaner_files.getKeys() + " is a file and not a directory!");
                        continue;
                    }
                    long minLastModifiedTime = System.currentTimeMillis() - (generalConfig.directory_cleaner_max_days.asInt() * 86400000L); // * 86400000 (1day in ms) to convert days to milliseconds
                    cleanDirectory(dir, cleanSubDirs, minLastModifiedTime);
                    if (countDeletedFiles > 0) {
                        addInfo("Directory cleaner removed " + countDeletedFiles + " files, from directory " + s);
                        countDeletedFiles = 0;
                    }
                }
            }
        } catch (Exception e) {
            addWarning(new BetterWarning(this, e));
        }
        finish("Finished.");
    }

    private void cleanDirectory(File dir, boolean cleanSubDirs, long minLastModifiedTime) {
        if (cleanSubDirs) {
            for (File f :
                    dir.listFiles()) {
                if (f.isDirectory()) {
                    cleanDirectory(f, cleanSubDirs, minLastModifiedTime);
                    if (f.listFiles() == null || f.listFiles().length == 0) {
                        f.delete();
                        countDeletedFiles++;
                    }
                } else {
                    if (f.lastModified() < minLastModifiedTime) {
                        f.delete();
                        countDeletedFiles++;
                    }
                }
            }
        } else {
            for (File f :
                    dir.listFiles()) {
                if (!f.isDirectory()) {
                    if (f.lastModified() < minLastModifiedTime) {
                        f.delete();
                        countDeletedFiles++;
                    }
                }
            }
        }
    }
}
