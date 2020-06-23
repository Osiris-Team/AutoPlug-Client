/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.managers;

import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.lang.time.DateUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class BackupManager {

    private AutoPlugLogger logger = new AutoPlugLogger();
    private File autoplug_backups_server = new File(GD.WORKING_DIR+"/autoplug-backups/server");
    private File autoplug_backups_plugins = new File(GD.WORKING_DIR+"/autoplug-backups/plugins");
    private File autoplug_backups_worlds = new File(GD.WORKING_DIR+"/autoplug-backups/worlds");

    public BackupManager(){
        logger.global_info("|----------------------------------------|");
        logger.global_info("|                [BACKUP]                |");
        createBackupServerFiles();
        createBackupPlugins();
        createBackupWorldFolders();
        logger.global_info(" ");
        logger.global_info("|----------------------------------------|");
    }

    public void createBackupServerFiles(){

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
        String formattedDate = date.format(formatter);

        String server_backup_dest = autoplug_backups_server.getAbsolutePath()+"/server-files-backup-"+formattedDate+".zip";
        int max_days_server = BackupConfig.backup_server_max_days;

        //Removes files older than user defined days
        logger.global_info(" ");
        if (max_days_server<=0) {logger.global_info(" - Skipping delete of older server backups...");}
        else{
            logger.global_info(" - Scanning for server-files backups older than "+max_days_server+" days...");

            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_server); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_server, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            logger.global_info(" - Removed " +deleted_files+ " zips.");
        }


        if (BackupConfig.backup_server) {
            logger.global_info(" - Creating server-files backup...");

            List<File> serverFiles = new FileManager().serverFiles();
            ZipFile zip = new ZipFile(server_backup_dest);


            try (ProgressBar pb = new ProgressBarBuilder()
                    .setInitialMax(serverFiles.size())
                    .setTaskName("Server-files backup: ")
                    .build()) {

                //Add each file to the zip
                for (int i = 0; i < serverFiles.size(); i++) {
                    zip.addFile(serverFiles.get(i));
                    pb.step();
                }
            } catch (ZipException e) {
                e.printStackTrace();
            }


            logger.global_info(" - Created server-files backup at:" + server_backup_dest);
        } else{
            logger.global_info(" - Skipping server-files backup...");
        }

    }

    public void createBackupPlugins(){

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
        String formattedDate = date.format(formatter);

        String plugins_backup_dest = autoplug_backups_plugins.getAbsolutePath()+"/plugins-backup-"+formattedDate+".zip";
        int max_days_plugins = BackupConfig.backup_plugins_max_days;

        //Removes files older than user defined days
        logger.global_info(" ");
        if (max_days_plugins<=0) {logger.global_info(" - Skipping delete of older plugin backups...");}
        else{
            logger.global_info(" - Scanning for plugins backups older than "+max_days_plugins+" days...");

            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_plugins); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_plugins, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            logger.global_info(" - Removed " +deleted_files+ " zips.");
        }


        if (BackupConfig.backup_plugins){
            logger.global_info(" - Creating plugins backup...");

            try (ProgressBar pb = new ProgressBarBuilder()
                    .setInitialMax(1)
                    .setTaskName("Plugins backup: ")
                    .build()) {

                new ZipFile(plugins_backup_dest).addFolder(GD.PLUGINS_DIR);
                pb.step();

            } catch (ZipException e) {
                e.printStackTrace();
            }

            logger.global_info(" - Created plugins backup at:" + plugins_backup_dest);
        } else{
            logger.global_info(" - Skipping plugins backup...");
        }

    }

    public void createBackupWorldFolders(){

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
        String formattedDate = date.format(formatter);

        String worlds_backup_dest = autoplug_backups_worlds.getAbsolutePath()+"/worlds-backup-"+formattedDate+".zip";
        int max_days_worlds = BackupConfig.backup_worlds_max_days;

        //Removes files older than user defined days
        logger.global_info(" ");
        if (max_days_worlds<=0) {logger.global_info(" - Skipping delete of older worlds backups...");}
        else{
            logger.global_info(" - Scanning for worlds backups older than "+max_days_worlds+" days...");

            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_worlds); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_worlds, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            logger.global_info(" - Removed " +deleted_files+ " zips.");
        }


        if (BackupConfig.backup_worlds) {
            logger.global_info(" - Creating worlds backup...");

            List<File> worldsFiles = new FileManager().serverWorldsFolders();
            ZipFile zip = new ZipFile(worlds_backup_dest);

            try (ProgressBar pb = new ProgressBarBuilder()
                    .setInitialMax(worldsFiles.size())
                    .setTaskName("Worlds backup: ")
                    .build()) {

                for (int i = 0; i < worldsFiles.size(); i++) {
                    zip.addFolder(worldsFiles.get(i));
                    pb.step();
                }
            } catch (ZipException e) {
                e.printStackTrace();
            }

            logger.global_info(" - Created worlds backup at:" + worlds_backup_dest);
        } else{
            logger.global_info(" - Skipping worlds backup...");
        }

    }


}
