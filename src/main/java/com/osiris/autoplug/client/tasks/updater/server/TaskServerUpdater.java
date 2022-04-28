/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.tasks.updater.TaskDownload;
import com.osiris.autoplug.client.tasks.updater.search.GithubSearch;
import com.osiris.autoplug.client.tasks.updater.search.JenkinsSearch;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import com.osiris.autoplug.core.json.exceptions.WrongJsonTypeException;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import com.osiris.dyml.exceptions.YamlReaderException;
import com.osiris.dyml.exceptions.YamlWriterException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

public class TaskServerUpdater extends BetterThread {
    public File downloadsDir = GD.DOWNLOADS_DIR;
    private UpdaterConfig updaterConfig;
    private String profile;
    private String serverSoftware;
    private String serverVersion;

    public TaskServerUpdater(String name, BetterThreadManager manager) {
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

        if (updaterConfig.server_github_repo_name.asString() != null || updaterConfig.server_jenkins_project_url.asString() != null) {
            doAlternativeUpdatingLogic();
        } else if (serverSoftware.equalsIgnoreCase("fabric")) {
            doFabricUpdatingLogic();
        } else if (serverSoftware.equalsIgnoreCase("purpur")) {
            doPurpurUpdatingLogic();
        } else {
            doPaperUpdatingLogic();
        }
        finish();
    }

    private void doAlternativeUpdatingLogic() throws YamlWriterException, IOException, InterruptedException, DuplicateKeyException, YamlReaderException, IllegalListException {
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

    private void doInstallDependingOnProfile(String version, String latestVersion, String downloadUrl, String onlineFileName) throws IOException, InterruptedException, YamlWriterException, DuplicateKeyException, YamlReaderException, IllegalListException {
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
            GD.SERVER_JAR = new FileManager().serverExecutable();
            System.out.println(GD.SERVER_JAR);

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
                        File final_dest = GD.SERVER_JAR;
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

    private void doPurpurUpdatingLogic() throws WrongJsonTypeException, IOException, HttpErrorException, InterruptedException, YamlWriterException, DuplicateKeyException, YamlReaderException, IllegalListException, NoSuchAlgorithmException {
        PurpurDownloadsAPI purpurDownloadsAPI = new PurpurDownloadsAPI();
        int buildId = updaterConfig.server_build_id.asInt();
        JsonObject latestBuild = purpurDownloadsAPI.getLatestBuild(serverSoftware.toLowerCase(Locale.ROOT), serverVersion);
        int latestBuildId = latestBuild.get("build").getAsInt();
        String buildHash = latestBuild.get("md5").getAsString();
        String url = purpurDownloadsAPI.getLatestDownloadUrl(serverSoftware.toLowerCase(Locale.ROOT), serverVersion);

        // Check if the latest build-id is bigger than our current one.
        if (latestBuildId <= buildId) {
            setStatus("Your server is on the latest version!");
            setSuccess(true);
            return;
        }

        if (profile.equals("NOTIFY")) {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + ")!");
        } else if (profile.equals("MANUAL")) {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest, true);
            download.start();

            while (true) {
                Thread.sleep(500); // Wait until download is finished
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithMD5(buildHash)) {
                            setStatus("Server update downloaded successfully.");
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded server update is broken (hash check failed). Nothing changed!");
                            setSuccess(false);
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        } else {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest, true);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithMD5(buildHash)) {
                            File final_dest = GD.SERVER_JAR;
                            if (final_dest == null)
                                final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                            if (final_dest.exists()) final_dest.delete();
                            final_dest.createNewFile();
                            FileUtils.copyFile(cache_dest, final_dest);
                            setStatus("Server update was installed successfully (" + buildId + " -> " + latestBuildId + ")!");
                            updaterConfig.server_build_id.setValues("" + latestBuildId);
                            updaterConfig.save();
                            setSuccess(true);
                        } else {
                            if (updaterConfig.server_skip_hash_check.asBoolean()) {
                                addWarning("Note that the hash check failed for this download, but installing anyways because skip-hash-check is enabled!");
                                File final_dest = GD.SERVER_JAR;
                                if (final_dest == null)
                                    final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                                if (final_dest.exists()) final_dest.delete();
                                final_dest.createNewFile();
                                FileUtils.copyFile(cache_dest, final_dest);
                                setStatus("Server update was installed successfully (" + buildId + " -> " + latestBuildId + ")!");
                                updaterConfig.server_build_id.setValues("" + latestBuildId);
                                updaterConfig.save();
                                setSuccess(true);
                            } else {
                                setStatus("Downloaded server update is broken (hash check failed). Nothing changed!");
                                setSuccess(false);
                            }
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        }
    }

