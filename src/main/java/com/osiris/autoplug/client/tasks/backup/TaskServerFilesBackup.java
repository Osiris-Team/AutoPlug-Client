/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.backup;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.utils.CoolDownReport;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsConfig;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.lang.time.DateUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class TaskServerFilesBackup extends BetterThread {

    private final File autoplug_backups_server = new File(GD.WORKING_DIR + "/autoplug/backups/server");

    private final LocalDateTime date = LocalDateTime.now();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
    private final String formattedDate = date.format(formatter);

    public TaskServerFilesBackup(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        autoplug_backups_server.mkdirs();
        createBackupServerFiles();
    }

    private void createBackupServerFiles() throws Exception {
        if (Server.isRunning()) throw new Exception("Cannot perform backup while server is running!");

        BackupConfig config = new BackupConfig();
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.lockFile();
        systemConfig.load();
        // Do cool-down check stuff
        String format = "dd/MM/yyyy HH:mm:ss";
        CoolDownReport coolDownReport = new UtilsConfig().getCoolDown(
                config.backup_server_files_cool_down.asInt(),
                new SimpleDateFormat(format),
                systemConfig.timestamp_last_server_files_backup_task.asString()); // Get the report first before saving any new values
        if (coolDownReport.isInCoolDown()) {
            systemConfig.unlockFile();
            this.skip("Skipped. Cool-down still active (" + (((coolDownReport.getMsRemaining() / 1000) / 60)) + " minutes remaining).");
            return;
        }
        // Update the cool-down with current time
        systemConfig.timestamp_last_server_files_backup_task.setValues(LocalDateTime.now().format(DateTimeFormatter.ofPattern(format)));
        systemConfig.save(); // Save the current timestamp to file
        systemConfig.unlockFile();


        String server_backup_dest = autoplug_backups_server.getAbsolutePath() + "/server-files-backup-" + formattedDate + ".zip";
        int max_days_server = config.backup_server_files_max_days.asInt();

        //Removes files older than user defined days
        if (max_days_server <= 0) {
            setStatus("Skipping delete of older backups...");
        } else {
            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_server); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_server, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
                setStatus("Deleting backups older than " + max_days_server + " days... Deleted: " + deleted_files + " zips");
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            setStatus("Deleting backups older than " + max_days_server + " days... Deleted: " + deleted_files + " zips");
        }
        Thread.sleep(1000);


        if (config.backup_server_files.asBoolean()) {
            setStatus("Creating backup zip...");
            List<File> serverFiles = new FileManager().serverFiles();
            ZipFile zip = new ZipFile(server_backup_dest);

            if (config.backup_server_files_exclude.asBoolean()) {
                List<File> copyServerFiles = new ArrayList<>(serverFiles);
                List<File> excludedFiles = config.getServerFilesExcluded();
                for (File file :
                        copyServerFiles) {
                    for (File excludeFile :
                            excludedFiles) {
                        if (excludeFile.toPath().equals(file.toPath())) {
                            serverFiles.remove(file);
                            AL.debug(this.getClass(), "Excluded '" + file.getName() + "' from backup. Full path: " + file.getAbsolutePath());
                        }
                    }
                }
            }

            if (config.backup_server_files_include.asBoolean()) {
                List<File> copyServerFiles = new ArrayList<>(serverFiles);
                List<File> includedFiles = config.getServerFilesIncluded();
                List<File> copyIncludedFiles = new ArrayList<>(includedFiles);
                for (File file :
                        copyServerFiles) {
                    for (File includeFile :
                            copyIncludedFiles) {
                        if (includeFile.toPath().equals(file.toPath())) {
                            includedFiles.remove(includeFile);
                            AL.debug(this.getClass(), "File '" + file.getName() + "' couldn't be included, because it is already in the list. Full path: " + file.getAbsolutePath());
                        }
                    }
                }
                serverFiles.addAll(includedFiles);
            }

            setMax(serverFiles.size());

            //Add each file to the zip
            for (File file : serverFiles) {
                setStatus("Backing up server-files... " + file.getName());
                try {
                    if (file.isDirectory())
                        zip.addFolder(file);
                    else
                        zip.addFile(file);
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e, "Failed to add " + file.getName() + " to zip."));
                }
                step();
            }

            //Upload
            if (config.backup_server_files_upload.asBoolean()) {

                setStatus("Uploading server-files backup...");

                Upload upload = new Upload(config.backup_server_files_upload_host.asString(),
                        config.backup_server_files_upload_port.asInt(),
                        config.backup_server_files_upload_user.asString(),
                        config.backup_server_files_upload_password.asString(),
                        config.backup_server_files_upload_path.asString(),
                        zip.getFile());

                String rsa = config.backup_server_files_upload_rsa.asString();
                try {
                    if (rsa == null || rsa.trim().isEmpty()) upload.ftps();
                    else upload.sftp(rsa.trim());

                    if (config.backup_server_files_upload_delete_on_complete.asBoolean())
                        zip.getFile().delete();
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e, "Failed to upload server-files backup."));
                }

                if (getWarnings().size() > 0)
                    setStatus("Completed backup & upload (" + getWarnings().size() + " warnings).");
                else
                    setStatus("Completed backup & upload.");
            } else {
                if (getWarnings().size() > 0)
                    setStatus("Completed backup & skipped upload (" + getWarnings().size() + " warnings).");
                else
                    setStatus("Completed backup & skipped upload.");
            }

            AL.debug(this.getClass(), "Created server-files backup to: " + server_backup_dest);
        } else
            skip();

        finish();
    }

}
