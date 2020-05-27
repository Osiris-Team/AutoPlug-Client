/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.managers;

import com.osiris.autoplug.client.utils.Config;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
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

public class BackupManager {

    private AutoPlugLogger logger = new AutoPlugLogger();
    private File autoplug_backups_server = new File(GD.WORKING_DIR+"/autoplug-backups/server");
    private File autoplug_backups_plugins = new File(GD.WORKING_DIR+"/autoplug-backups/plugins");

    public void createBackupServerJar(){

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
        String formattedDate = date.format(formatter);

        String server_backup_dest = autoplug_backups_server.getAbsolutePath()+"/server-backup-"+formattedDate+".zip";
        int max_days_server = Config.backup_server_max_days;

        //Removes files older than user defined days
        if (max_days_server<=0) {logger.global_info(" Skipping delete of older server backups...");}
        else{
            logger.global_info(" Scanning for server backups older than "+max_days_server+" days...");

            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_server); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_server, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            logger.global_info(" Removed " +deleted_files+ " zips.");
        }


        if (Config.backup_server) {
            logger.global_info(" Creating server backup...");

            try {

                File server_jar = null;
                FileManager fileManager = new FileManager();
                //If server jar isn't auto-detect then we search for the specific name given in the config
                if (!Config.server_jar.equals("auto-detect")){
                    server_jar = fileManager.serverJar(Config.server_jar);
                } else {
                    server_jar = fileManager.serverJar();
                }

                new ZipFile(server_backup_dest).addFile(server_jar);
            } catch (ZipException e) {
                e.printStackTrace();
            }

            logger.global_info(" Created server backup at:" + server_backup_dest);
        } else{
            logger.global_info(" Skipping backup-server...");
        }

    }

    public void createBackupPlugins(){

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
        String formattedDate = date.format(formatter);

        String plugins_backup_dest = autoplug_backups_plugins.getAbsolutePath()+"/plugins-backup-"+formattedDate+".zip";
        int max_days_plugins = Config.backup_plugins_max_days;

        if (max_days_plugins<=0) {logger.global_info(" Skipping delete of older plugin backups...");}
        else{
            logger.global_info(" Scanning for plugins backups older than "+max_days_plugins+" days...");

            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_plugins); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_plugins, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            logger.global_info(" Removed " +deleted_files+ " zips.");
        }


        if (Config.backup_plugins){
            logger.global_info(" Creating plugins backup...");

            try {
                new ZipFile(plugins_backup_dest).addFolder(GD.PLUGINS_DIR);
            } catch (ZipException e) {
                e.printStackTrace();
            }

            logger.global_info(" Created plugins backup at:" + plugins_backup_dest);
        } else{
            logger.global_info(" Skipping backup-plugins...");
        }

    }


}
