/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonArray;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.JsonTools;
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
        if (config.server_updater.asBoolean()) {

            setStatus("Searching for updates...");

            String name = config.server_software.asString();
            String mc_version = config.server_version.asString();

            int build_id = config.build_id.asInt();
            int latest_build_id = getLatestBuildId(name, mc_version);

            // Check if the latest build-id is bigger than our current one.
            if (latest_build_id > build_id) {
                String profile = config.server_updater_profile.asString();
                if (profile.equals("NOTIFY")) {
                    setStatus("Update found (" + build_id + " -> " + latest_build_id + ")!");
                } else if (profile.equals("MANUAL")) {
                    setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");

                    // Download the file
                    String build_hash = getLatestBuildHash(name, mc_version, latest_build_id);
                    String build_name = getLatestBuildFileName(name, mc_version, latest_build_id);
                    String url = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version + "/builds/" + latest_build_id + "/downloads/" + build_name;
                    File cache_dest = new File(GD.WORKING_DIR + "/autoplug-downloads/" + name + "-latest.jar");
                    if (cache_dest.exists()) cache_dest.delete();
                    cache_dest.createNewFile();
                    TaskServerDownload download = new TaskServerDownload("ServerDownloader", getManager(), url, cache_dest);
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
                    File final_dest = GD.SERVER_PATH;
                    if (final_dest == null) final_dest = new File(GD.WORKING_DIR + "/" + name + "-latest.jar");
                    if (final_dest.exists()) final_dest.delete();
                    final_dest.createNewFile();

                    // Download the file
                    String build_hash = getLatestBuildHash(name, mc_version, latest_build_id);
                    String build_name = getLatestBuildFileName(name, mc_version, latest_build_id);
                    String url = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version + "/builds/" + latest_build_id + "/downloads/" + build_name;
                    File cache_dest = new File(GD.WORKING_DIR + "/autoplug-downloads/" + name + "-latest.jar");
                    if (cache_dest.exists()) cache_dest.delete();
                    cache_dest.createNewFile();
                    TaskServerDownload download = new TaskServerDownload("ServerDownloader", getManager(), url, cache_dest);
                    download.start();

                    while (true) {
                        Thread.sleep(500);
                        if (download.isFinished()) {
                            if (download.isSuccess()) {
                                setStatus("Server update downloaded. Checking hash...");
                                if (download.compareWithSHA256(build_hash)) {
                                    FileUtils.copyFile(cache_dest, final_dest);
                                    setStatus("Server update was installed successfully (" + build_id + " -> " + latest_build_id + ")!");
                                    config.build_id.setValues("" + latest_build_id);
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

            } else {
                setStatus("Your server is on the latest version!");
                setSuccess(true);
            }

        } else {
            skip();
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