    private void doFabricUpdatingLogic() throws WrongJsonTypeException, IOException, HttpErrorException, InterruptedException, YamlWriterException, DuplicateKeyException, YamlReaderException, IllegalListException, NoSuchAlgorithmException {
        FabricDownloadsAPI fabricDownloadsAPI = new FabricDownloadsAPI();
        String[] buildId = updaterConfig.server_build_id.getValue().asArraySplitByColons();
        if (buildId.length <= 1) {
            buildId = new String[]{"0.0.0", "0.0.0"};
        }
        String[] loaderId = buildId[0].split("\\.");
        int loaderMajorId = Integer.parseInt(loaderId[0]);
        int loaderMinorId = Integer.parseInt(loaderId[1]);
        int loaderBuildId = Integer.parseInt(loaderId[2]);
        String[] installerId = buildId[1].split("\\.");
        int installerMajorId = Integer.parseInt(installerId[0]);
        int installerMinorId = Integer.parseInt(installerId[1]);
        int installerBuildId = Integer.parseInt(installerId[2]);
        JsonObject latestLoader = fabricDownloadsAPI.getLatestLoader();
        int latestLoaderMajorId = latestLoader.get("majorID").getAsInt();
        int latestLoaderMinorId = latestLoader.get("minorID").getAsInt();
        int latestLoaderBuildId = latestLoader.get("buildID").getAsInt();
        JsonObject latestInstaller = fabricDownloadsAPI.getLatestInstaller(serverVersion);
        int latestInstallerMajorId = latestInstaller.get("majorID").getAsInt();
        int latestInstallerMinorId = latestInstaller.get("minorID").getAsInt();
        int latestInstallerBuildId = latestInstaller.get("buildID").getAsInt();
        String[] latestBuildId = {latestLoader.get("version").getAsString(), latestInstaller.get("version").getAsString()};
        //String buildHash = latestBuild.get("md5").getAsString();
        String url = fabricDownloadsAPI.getLatestDownloadUrl(serverVersion, latestLoader.get("version").getAsString(), latestInstaller.get("version").getAsString());

        // Check if the latest build-id is bigger than our current one.
        if (
                latestLoaderMajorId <= loaderMajorId &&
                        latestLoaderMinorId <= loaderMinorId &&
                        latestLoaderBuildId <= loaderBuildId &&
                        latestInstallerMajorId <= installerMajorId &&
                        latestInstallerMinorId <= installerMinorId &&
                        latestInstallerBuildId <= installerBuildId
        ) {
            setStatus("Your server is on the latest version!");
            setSuccess(true);
            return;
        }

        if (profile.equals("NOTIFY")) {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + ")!");
        } else if (profile.equals("MANUAL")) {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest, true);
            download.start();

