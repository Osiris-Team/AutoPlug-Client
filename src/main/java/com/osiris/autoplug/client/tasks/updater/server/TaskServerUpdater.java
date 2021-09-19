/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.tasks.updater.TaskDownload;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.StringComparator;
import com.osiris.autoplug.core.json.JsonTools;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class TaskServerUpdater extends BetterThread {
    private UpdaterConfig updaterConfig;

    public TaskServerUpdater(String name, BetterThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        updaterConfig = new UpdaterConfig();
        if (!updaterConfig.server_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform update while server is running!");

        UpdaterConfig config = new UpdaterConfig();
        if (!config.server_updater.asBoolean()) {
            skip();
            return;
        }

        setStatus("Searching for updates...");

        String profile = config.server_updater_profile.asString();
        File downloadsDir = new File(GD.WORKING_DIR + "/autoplug/downloads");
        if (config.server_jenkins.asBoolean()) {
            String project_url = config.server_jenkins_project_url.asString();
            String artifact_name = config.server_jenkins_artifact_name.asString();
            double minimumSimilarity = Double.parseDouble("0." + config.server_jenkins_artifact_name_similarity.asInt());
            int build_id = 0;
            if (config.server_jenkins_build_id.asString() != null)
                build_id = config.server_jenkins_build_id.asInt();

            JsonTools json_tools = new JsonTools();
            JsonObject json_purpur = json_tools.getJsonObject(project_url + "/api/json");
            JsonObject json_last_successful_build = json_purpur.get("lastSuccessfulBuild").getAsJsonObject();
            int latest_build_id = json_last_successful_build.get("number").getAsInt();
            if (latest_build_id <= build_id) {
                setStatus("Your server is on the latest version!");
                setSuccess(true);
                return;
            }
            String buildUrl = json_last_successful_build.get("url").getAsString() + "/api/json";
            JsonArray arrayArtifacts = json_tools.getJsonObject(buildUrl).getAsJsonArray("artifacts");
            String onlineArtifactFileName = null;
            String download_url = null;
            for (JsonElement e :
                    arrayArtifacts) {
                if (e.getAsJsonObject().get("fileName").getAsString().equals(artifact_name)) {
                    onlineArtifactFileName = e.getAsJsonObject().get("fileName").getAsString();
                    download_url = project_url + "/" + latest_build_id + "/artifact/" + e.getAsJsonObject().get("relativePath").getAsString();
                    break;
                }
            }

            if (download_url == null && config.server_jenkins_artifact_name_similarity.asString() != null)
                for (JsonElement e :
                        arrayArtifacts) {
                    if (StringComparator.similarity(e.getAsJsonObject().get("fileName").getAsString(), artifact_name) > minimumSimilarity) {
                        onlineArtifactFileName = e.getAsJsonObject().get("fileName").getAsString();
                        download_url = project_url + "/" + latest_build_id + "/artifact/" + e.getAsJsonObject().get("relativePath").getAsString();
                        break;
                    }
                }

            if (download_url == null) {
                finish("Failed to find an equal or similar artifact-name '" + artifact_name + "' inside of '" + arrayArtifacts + "'!", false);
                return;
            }

            if (profile.equals("NOTIFY")) {
                setStatus("Update found (" + build_id + " -> " + latest_build_id + ")!");
            } else if (profile.equals("MANUAL")) {
                setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");

                // Download the file
                File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + onlineArtifactFileName);
                if (cache_dest.exists()) cache_dest.delete();
                cache_dest.createNewFile();
                TaskDownload download = new TaskDownload("ServerDownloader", getManager(), download_url, cache_dest);
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
                setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");
                GD.SERVER_JAR = new FileManager().serverJar();
                System.out.println(GD.SERVER_JAR);
                File final_dest = GD.SERVER_JAR;
                if (final_dest == null)
                    final_dest = new File(GD.WORKING_DIR + "/" + onlineArtifactFileName);
                if (final_dest.exists()) final_dest.delete();
                final_dest.createNewFile();

                // Download the file
                File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + onlineArtifactFileName);
                if (cache_dest.exists()) cache_dest.delete();
                cache_dest.createNewFile();
                TaskDownload download = new TaskDownload("ServerDownloader", getManager(), download_url, cache_dest);
                download.start();

                while (true) {
                    Thread.sleep(500);
                    if (download.isFinished()) {
                        if (download.isSuccess()) {
                            FileUtils.copyFile(cache_dest, final_dest);
                            setStatus("Server update was installed successfully (" + build_id + " -> " + latest_build_id + ")!");
                            if (config.server_jenkins_build_id.asString() != null)
                                config.server_jenkins_build_id.setValues("" + latest_build_id);
                            else
                                config.server_build_id.setValues("" + latest_build_id);
                            config.save();
                            setSuccess(true);
                        } else {
                            setStatus("Server update failed!");
                            setSuccess(false);
                        }
                        break;
                    }
                }
            }
            return;
        }

        String name = config.server_software.asString();
        String mc_version = config.server_version.asString();

        int build_id = config.server_build_id.asInt();
        int latest_build_id = getLatestBuildId(name, mc_version);

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
            String build_hash = getLatestBuildHash(name, mc_version, latest_build_id);
            String build_name = getLatestBuildFileName(name, mc_version, latest_build_id);
            String url = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version + "/builds/" + latest_build_id + "/downloads/" + build_name;
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + name + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest);
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
                            setStatus("Downloaded server update is broken. Nothing changed!");
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
            File final_dest = GD.SERVER_JAR;
            if (final_dest == null)
                final_dest = new File(GD.WORKING_DIR + "/" + name + "-latest.jar");
            if (final_dest.exists()) final_dest.delete();
            final_dest.createNewFile();

            // Download the file
            String build_hash = getLatestBuildHash(name, mc_version, latest_build_id);
            String build_name = getLatestBuildFileName(name, mc_version, latest_build_id);
            String url = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version + "/builds/" + latest_build_id + "/downloads/" + build_name;
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + name + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithSHA256(build_hash)) {
                            FileUtils.copyFile(cache_dest, final_dest);
                            setStatus("Server update was installed successfully (" + build_id + " -> " + latest_build_id + ")!");
                            config.server_build_id.setValues("" + latest_build_id);
                            config.save();
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded server update is broken. Nothing changed!");
                            setSuccess(false);
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        }
        finish();
    }

    @Nullable
    private String getLatestBuildHash(String name, String mc_version, int latest_build_id) {
        String result = null;
        final String address = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version + "/builds/" + latest_build_id;
        try {
            result = new JsonTools().getJsonObject(address).getAsJsonObject("downloads").getAsJsonObject("application").get("sha256").getAsString();
            AL.debug(this.getClass(), "Got from paper-api: build-sha256=" + result);
        } catch (Exception e) {
            getWarnings().add(new BetterWarning(this, e, "Failed to get build-sha256 from: " + address));
            setSuccess(false);
        }
        return result;
    }

    @Nullable
    private String getLatestBuildFileName(String name, String mc_version, int latest_build_id) {
        String result = null;
        final String address = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version + "/builds/" + latest_build_id;
        try {
            result = new JsonTools().getJsonObject(address).getAsJsonObject("downloads").getAsJsonObject("application").get("name").getAsString();
            AL.debug(this.getClass(), "Got from paper-api: build-name=" + result);
        } catch (Exception e) {
            getWarnings().add(new BetterWarning(this, e, "Failed to get build-name from: " + address));
            setSuccess(false);
        }
        return result;
    }

    private int getLatestBuildId(String name, String mc_version) {
        int result = 0;
        final String address = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version;
        try {
            JsonArray builds = new JsonTools().getJsonObject(address).getAsJsonArray("builds");
            // Gets the last value in the array (latest). Example: size is 10 but an array starts at 0, that's why we do 10-1=9 to get the last value in the array.
            result = builds.get(builds.size() - 1).getAsInt();
            AL.debug(this.getClass(), "Got from paper-api: build-id=" + result);
        } catch (Exception e) {
            getWarnings().add(new BetterWarning(this, e, "Failed to get latest build-id from: " + address));
            setSuccess(false);
        }
        return result;
    }


}
