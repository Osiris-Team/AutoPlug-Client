/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.tasks.updater.TaskDownload;
import com.osiris.autoplug.client.tasks.updater.search.GithubSearch;
import com.osiris.autoplug.client.tasks.updater.search.JenkinsSearch;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.SteamCMD;
import com.osiris.autoplug.client.utils.UtilsLists;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BWarning;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;
import me.hsgamer.mcserverupdater.UpdateBuilder;
import me.hsgamer.mcserverupdater.UpdateStatus;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static com.osiris.jprocesses2.util.OS.isWindows;

public class TaskServerUpdater extends BThread {
    public File downloadsDir = GD.DOWNLOADS_DIR;
    public File serverExe = Server.getServerExecutable();
    private UpdaterConfig updaterConfig;
    private String profile;
    private String serverSoftware;
    private String serverVersion;

    public TaskServerUpdater(String name, BThreadManager manager) throws NotLoadedException, YamlReaderException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, IllegalListException {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        if (Server.isRunning()) throw new Exception("Cannot perform update while server is running!");
        updaterConfig = new UpdaterConfig();
        if (!updaterConfig.server_updater.asBoolean()) {
            skip();
            return;
        }
        profile = updaterConfig.server_updater_profile.asString();
        serverSoftware = updaterConfig.server_software.asString();
        serverVersion = updaterConfig.server_version.asString();

        setStatus("Searching for updates...");

        boolean isSteamAppId = false;
        try {
            Integer.parseInt(updaterConfig.server_software.asString());
            // Steam ids are only numbers, thus
            // if this fails we know it's not a steam id
            isSteamAppId = true;
        } catch (Exception e) {
        }

        if (updaterConfig.server_github_repo_name.asString() != null || updaterConfig.server_jenkins_project_url.asString() != null) {
            doAlternativeUpdatingLogic();
        } else {
            if (isSteamAppId)
                doSteamUpdaterLogic();
            else
                doMCServerUpdaterLogic();
        }
        finish();
    }

    private void doAlternativeUpdatingLogic() throws YamlWriterException, IOException, InterruptedException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException {
        SearchResult sr = null;
        if (updaterConfig.server_github_repo_name.asString() != null) {
            sr = new GithubSearch().search(updaterConfig.server_github_repo_name.asString(),
                    updaterConfig.server_github_asset_name.asString(),
                    updaterConfig.server_github_version.asString());
            if (sr.resultCode == 0) {
                setStatus("Your server is on the latest version!");
                setSuccess(true);
                return;
            }
            if (sr.resultCode == 1) {
                doInstallDependingOnProfile(updaterConfig.server_github_version.asString(), sr.latestVersion, sr.downloadUrl, sr.fileName);
            }
        } else {
            sr = new JenkinsSearch().search(updaterConfig.server_jenkins_project_url.asString(),
                    updaterConfig.server_jenkins_artifact_name.asString(),
                    updaterConfig.server_jenkins_build_id.asInt());

            if (sr.resultCode == 0) {
                setStatus("Your server is on the latest version!");
                setSuccess(true);
                return;
            }
            if (sr.resultCode == 1) {
                doInstallDependingOnProfile(updaterConfig.server_jenkins_build_id.asString(), sr.latestVersion, sr.downloadUrl, sr.fileName);
            }
        }
    }

