/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BWarning;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TaskGeneral extends BThread {
    private GeneralConfig generalConfig;
    private int countDeletedFiles = 0;

    private static final long MILLISECONDS_PER_DAY = 86400000L;

    public TaskGeneral(String name, BThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        generalConfig = new GeneralConfig();

        //Created a separate method for handling eula.txt file creation
        createEualaFile();
        try {

            //Created a different method for handling Directory Cleaner
            handleDirectoryCleaner();
        } catch (Exception e) {
            addWarning(new BWarning(this, e));
        }
        finish("Finished.");
    }

    private void createEualaFile() throws IOException {
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
    }

    private void handleDirectoryCleaner() {
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

                //Introduced Explaining Variable
                long minLastModifiedTime = System.currentTimeMillis() - (generalConfig.directory_cleaner_max_days.asInt() * MILLISECONDS_PER_DAY);
                cleanDirectory(dir, cleanSubDirs, minLastModifiedTime);
                if (countDeletedFiles > 0) {
                    addInfo("Directory cleaner removed " + countDeletedFiles + " files, from directory " + s);
                    countDeletedFiles = 0;
                }
            }
        }
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
