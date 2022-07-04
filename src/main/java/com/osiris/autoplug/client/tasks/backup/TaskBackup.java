/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.backup;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsConfig;
import com.osiris.autoplug.client.utils.tasks.CoolDownReport;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BWarning;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ExcludeFileFilter;
import net.lingala.zip4j.model.ZipParameters;
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

public class TaskBackup extends BThread {

    private final File autoplug_backups = new File(GD.WORKING_DIR + "/autoplug/backups");
    private final LocalDateTime date = LocalDateTime.now();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm");
    private final String formattedDate = date.format(formatter);
    public boolean ignoreCooldown;

    public TaskBackup(String name, BThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        autoplug_backups.mkdirs();
        createBackup();
    }

    private void createBackup() throws Exception {
        if (Server.isRunning()) throw new Exception("Cannot perform backup while server is running!");

        BackupConfig config = new BackupConfig();
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.lockFile();
        systemConfig.load();
        // Do cool-down check stuff
        if (!ignoreCooldown) {
            String format = "dd/MM/yyyy HH:mm:ss";
            CoolDownReport coolDownReport = new UtilsConfig().getCoolDown(
                    config.backup_cool_down.asInt(),
                    new SimpleDateFormat(format),
                    systemConfig.timestamp_last_backup.asString()); // Get the report first before saving any new values
            if (coolDownReport.isInCoolDown()) {
                systemConfig.unlockFile();
                this.skip("Skipped. Cool-down still active (" + (((coolDownReport.getMsRemaining() / 1000) / 60)) + " minutes remaining).");
                return;
            }
            // Update the cool-down with current time
            systemConfig.timestamp_last_backup.setValues(LocalDateTime.now().format(DateTimeFormatter.ofPattern(format)));
            systemConfig.save(); // Save the current timestamp to file
            systemConfig.unlockFile();
        }

        String server_backup_dest = autoplug_backups.getAbsolutePath() + "/" + formattedDate + "-BACKUP.zip";
        int max_days_server = config.backup_max_days.asInt();

        //Removes files older than user defined days
        if (max_days_server <= 0) {
            setStatus("Skipping delete of older backups...");
        } else {
            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_server); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups, new AgeFileFilter(oldestAllowedFileDate), null);
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


        if (config.backup.asBoolean()) {
            setStatus("Creating backup zip...");
            ZipFile zip = new ZipFile(server_backup_dest);
            List<File> filesToBackup = new ArrayList<>();

            if (config.backup_include.asBoolean()) filesToBackup.addAll(config.getIncludedFiles());
            if (config.backup_exclude.asBoolean()) {
                List<File> excludedFiles = config.getExcludedFiles();
                for (File file :
                        excludedFiles) {
                    AL.debug(this.getClass(), "Excluded '" + file.getName() + "' from backup. Full path: " + file.getAbsolutePath());
                }
                ExcludeFileFilter excludeFileFilter = excludedFiles::contains;
                setMax(filesToBackup.size());
                for (File file : filesToBackup) { //Add each file to the zip
                    setStatus("Backing up files... " + file.getName());
                    try {
                        ZipParameters zipParameters = new ZipParameters();
                        zipParameters.setExcludeFileFilter(excludeFileFilter);
                        if (file.isDirectory())
                            zip.addFolder(file, zipParameters);
                        else
                            zip.addFile(file, zipParameters);
                    } catch (Exception e) {
                        getWarnings().add(new BWarning(this, e, "Failed to add " + file.getName() + " to zip."));
                    }
                    step();
                }
            } else {
                setMax(filesToBackup.size());
                for (File file : filesToBackup) { //Add each file to the zip
                    setStatus("Backing up files... " + file.getName());
                    try {
                        if (file.isDirectory())
                            zip.addFolder(file);
                        else
                            zip.addFile(file);
                    } catch (Exception e) {
                        getWarnings().add(new BWarning(this, e, "Failed to add " + file.getName() + " to zip."));
                    }
                    step();
                }
            }

            //Upload
            if (config.backup_upload.asBoolean()) {

                setStatus("Uploading server-files backup...");

                Upload upload = new Upload(config.backup_upload_host.asString(),
                        config.backup_upload_port.asInt(),
                        config.backup_upload_user.asString(),
                        config.backup_upload_password.asString(),
                        config.backup_upload_path.asString(),
                        zip.getFile());

                String rsa = config.backup_upload_rsa.asString();
                try {
                    if (rsa == null || rsa.trim().isEmpty()) upload.ftps();
                    else upload.sftp(rsa.trim());

                    if (config.backup_upload_delete_on_complete.asBoolean())
                        zip.getFile().delete();
                } catch (Exception e) {
                    getWarnings().add(new BWarning(this, e, "Failed to upload backup."));
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

            AL.debug(this.getClass(), "Created backup at: " + server_backup_dest);
        } else
            skip();

        finish();
    }

}