    private void doInstallDependingOnProfile(String version, String latestVersion, String downloadUrl, String onlineFileName) throws IOException, InterruptedException, YamlWriterException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException {
        if (profile.equals("NOTIFY")) {
            setStatus("Update found (" + version + " -> " + latestVersion + ")!");
        } else if (profile.equals("MANUAL")) {
            setStatus("Update found (" + version + " -> " + latestVersion + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + onlineFileName);
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), downloadUrl, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500); // Wait until download is finished
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded successfully.");
                        setSuccess(true);
                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        } else {
            setStatus("Update found (" + version + " -> " + latestVersion + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + onlineFileName);
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), downloadUrl, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        File final_dest = serverExe;
                        if (final_dest == null)
                            final_dest = new File(GD.WORKING_DIR + "/" + onlineFileName);
                        if (final_dest.exists()) final_dest.delete();
                        final_dest.createNewFile();
                        FileUtils.copyFile(cache_dest, final_dest);
                        setStatus("Server update was installed successfully (" + version + " -> " + latestVersion + ")!");
                        updaterConfig.server_jenkins_build_id.setValues("" + latestVersion);
                        updaterConfig.save();
                        setSuccess(true);
                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        }
    }

    private void doMCServerUpdaterLogic() throws Exception {
        UpdateBuilder updateBuilder = UpdateBuilder
                .updateProject(serverSoftware)
                .version(serverVersion)
                .debugConsumer(s -> {
                    AL.debug(getClass(), s);
                    setStatus(s);
                });

        // Change the output file based on the profile.
        File outputFile;
        if (profile.equals("MANUAL")) {
            outputFile = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
        } else if (serverExe == null) {
            outputFile = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
        } else {
            outputFile = serverExe;
        }
        // The update process will create the output file if it doesn't exist.
        updateBuilder.outputFile(outputFile);

        // Set the working directory for the updater
        updateBuilder.workingDirectory(downloadsDir);

        // If it's NOTIFY profile, we don't need to download anything, only check if the server is up-to-date.
        if (profile.equals("NOTIFY")) {
            updateBuilder.checkOnly(true);
        }

        // Use build-id from the config as the checksum.
        // Note that each software has a different form of checksum, so we just inspect the checksum as a string.
        updateBuilder.checksumSupplier(updaterConfig.server_build_id::asString);
        updateBuilder.checksumConsumer(checksum -> {
            updaterConfig.server_build_id.setValues(checksum);
            try {
                updaterConfig.save();
            } catch (YamlWriterException | YamlReaderException | IllegalListException | DuplicateKeyException e) {
                throw new IOException(e);
            }
        });

        // Do the update
        UpdateStatus status = updateBuilder.execute();
        if (status == UpdateStatus.OUT_OF_DATE) {
            setStatus("Update found!");
        } else {
            setStatus(status.getMessage());
        }
        if (status.getThrowable() != null)
            addWarning(new BWarning(this, status.getThrowable()));
        setSuccess(status.isSuccessStatus());
    }

    private void doSteamUpdaterLogic() throws Exception {
        if (profile.equals("NOTIFY")) {
            setStatus("NOTIFY profile is not supported by SteamCMD.");
            setSuccess(false);
            return;
        }

        SteamCMD steamCMD = new SteamCMD();
        File serverDir = new File(steamCMD.dirSteamServersDownloads + "/" + updaterConfig.server_software.asString());
        boolean isFirstInstall = !serverDir.exists();
        boolean isSuccess = steamCMD.installOrUpdateServer(updaterConfig.server_software.asString(), line -> {
            if (line.startsWith("Waiting for user info")) // Download status not shown, stays stuck at this message for me on Windows, don't know why.
                line = "Downloading files, this might take a bit...";
            AL.debug(this.getClass(), "SteamCMD-Out: " + line);
            setStatus(line);
        }, errLine -> {
            AL.debug(this.getClass(), "SteamCMD-Err-Out: " + errLine);
            setStatus(errLine);
            addWarning(errLine);
        });
        if (!isSuccess) {
            setSuccess(false);
            return;
        }
        if (profile.equals("AUTOMATIC")) {
            setStatus("Moving files, this might take a bit...");
            FileUtils.copyDirectory(serverDir, GD.WORKING_DIR);
            if (isFirstInstall) { // Update start command
                String startCommand = (isWindows ? ".\\" : "./");
                GeneralConfig general = new GeneralConfig();
                File serverExe = new FileManager().serverExecutable(serverDir); // Find exe
                if (serverExe == null)
                    addWarning("Failed to determine server exe, define the start command yourself: " +
                            new UtilsLists().toString(general.server_start_command.getKeys()));
                else {
                    startCommand += serverExe.getName() + " +server.port 25565";
                    general.server_start_command.setValues(startCommand);
                    general.save();
                }
            }
        }

        setStatus("Installed updated if needed (SteamCMD).");
        setSuccess(true);
    }
}
