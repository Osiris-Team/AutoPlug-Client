/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.backup;

import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.lang.time.DateUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class TaskWorldsBackup extends BetterThread {

    private final File autoplug_backups_server = new File(GD.WORKING_DIR+"/autoplug-backups/server");
    private final File autoplug_backups_plugins = new File(GD.WORKING_DIR+"/autoplug-backups/plugins");
    private final File autoplug_backups_worlds = new File(GD.WORKING_DIR+"/autoplug-backups/worlds");

    private final LocalDateTime date = LocalDateTime.now();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
    private final String formattedDate = date.format(formatter);

    public TaskWorldsBackup(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        createWorldFoldersBackup();
    }

    private void createWorldFoldersBackup() throws Exception{
        if (Server.isRunning()) throw new Exception("Cannot perform backup while server is running!");

        BackupConfig config = new BackupConfig();

        String worlds_backup_dest = autoplug_backups_worlds.getAbsolutePath()+"/worlds-backup-"+formattedDate+".zip";
        int max_days_worlds = config.backup_worlds_max_days.asInt();

        //Removes files older than user defined days
        if (max_days_worlds<=0) {
            setStatus("Skipping delete of older backups...");
            Thread.sleep(1000);
        }
        else{
            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_worlds); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_worlds, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
                setStatus("Deleting backups older than "+max_days_worlds+" days... Deleted: "+deleted_files+" zips");
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            setStatus("Deleting backups older than "+max_days_worlds+" days... Deleted: "+deleted_files+" zips");
        }


        if (config.backup_worlds.asBoolean()) {
            setStatus("Creating backup zip...");

            List<File> worldsFiles = new FileManager().serverWorldsFolders();
            ZipFile zip = new ZipFile(worlds_backup_dest);

            setMax(worldsFiles.size());

            //Add each file to the zip
            File file = null;
            try{
                for (int i = 0; i < worldsFiles.size(); i++) {
                    file = worldsFiles.get(i);
                    zip.addFile(file);
                    setStatus("Creating backup zip... "+file.getName());
                    step();
                }
            } catch (Exception e) {
                getWarnings().add(new BetterWarning(this, e, "Failed to add "+file.getName()+" to zip."));
            }

            AL.debug(this.getClass(), "Created backup to: "+worlds_backup_dest);
            setStatus("Created backup zip successfully with "+ getWarnings().size()+" warning(s)!");
        } else{
            setStatus("Skipped backup...");
            skip();
        }
        finish();
    }


}
