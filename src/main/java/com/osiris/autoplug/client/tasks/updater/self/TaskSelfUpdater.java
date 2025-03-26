/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.self;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.SelfInstaller;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.tasks.updater.TaskDownloadBase;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsJar;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.search.Version;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

public class TaskSelfUpdater extends BThread {
    // These URLS are not allowed to change:
    private final String stableUpdateUrl = "https://raw.githubusercontent.com/Osiris-Team/AutoPlug-Releases/master/stable-builds/update.json";
    private final String betaUpdateUrl = "https://raw.githubusercontent.com/Osiris-Team/AutoPlug-Releases/master/beta-builds/update.json";
    private UpdaterConfig updaterConfig;

    public TaskSelfUpdater(BThreadManager manager) {
        super(manager);
    }

    public TaskSelfUpdater(String name, BThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        updaterConfig = new UpdaterConfig();

        if (!updaterConfig.self_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform self update while server is running!");

        if (updaterConfig.self_updater_build.asString().equals("stable"))
            doUpdating(stableUpdateUrl);
        else
            doUpdating(betaUpdateUrl);
    }

    private void doUpdating(String url) throws Exception {
        // This url contains a JsonArray with JsonObjects, each representing a java application.
        // In this case we are only interested in the AutoPlug-Client.jar with id 0.
        List<JsonObject> apps = Json.getAsList(url);
        JsonObject jsonLatestJar = null;
        for (JsonObject o :
                apps) {
            if (o.get("id").getAsInt() == 0) {
                jsonLatestJar = o;
                break;
            }
        }

        if (jsonLatestJar == null)
            throw new Exception("Failed to find a JsonObject with id=0! Url: " + url);

        // Get latest jars details
        int id = jsonLatestJar.get("id").getAsInt();
        File installationFile = FileManager.convertRelativeToAbsolutePath(jsonLatestJar.get("installation-path").getAsString());
        String version = jsonLatestJar.get("version").getAsString();
        String downloadUrl = jsonLatestJar.get("download-url").getAsString();
        String sha256 = jsonLatestJar.get("sha-256").getAsString();
        long size = jsonLatestJar.get("file-size").getAsLong();
        String launchClass = jsonLatestJar.get("main-class").getAsString();

        // Get current jars details
        Properties currentJar = new UtilsJar().getThisJarsAutoPlugProperties();
        int currentId = Integer.parseInt(currentJar.getProperty("id"));
        String currentInstallationPath = currentJar.getProperty("installation-path");
        String currentVersion = currentJar.getProperty("version");

        // Just to be sure we check if the ids match. Both should be 0.
        if (id != 0 || currentId != 0)
            throw new Exception("The update jars and the current jars ids don't match!");

        // Now we are good to go! Start the download!
        // Check if the latest version is bigger than our current one.
        File downloadsDir = new File(GD.WORKING_DIR + "/autoplug/downloads");
        downloadsDir.mkdirs();
        if (!(Version.isLatestBigger(currentVersion, version))) {
            finish("AutoPlug is on the latest version!");
            return;
        }

        String profile = updaterConfig.self_updater_profile.asString();
        if (profile.equals("NOTIFY")) {
            setStatus("Update found (" + currentVersion + " -> " + version + ")!");
        } else if (profile.equals("MANUAL")) {
            setStatus("Update found (" + currentVersion + " -> " + version + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + installationFile.getName());
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownloadBase download = new TaskDownloadBase("Downloader", getManager(), downloadUrl, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500); // Wait until download is finished
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("AutoPlug update downloaded. Checking checksum...");
                        if (download.compareWithSHA256(sha256)) {
                            // Create the actual update copy file, by simply copying the newly downloaded file.
                            Files.copy(cache_dest.toPath(), new File(downloadsDir.getAbsolutePath() + "/AutoPlug-Client-Copy.jar").toPath(),
                                    StandardCopyOption.REPLACE_EXISTING);
                            setStatus("AutoPlug update downloaded successfully.");
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded AutoPlug update is broken. Nothing changed!");
                            setSuccess(false);
                        }

                    } else {
                        setStatus("AutoPlug update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        } else {
            setStatus("Update found (" + currentVersion + " -> " + version + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + installationFile.getName());
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownloadBase download = new TaskDownloadBase("Downloader", getManager(), downloadUrl, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (!download.isSuccess()) {
                        finish("AutoPlug update failed!", false);
                        return;
                    }
                    setStatus("AutoPlug update downloaded. Checking hash...");
                    if (!download.compareWithSHA256(sha256)) {
                        finish("Downloaded AutoPlug update is broken. Nothing changed!", false);
                        return;
                    }
                    setStatus("Installing AutoPlug update (" + currentVersion + " -> " + version + ")...");
                    // Create the actual update copy file, by simply copying the newly downloaded file.
                    Files.copy(cache_dest.toPath(),
                            new File(downloadsDir.getAbsolutePath() + "/AutoPlug-Client-Copy.jar").toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    // Start that newly downloaded AutoPlug-Client.jar in the downloads dir.
                    // That jar detects, that its started inside of the downloads dir and installs the AutoPlug-Client-Copy.jar and starts it
                    new SelfInstaller().startJarFromPath(cache_dest, cache_dest.getParentFile());
                    System.exit(0);
                    finish(true);
                    break;
                }

            }
        }


    }

}


