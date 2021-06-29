/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.PluginsConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.connections.PluginsUpdateResultConnection;
import com.osiris.autoplug.client.tasks.updater.plugins.search.SearchMaster;
import com.osiris.autoplug.client.tasks.updater.plugins.search.SearchResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TaskPluginsUpdater extends BetterThread {
    //private final PluginsUpdateResultConnection con;
    private final String notifyProfile = "NOTIFY";
    private final String manualProfile = "MANUAL";
    private final String automaticProfile = "AUTOMATIC";
    private UpdaterConfig updaterConfig;
    private String userProfile;
    private PluginsConfig pluginsConfig;
    private String pluginsConfigName;
    private Socket online_socket;
    private DataInputStream online_dis;
    private DataOutputStream online_dos;
    private int updatesAvailable = 0;
    private final int updatesDownloaded = 0;
    private final List<TaskPluginDownload> downloadTasksList = new ArrayList<>();

    public TaskPluginsUpdater(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        try {
            pluginsConfig = new PluginsConfig();
        } catch (DuplicateKeyException e) {
            getWarnings().add(new BetterWarning(this, e, "Duplicate plugin (or plugin name from its plugin.yml) found in your plugins directory. " +
                    "Remove it and restart AutoPlug."));
            setSuccess(false);
            return;
        }
        pluginsConfigName = pluginsConfig.getFileNameWithoutExt();
        updaterConfig = new UpdaterConfig();
        userProfile = updaterConfig.plugin_updater_profile.asString();

        if (!updaterConfig.plugin_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform plugins update while server is running!");

        // TODO DO COOL-DOWN CHECK STUFF LOCALLY
        /*
        long msLeft = online_dis.readLong(); // 0 if the last plugins check was over 4 hours ago, else it returns the time left, till a new check is allowed
        if (msLeft != 0) {
            skip("Skipped. Cool-down still active (" + (msLeft / 60000) + " minutes remaining).");
            return;
        }

         */

        // First we get the latest plugin details from the yml config.
        // The minimum required information is:
        // name, version, and author. Otherwise they won't get update-checked by AutoPlug (and are not inside the list below).
        setStatus("Fetching latest plugin data...");
        List<DetailedPlugin> plugins = pluginsConfig.getIncludedPlugins();
        int size = plugins.size();
        if (size == 0) throw new Exception("Plugins size is 0! Nothing to check...");
        setMax(size);

        // TODO USE THIS FOR RESULT REPORT
        int sizeSpigotPlugins = 0;
        int sizeBukkitPlugins = 0;
        int sizeCustomLinkPlugins = 0;
        int sizeUnknownPlugins = 0;

        ExecutorService executorService;
        if (updaterConfig.plugin_updater_async.asBoolean())
            executorService = Executors.newFixedThreadPool(size);
        else
            executorService = Executors.newSingleThreadExecutor();
        List<Future<SearchResult>> activeFutures = new ArrayList<>();
        for (DetailedPlugin pl :
                plugins) {
            try {
                setStatus("Initialising update check for  " + pl.getName() + "...");
                if (pl.getSpigotId() != 0) {
                    sizeSpigotPlugins++; // SPIGOT PLUGIN
                    activeFutures.add(executorService.submit(() -> new SearchMaster().searchBySpigotId(pl)));
                } else if (pl.getBukkitId() != 0) {
                    sizeBukkitPlugins++; // BUKKIT PLUGIN
                    activeFutures.add(executorService.submit(() -> new SearchMaster().searchByBukkitId(pl)));
                } else if (pl.getCustomLink() != null && !pl.getCustomLink().isEmpty()) {
                    sizeCustomLinkPlugins++; // CUSTOM LINK PLUGIN
                    if (pl.getSpigotId() != 0)
                        activeFutures.add(executorService.submit(() -> new SearchMaster().searchBySpigotId(pl)));
                    else if (pl.getBukkitId() != 0)
                        activeFutures.add(executorService.submit(() -> new SearchMaster().searchByBukkitId(pl)));
                    else activeFutures.add(executorService.submit(() -> new SearchMaster().unknownSearch(pl)));
                } else {
                    sizeUnknownPlugins++; // UNKNOWN PLUGIN
                    activeFutures.add(executorService.submit(() -> new SearchMaster().unknownSearch(pl)));
                }
            } catch (Exception e) {
                this.getWarnings().add(new BetterWarning(this, e, "Critical error while searching for update for '" + pl.getName() + "' plugin!"));
            }
        }


        List<SearchResult> results = new ArrayList<>();
        while (!activeFutures.isEmpty()) {
            Thread.sleep(250);
            Future<SearchResult> finishedFuture = null;
            for (Future<SearchResult> future :
                    activeFutures) {
                if (future.isDone()) {
                    finishedFuture = future;
                    break;
                }
            }

            if (finishedFuture != null) {
                activeFutures.remove(finishedFuture);
                SearchResult result = finishedFuture.get();
                results.add(result);
                DetailedPlugin pl = result.getPlugin();
                byte code = result.getResultCode();
                String type = result.getDownloadType(); // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
                String latest = result.getLatestVersion(); // The latest version as String
                String downloadUrl = result.getDownloadUrl(); // The download url for the latest version
                String resultSpigotId = result.getSpigotId();
                String resultBukkitId = result.getBukkitId();
                this.setStatus("Checked '" + pl.getName() + "' plugin (" + results.size() + "/" + size + ")");
                if (code == 0 || code == 1) {
                    doDownloadLogic(pl, code, type, latest, downloadUrl, resultSpigotId, resultBukkitId);
                } else if (code == 2)
                    if (result.getException() != null)
                        getWarnings().add(new BetterWarning(this, result.getException(), "There was an api-error for " + pl.getName() + "!"));
                    else
                        getWarnings().add(new BetterWarning(this, new Exception("There was an api-error for " + pl.getName() + "!")));
                else if (code == 3)
                    getWarnings().add(new BetterWarning(this, new Exception("Plugin " + pl.getName() + " was not found by the search-algorithm! Specify an id in the plugins config file.")));
                else
                    getWarnings().add(new BetterWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));

                try {
                    DYModule mSpigotId = pluginsConfig.get(pluginsConfigName, pl.getName(), "spigot-id");
                    if (resultSpigotId != null
                            && (mSpigotId.asString() == null || mSpigotId.asInt() == 0)) // Because we can get a "null" string from the server
                        mSpigotId.setValues(resultSpigotId);

                    DYModule mBukkitId = pluginsConfig.get(pluginsConfigName, pl.getName(), "bukkit-id");
                    if (resultBukkitId != null
                            && (mSpigotId.asString() == null || mSpigotId.asInt() == 0)) // Because we can get a "null" string from the server
                        mBukkitId.setValues(resultBukkitId);

                    // The config gets saved at the end of the runAtStart method.
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e));
                }
            }
        }

        pluginsConfig.save();


        // Wait until all download tasks have finished.
        while (!downloadTasksList.isEmpty()) {
            Thread.sleep(1000);
            TaskPluginDownload finishedDownloadTask = null;
            for (TaskPluginDownload task :
                    downloadTasksList) {
                if (!task.isAlive()) {
                    finishedDownloadTask = task;
                    break;
                }
            }

            if (finishedDownloadTask != null) {
                downloadTasksList.remove(finishedDownloadTask);
                SearchResult matchingResult = null;
                for (SearchResult result :
                        results) {
                    if (result.getPlugin().getName().equals(finishedDownloadTask.getPlName())) {
                        matchingResult = result;
                        break;
                    }
                }
                if (matchingResult == null)
                    throw new Exception("This should not happen! Please report to the devs!");

                if (finishedDownloadTask.isDownloadSuccessful())
                    matchingResult.setResultCode((byte) 5);

                if (finishedDownloadTask.isInstallSuccessful())
                    matchingResult.setResultCode((byte) 6);
            }
        }

        if (new WebConfig().send_plugins_updater_results.asBoolean()) {
            setStatus("Sending update check results to AutoPlug-Web...");
            try {
                new PluginsUpdateResultConnection(results, pluginsConfig.getExcludedPlugins())
                        .open();
            } catch (Exception e) {
                addWarning(new BetterWarning(this, e));
            }
        }

        finish("Finished checking all plugins (" + results.size() + "/" + size + ")");
    }

    private void doDownloadLogic(@NotNull DetailedPlugin pl, byte code, @NotNull String type, String latest, String url, @NotNull String resultSpigotId, @NotNull String resultBukkitId) {
        if (code == 0) {
            //getSummary().add("Plugin " +pl.getName()+ " is already on the latest version (" + pl.getVersion() + ")"); // Only for testing right now
        } else {
            updatesAvailable++;

            try {
                // Update the in-memory config
                DYModule mLatest = pluginsConfig.get(pluginsConfigName, pl.getName(), "latest-version");
                mLatest.setValues(latest);
            } catch (Exception e) {
                getWarnings().add(new BetterWarning(this, e));
            }

            if (userProfile.equals(notifyProfile)) {
                addInfo("NOTIFY: Plugin '" + pl.getName() + "' has an update available (" + pl.getVersion() + " -> " + latest + ")");
            } else {
                if (type.equals(".jar") || type.equals("external")) { // Note that "external" support is kind off random and strongly dependent on what spigot devs are doing
                    if (userProfile.equals(manualProfile)) {
                        File cache_dest = new File(GD.WORKING_DIR + "/autoplug-downloads/" + pl.getName() + "[" + latest + "].jar");
                        TaskPluginDownload task = new TaskPluginDownload("PluginDownloader", getManager(), pl.getName(), latest, url, userProfile, cache_dest);
                        downloadTasksList.add(task);
                        task.start();
                    } else {
                        File oldPl = new File(pl.getInstallationPath());
                        File dest = new File(GD.WORKING_DIR + "/plugins/" + pl.getName() + "-LATEST-" + "[" + latest + "]" + ".jar");
                        TaskPluginDownload task = new TaskPluginDownload("PluginDownloader", getManager(), pl.getName(), latest, url, userProfile, dest, oldPl);
                        downloadTasksList.add(task);
                        task.start();
                    }
                } else
                    getWarnings().add(new BetterWarning(this, new Exception("Failed to download plugin update(" + latest + ") for " + pl.getName() + " because of unsupported type: " + type)));
            }
        }

    }

}
