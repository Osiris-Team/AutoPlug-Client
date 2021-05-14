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
import de.kastenklicker.backup.Upload;
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

public class TaskPluginsBackup extends BetterThread {

    private final File autoplug_backups_server = new File(GD.WORKING_DIR+"/autoplug-backups/server");
    private final File autoplug_backups_plugins = new File(GD.WORKING_DIR+"/autoplug-backups/plugins");
    private final File autoplug_backups_worlds = new File(GD.WORKING_DIR+"/autoplug-backups/worlds");

    private final LocalDateTime date = LocalDateTime.now();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
    private final String formattedDate = date.format(formatter);

    public TaskPluginsBackup(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        createPluginsBackup();
    }

    private void createPluginsBackup() throws Exception{
        if (Server.isRunning()) throw new Exception("Cannot perform backup while server is running!");

        BackupConfig config = new BackupConfig();

        String plugins_backup_dest = autoplug_backups_plugins.getAbsolutePath()+"/plugins-backup-"+formattedDate+".zip";
        int max_days_plugins = config.backup_plugins_max_days.asInt();

        //Removes files older than user defined days
        if (max_days_plugins<=0) {
            setStatus("Skipping delete of older backups...");
        }
        else{
            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_plugins); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_plugins, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
                setStatus("Deleting backups older than "+max_days_plugins+" days... Deleted: "+deleted_files+" zips");
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            setStatus("Deleting backups older than "+max_days_plugins+" days... Deleted: "+deleted_files+" zips");
        }
        Thread.sleep(1000);


        if (config.backup_plugins.asBoolean()){
            setStatus("Creating backup zip...");

            FileManager man = new FileManager();
            List<File> pluginsFiles = man.getFilesFrom(GD.PLUGINS_DIR);
            List<File> pluginsFolders = man.getFoldersFrom(GD.PLUGINS_DIR);
            ZipFile zip = new ZipFile(plugins_backup_dest);

            setMax(pluginsFiles.size() + pluginsFolders.size());

            // Add each folder to the zip
            for (File file : pluginsFolders) {
                setStatus("Backing up plugins... " + file.getName());
                try{
                    zip.addFolder(file);
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e, "Failed to add folder "+file.getName()+" to zip."));
                }
                step();
            }

            //Add each file to the zip
            for (File file : pluginsFiles) {
                setStatus("Backing up plugins... " + file.getName());
                try{
                    zip.addFile(file);
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e, "Failed to add file "+file.getName()+" to zip."));
                }
                step();
            }

            //Upload
            if (config.backup_plugins_upload.asBoolean()) {

                setStatus("Starting upload of plugins-backup...");

                Upload upload = new Upload(config.backup_plugins_upload_host.asString(),
                        config.backup_plugins_upload_port.asInt(),
                        config.backup_plugins_upload_user.asString(),
                        config.backup_plugins_upload_password.asString(),
                        config.backup_plugins_upload_path.asString(),
                        zip.getFile());

                String rsa = config.backup_plugins_upload_rsa.asString();
                try {
                    if (rsa == null || rsa.trim().isEmpty()) upload.ftps();
                    else upload.sftp(rsa.trim());
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e, "Failed to upload plugins-backup."));
                }

                setStatus("Uploaded plugins backup successfully with warnings " + getWarnings().size()+" warning(s)!");
            } else{
                setStatus("Skipped upload of plugins-backup...");
                skip();
            }

            AL.debug(this.getClass(), "Created plugins-backup to: "+plugins_backup_dest);
            setStatus("Created backup zip successfully with "+ getWarnings().size()+" warning(s)!");
        } else{
            setStatus("Skipped plugins-backup...");
            skip();
        }
        finish();
    }

}
