/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FileChecker {

    enum FileType {
        DIR, FILE
    }

    public final File working_dir = new File(System.getProperty("user.dir"));
    public final File plugins = new File(working_dir +"/plugins");
    public final File autoplug_system = new File(working_dir +"/autoplug-system");
    public final File autoplug_downloads = new File(working_dir +"/autoplug-downloads");
    public final File autoplug_backups = new File(working_dir +"/autoplug-backups");
    public final File autoplug_backups_server = new File(working_dir +"/autoplug-backups/server");
    public final File autoplug_backups_plugins = new File(working_dir +"/autoplug-backups/plugins");
    public final File autoplug_backups_worlds = new File(working_dir +"/autoplug-backups/worlds");
    public final File autoplug_logs = new File(working_dir +"/autoplug-logs");

    private List<File> directories;

    private static int missing;
    private static int total;

    public void check() throws Exception{
        missing=0;
        total=0;

        directories = Arrays.asList(
                plugins,
                autoplug_downloads, autoplug_backups, autoplug_backups_server,
                autoplug_backups_plugins, autoplug_backups_worlds, autoplug_logs,
                autoplug_system);
        checkFiles(FileType.DIR, directories);
    }

    public boolean isFirstRun(){
        if (missing==total){
            return true;
        }
        return false;
    }

    private void checkFiles(FileType file_type, List<File> files) throws IOException {

        total++;

        //Iterate through all directories and create missing ones
        System.out.println("Checking "+file_type+"s...");
        for (int i = 0; i < files.size(); i++) {

            if (!files.get(i).exists()) {

                if (file_type.equals(FileType.DIR))
                    files.get(i).mkdirs();
                else
                    files.get(i).createNewFile();

                missing++;
                System.out.println(" - Generated: " + files.get(i).getName());
            }

        }
        System.out.println("All "+file_type+"s ok!");

    }

}