            while (true) {
                Thread.sleep(500); // Wait until download is finished
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded.");
                        /*setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithMD5(buildHash)) {
                            setStatus("Server update downloaded successfully.");
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded server update is broken (hash check failed). Nothing changed!");
                            setSuccess(false);
                        } */

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        } else {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest, true);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded.");
                        /*if (download.compareWithMD5(buildHash)) {
                            File final_dest = GD.SERVER_JAR;
                            if (final_dest == null)
                                final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                            if (final_dest.exists()) final_dest.delete();
                            final_dest.createNewFile();
                            FileUtils.copyFile(cache_dest, final_dest);
                            setStatus("Server update was installed successfully (" + buildId + " -> " + latestBuildId + ")!");
                            updaterConfig.server_build_id.setValues("" + latestBuildId);
                            updaterConfig.save();
                            setSuccess(true);
                        } else {
                            if (updaterConfig.server_skip_hash_check.asBoolean()) {
                                addWarning("Note that the hash check failed for this download, but installing anyways because skip-hash-check is enabled!");
                                File final_dest = GD.SERVER_JAR;
                                if (final_dest == null)
                                    final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                                if (final_dest.exists()) final_dest.delete();
                                final_dest.createNewFile();
                                FileUtils.copyFile(cache_dest, final_dest);
                                setStatus("Server update was installed successfully (" + buildId + " -> " + latestBuildId + ")!");
                                updaterConfig.server_build_id.setValues("" + latestBuildId);
                                updaterConfig.save();
                                setSuccess(true);
                            } else {
                                setStatus("Downloaded server update is broken (hash check failed). Nothing changed!");
                                setSuccess(false);
                            }
                        }*/
                        File final_dest = GD.SERVER_JAR;
                        if (final_dest == null)
                            final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                        if (final_dest.exists()) final_dest.delete();
                        final_dest.createNewFile();
                        FileUtils.copyFile(cache_dest, final_dest);
                        setStatus("Server update was installed successfully (" + Arrays.toString(buildId) + " -> " + Arrays.toString(latestBuildId) + ")!");
                        updaterConfig.server_build_id.setValues("" + String.join(",", latestBuildId));
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

    private void doPaperUpdatingLogic() throws WrongJsonTypeException, IOException, HttpErrorException, InterruptedException, YamlWriterException, DuplicateKeyException, YamlReaderException, IllegalListException {

        PaperDownloadsAPI paperDownloadsAPI = new PaperDownloadsAPI();
        int build_id = updaterConfig.server_build_id.asInt();
        int latest_build_id = paperDownloadsAPI.getLatestBuildId(serverSoftware, serverVersion);

        // Check if the latest build-id is bigger than our current one.
        if (latest_build_id <= build_id) {
            setStatus("Your server is on the latest version!");
            setSuccess(true);
            return;
        }

        if (profile.equals("NOTIFY")) {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + ")!");
        } else if (profile.equals("MANUAL")) {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");

            // Download the file
            String build_hash = paperDownloadsAPI.getLatestBuildHash(serverSoftware, serverVersion, latest_build_id);
            String build_name = paperDownloadsAPI.getLatestBuildFileName(serverSoftware, serverVersion, latest_build_id);

            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(),
                    paperDownloadsAPI.getDownloadUrl(serverSoftware, serverVersion, latest_build_id, build_name),
                    cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500); // Wait until download is finished
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithSHA256(build_hash)) {
                            setStatus("Server update downloaded successfully.");
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded server update is broken (hash check failed). Nothing changed!");
                            setSuccess(false);
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        } else {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");

            // Download the file
            String build_hash = paperDownloadsAPI.getLatestBuildHash(serverSoftware, serverVersion, latest_build_id);
            String build_name = paperDownloadsAPI.getLatestBuildFileName(serverSoftware, serverVersion, latest_build_id);
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(),
                    paperDownloadsAPI.getDownloadUrl(serverSoftware, serverVersion, latest_build_id, build_name),
                    cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithSHA256(build_hash)) {
                            File final_dest = GD.SERVER_JAR;
                            if (final_dest == null)
                                final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                            if (final_dest.exists()) final_dest.delete();
                            final_dest.createNewFile();
                            FileUtils.copyFile(cache_dest, final_dest);
                            setStatus("Server update was installed successfully (" + build_id + " -> " + latest_build_id + ")!");
                            updaterConfig.server_build_id.setValues("" + latest_build_id);
                            updaterConfig.save();
                            setSuccess(true);
                        } else {
                            if (updaterConfig.server_skip_hash_check.asBoolean()) {
                                addWarning("Note that the hash check failed for this download, but installing anyways because skip-hash-check is enabled!");
                                File final_dest = GD.SERVER_JAR;
                                if (final_dest == null)
                                    final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                                if (final_dest.exists()) final_dest.delete();
                                final_dest.createNewFile();
                                FileUtils.copyFile(cache_dest, final_dest);
                                setStatus("Server update was installed successfully (" + build_id + " -> " + latest_build_id + ")!");
                                updaterConfig.server_build_id.setValues("" + latest_build_id);
                                updaterConfig.save();
                                setSuccess(true);
                            } else {
                                setStatus("Downloaded server update is broken (hash check failed). Nothing changed!");
                                setSuccess(false);
                            }
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        }
    }


}
