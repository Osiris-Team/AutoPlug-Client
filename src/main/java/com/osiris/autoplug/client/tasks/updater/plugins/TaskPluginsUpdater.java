/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.configs.PluginsConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.network.online.connections.ConPluginsUpdateResult;
import com.osiris.autoplug.client.tasks.updater.UtilsUpdater;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsFile;
import com.osiris.autoplug.client.utils.UtilsLists;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BWarning;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.json.exceptions.HttpErrorException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TaskPluginsUpdater extends BThread {
    private final String notifyProfile = "NOTIFY";
    private final String manualProfile = "MANUAL";
    private final String automaticProfile = "AUTOMATIC";
    private final List<TaskPluginDownload> downloadTasksList = new ArrayList<>();
    @NotNull
    private final List<MinecraftPlugin> includedPlugins = new ArrayList<>();
    @NotNull
    private final List<MinecraftPlugin> allPlugins = new ArrayList<>();
    @NotNull
    private final List<MinecraftPlugin> excludedPlugins = new ArrayList<>();
    private final Gson gson = new GsonBuilder().create();
    PluginsConfig pluginsConfig;
    private UpdaterConfig updaterConfig;
    private String userProfile;
    private String pluginsConfigName;
    private int updatesAvailable = 0;
    private GeneralConfig generalConfig;

    public TaskPluginsUpdater(String name, BThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        pluginsConfig = new PluginsConfig();
        pluginsConfig.load(); // No lock needed, since there are no other threads that access this file
        String name = pluginsConfig.getFileNameWithoutExt();

        pluginsConfigName = pluginsConfig.getFileNameWithoutExt();
        generalConfig = new GeneralConfig();
        updaterConfig = new UpdaterConfig();
        userProfile = updaterConfig.plugins_updater_profile.asString();

        if (!updaterConfig.plugins_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform plugins update while server is running!");

        boolean isPremiumServer = false;
        if (updaterConfig.plugins_updater_web_database.asBoolean()) {
            try {
                JsonObject obj = new JsonObject();
                obj.addProperty("serverKey", generalConfig.server_key.asString());
                obj.addProperty("name", "PluginName");
                obj.addProperty("author", "PluginAuthor");
                Json.post(GD.OFFICIAL_WEBSITE + "api/minecraft-plugin-details", obj);
                // Status code 403 if not registered server or not premium server
                isPremiumServer = true;
            } catch (HttpErrorException e) {
                if (e.getHttpErrorCode() == 403)
                    getWarnings().add(new BWarning(this, "This server is either not registered or not premium," +
                            " thus failed to fill in missing plugin details via the AutoPlug-Web database. Disable " +
                            new UtilsLists().toString(updaterConfig.plugins_updater_web_database.getKeys())
                            + " in the /autoplug/updater.yml to hide this warning."));
                else if (e.getHttpErrorCode() == 404) {
                    // We expect this error code since PluginName and PluginAuthor should not exist in the database
                    isPremiumServer = true;
                } else
                    getWarnings().add(new BWarning(this, e));
            } catch (Exception e) {
                getWarnings().add(new BWarning(this, e));
            }
        }

        UtilsMinecraft utilsMinecraft = new UtilsMinecraft();
        this.allPlugins.addAll(utilsMinecraft.getPlugins(FileManager.convertRelativeToAbsolutePath(updaterConfig.plugins_updater_path.asString())));
        for (MinecraftPlugin installedPlugin :
                allPlugins) {
            try {
                final String plName = installedPlugin.getName();
                if (installedPlugin.getName() == null || installedPlugin.getName().isEmpty())
                    throw new Exception("The plugins name couldn't be determined for '" + installedPlugin.getInstallationPath() + "'!");

                YamlSection exclude = pluginsConfig.put(name, plName, "exclude").setDefValues("false"); // Check this plugin?
                if (exclude.asBoolean()) {
                    excludedPlugins.add(installedPlugin);
                    continue;
                }
                YamlSection version = pluginsConfig.put(name, plName, "version").setDefValues(installedPlugin.getVersion());
                YamlSection latestVersion = pluginsConfig.put(name, plName, "latest-version");
                YamlSection author = pluginsConfig.put(name, plName, "author").setDefValues(installedPlugin.getAuthor());
                YamlSection spigotId = pluginsConfig.put(name, plName, "spigot-id").setDefValues("0");
                YamlSection modrinthId = pluginsConfig.put(name, plName, "modrinth-id");
                YamlSection bukkitId = pluginsConfig.put(name, plName, "bukkit-id").setDefValues("0");
                YamlSection ignoreContentType = pluginsConfig.put(name, plName, "ignore-content-type").setDefValues("false");
                YamlSection forceUpdate = pluginsConfig.put(name, plName, "force-update").setDefValues("false");
                YamlSection customCheckURL = pluginsConfig.put(name, plName, "custom-check-url");
                YamlSection customDownloadURL = pluginsConfig.put(name, plName, "custom-download-url");
                YamlSection githubRepoName = pluginsConfig.put(name, plName, "alternatives", "github", "repo-name");
                YamlSection githubAssetName = pluginsConfig.put(name, plName, "alternatives", "github", "asset-name");
                YamlSection jenkinsProjectUrl = pluginsConfig.put(name, plName, "alternatives", "jenkins", "project-url");
                YamlSection jenkinsArtifactName = pluginsConfig.put(name, plName, "alternatives", "jenkins", "artifact-name");
                YamlSection jenkinsBuildId = pluginsConfig.put(name, plName, "alternatives", "jenkins", "build-id").setDefValues("0");

                // The plugin devs can add their spigot/bukkit ids to their plugin.yml files
                if (installedPlugin.getSpigotId() != 0 && spigotId.asString() != null && spigotId.asInt() == 0) // Don't update the value, if the user has already set it
                    spigotId.setValues(String.valueOf(installedPlugin.getSpigotId()));
                if (installedPlugin.getBukkitId() != 0 && bukkitId.asString() != null && bukkitId.asInt() == 0)
                    bukkitId.setValues(String.valueOf(installedPlugin.getBukkitId()));

                // Fetch missing details from the AutoPlug-Web database
                if (isPremiumServer) {
                    try {
                        if (plName != null && author.asString() != null && !author.asString().trim().isEmpty()) {
                            JsonObject request = new JsonObject();
                            request.addProperty("serverKey", generalConfig.server_key.asString());
                            request.addProperty("name", plName);
                            request.addProperty("author", author.asString());
                            JsonObject result = Json.post(GD.OFFICIAL_WEBSITE + "api/minecraft-plugin-details", request).getAsJsonObject();
                            Type typeOfSet = new TypeToken<HashSet<Entry>>() {
                            }.getType();
                            UtilsUpdater.Entry webSpigotId = UtilsUpdater.getValidEntryWithMostUsages(gson.fromJson(result.get("spigotId").getAsString(), typeOfSet), updaterConfig.plugins_updater_web_database_min_usages.asInt());
                            UtilsUpdater.Entry webBukkitId = UtilsUpdater.getValidEntryWithMostUsages(gson.fromJson(result.get("bukkitId").getAsString(), typeOfSet), updaterConfig.plugins_updater_web_database_min_usages.asInt());
                            UtilsUpdater.Entry webGithubRepoName = UtilsUpdater.getValidEntryWithMostUsages(gson.fromJson(result.get("githubRepoName").getAsString(), typeOfSet), updaterConfig.plugins_updater_web_database_min_usages.asInt());
                            UtilsUpdater.Entry webGithubAssetName = UtilsUpdater.getValidEntryWithMostUsages(gson.fromJson(result.get("githubAssetName").getAsString(), typeOfSet), updaterConfig.plugins_updater_web_database_min_usages.asInt());
                            UtilsUpdater.Entry webJenkinsProjectUrl = UtilsUpdater.getValidEntryWithMostUsages(gson.fromJson(result.get("jenkinsProjectUrl").getAsString(), typeOfSet), updaterConfig.plugins_updater_web_database_min_usages.asInt());
                            UtilsUpdater.Entry webJenkinsArtifactName = UtilsUpdater.getValidEntryWithMostUsages(gson.fromJson(result.get("jenkinsArtifactName").getAsString(), typeOfSet), updaterConfig.plugins_updater_web_database_min_usages.asInt());

                            // Fill missing id information
                            if (webSpigotId != null && (spigotId.asString() == null || spigotId.asInt() == 0))
                                spigotId.setValues(webSpigotId.key);
                            if (webBukkitId != null && (bukkitId.asString() == null || bukkitId.asInt() == 0))
                                bukkitId.setValues(webBukkitId.key);

                            // Fill missing alternative information, only if it has more usages than the ids
                            if (githubRepoName.asString() == null && githubAssetName.asString() == null
                                    && webGithubRepoName != null && webGithubAssetName != null
                                    && (webSpigotId != null && webSpigotId.usage < webGithubRepoName.usage)
                                    && (webBukkitId != null && webBukkitId.usage < webGithubRepoName.usage)) {
                                spigotId.setValues("0");
                                bukkitId.setValues("0");
                                githubRepoName.setValues(webGithubRepoName.key);
                                githubAssetName.setValues(webGithubAssetName.key);
                            }
                            if (jenkinsProjectUrl.asString() == null && jenkinsArtifactName.asString() == null
                                    && webJenkinsProjectUrl != null && webJenkinsArtifactName != null
                                    && (webSpigotId != null && webSpigotId.usage < webJenkinsProjectUrl.usage)
                                    && (webBukkitId != null && webBukkitId.usage < webJenkinsProjectUrl.usage)) {
                                spigotId.setValues("0");
                                bukkitId.setValues("0");
                                jenkinsProjectUrl.setValues(webJenkinsProjectUrl.key);
                                jenkinsArtifactName.setValues(webJenkinsArtifactName.key);
                            }
                        }
                    } catch (HttpErrorException e) {
                        // Ignore 404 because it means that name,author combination doesn't exist in the database yet
                        if (e.getHttpErrorCode() != 404)
                            addWarning(new BWarning(this, e, "Issues for " + plName));
                    } catch (Exception e) {
                        addWarning(new BWarning(this, e, "Issues for " + plName));
                    }
                }

                // Update the detailed plugins in-memory values
                installedPlugin.setSpigotId(spigotId.asInt());
                installedPlugin.setBukkitId(bukkitId.asInt());
                installedPlugin.setIgnoreContentType(ignoreContentType.asBoolean());
                installedPlugin.setCustomDownloadURL(customDownloadURL.asString());
                installedPlugin.setCustomCheckURL(customCheckURL.asString());
                installedPlugin.setGithubRepoName(githubRepoName.asString());
                installedPlugin.setGithubAssetName(githubAssetName.asString());
                installedPlugin.setJenkinsProjectUrl(jenkinsProjectUrl.asString());
                installedPlugin.setJenkinsArtifactName(jenkinsArtifactName.asString());
                installedPlugin.setJenkinsBuildId(jenkinsBuildId.asInt());
                installedPlugin.setModrinthId(modrinthId.asString());
                installedPlugin.forceUpdate = forceUpdate.asBoolean();

                // Check for missing plugin details in plugin.yml
                if (jenkinsArtifactName.asString() != null && jenkinsProjectUrl.asString() != null)
                    exclude.setValues("false");
                else if (githubAssetName.asString() != null && githubRepoName.asString() != null)
                    exclude.setValues("false");
                else if (spigotId.asString() != null && spigotId.asInt() != 0)
                    exclude.setValues("false");
                else if (bukkitId.asString() != null && bukkitId.asInt() != 0)
                    exclude.setValues("false");
                else if (installedPlugin.getVersion() == null || installedPlugin.getVersion().trim().isEmpty()) {
                    exclude.setValues("true");
                    this.addWarning("Plugin " + installedPlugin.getName() + " is missing 'version' in its plugin.yml file and was excluded." +
                            " Provide additional information in /autoplug/plugins.yml.");
                } else if (installedPlugin.getAuthor() == null || installedPlugin.getAuthor().trim().isEmpty()) {
                    exclude.setValues("true");
                    this.addWarning("Plugin " + installedPlugin.getName() + " is missing 'author' or 'authors' in its plugin.yml file and was excluded." +
                            " Provide additional information in /autoplug/plugins.yml.");
                } else {
                    // Probably first time using this plugin, thus no information available yet
                    exclude.setValues("false");
                }

                if (exclude.asBoolean())
                    excludedPlugins.add(installedPlugin);
                else
                    includedPlugins.add(installedPlugin);
            } catch (DuplicateKeyException e) {
                addWarning(new BWarning(this, e, "Duplicate plugin '" + installedPlugin.getName() + "' (or plugin name from its plugin.yml) found in your plugins directory. " +
                        "Its recommended to remove it."));
            } catch (Exception e) {
                addWarning(new BWarning(this, e));
            }
        }

        if (pluginsConfig.keep_removed.asBoolean())
            pluginsConfig.save();
        else {
            pluginsConfig.save(true); // This overwrites the file and removes everything else that wasn't added via the add method before.
        }

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
        int includedSize = includedPlugins.size();
        if (includedSize == 0)
            throw new Exception("No plugins found in " + updaterConfig.plugins_updater_path.asString() + "! Nothing to check...");
        setMax(includedSize);

        // TODO USE THIS FOR RESULT REPORT
        int sizeCustomPlugins = 0;
        int sizeJenkinsPlugins = 0;
        int sizeGithubPlugins = 0;
        int sizeSpigotPlugins = 0;
        int sizeBukkitPlugins = 0;
        int sizeModrinthPlugins = 0;
        int sizeUnknownPlugins = 0;

        ExecutorService executorService;
        if (updaterConfig.plugins_updater_async.asBoolean())
            executorService = Executors.newFixedThreadPool(includedSize);
        else
            executorService = Executors.newSingleThreadExecutor();
        List<Future<SearchResult>> activeFutures = new ArrayList<>();
        UpdaterConfig updaterConfig = new UpdaterConfig();

        String mcVersion = updaterConfig.plugins_updater_version.asString();
        if (mcVersion == null) updaterConfig.server_updater_version.asString();
        if (mcVersion == null) mcVersion = Server.getMCVersion();

        for (MinecraftPlugin pl :
                includedPlugins) {
            try {
                setStatus("Initialising update check for  " + pl.getName() + "...");
                if (pl.customCheckURL != null) { // Custom Check
                    sizeCustomPlugins++;
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findByCustomCheckURL(pl)));
                } else if (pl.getJenkinsProjectUrl() != null) { // JENKINS PLUGIN
                    sizeJenkinsPlugins++;
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findByJenkinsUrl(pl)));
                } else if (pl.getGithubRepoName() != null) { // GITHUB PLUGIN
                    sizeGithubPlugins++;
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findByGithubUrl(pl)));
                } else if (pl.getSpigotId() != 0) {
                    sizeSpigotPlugins++; // SPIGOT PLUGIN
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findPluginBySpigotId(pl)));
                } else if (pl.getBukkitId() != 0) {
                    sizeBukkitPlugins++; // BUKKIT PLUGIN
                    pl.setIgnoreContentType(true); // TODO temporary workaround for xamazon-json content type curseforge/bukkit issue: https://github.com/Osiris-Team/AutoPlug-Client/issues/109
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findPluginByBukkitId(pl)));
                } else if (pl.getModrinthId() != null) { // MODRINTH PLUGIN
                    sizeModrinthPlugins++;
                    String finalMcVersion = mcVersion;
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findPluginByModrinthId(pl, finalMcVersion)));
                } else {
                    sizeUnknownPlugins++; // UNKNOWN PLUGIN
                    pl.setIgnoreContentType(true); // TODO temporary workaround for xamazon-json content type curseforge/bukkit issue: https://github.com/Osiris-Team/AutoPlug-Client/issues/109
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findUnknownSpigotPlugin(pl)));
                }
            } catch (Exception e) {
                this.getWarnings().add(new BWarning(this, e, "Critical error while searching for update for '" + pl.getName() + "' plugin!"));
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
                MinecraftPlugin pl = result.getPlugin();
                SearchResult.Type code = result.type;
                String type = result.getDownloadType(); // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
                String latest = result.getLatestVersion(); // The latest version as String
                String downloadUrl = result.getDownloadUrl(); // The download url for the latest version
                String resultSpigotId = result.getSpigotId();
                String resultBukkitId = result.getBukkitId();
                this.setStatus("Checked '" + pl.getName() + "' plugin (" + results.size() + "/" + includedSize + ")");
                if (code == SearchResult.Type.UP_TO_DATE || code == SearchResult.Type.UPDATE_AVAILABLE) {

                    if (code == SearchResult.Type.UPDATE_AVAILABLE && pl.isPremium())
                        getWarnings().add(new BWarning(this,
                                result.getPlugin().getName() + " (" + result.getLatestVersion() + ") is a premium plugin and thus not supported by the regular plugin updater!"));
                    else
                        doDownloadLogic(pl, result);

                } else if (code == SearchResult.Type.API_ERROR)
                    if (result.getException() != null)
                        getWarnings().add(new BWarning(this, result.getException(), "There was an api-error for " + pl.getName() + "!"));
                    else
                        getWarnings().add(new BWarning(this, new Exception("There was an api-error for " + pl.getName() + "!")));
                else if (code == SearchResult.Type.RESOURCE_NOT_FOUND)
                    getWarnings().add(new BWarning(this, new Exception("Plugin " + pl.getName() + " was not found by the search-algorithm! Specify an id in the /autoplug/plugins.yml file.")));
                else
                    getWarnings().add(new BWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));

                try {
                    YamlSection mSpigotId = pluginsConfig.get(pluginsConfigName, pl.getName(), "spigot-id");
                    if (resultSpigotId != null
                            && (mSpigotId.asString() == null || mSpigotId.asInt() == 0)) // Because we can get a "null" string from the server
                        mSpigotId.setValues(resultSpigotId);

                    YamlSection mBukkitId = pluginsConfig.get(pluginsConfigName, pl.getName(), "bukkit-id");
                    if (resultBukkitId != null
                            && (mSpigotId.asString() == null || mSpigotId.asInt() == 0)) // Because we can get a "null" string from the server
                        mBukkitId.setValues(resultBukkitId);

                    // The config gets saved at the end of the runAtStart method.
                } catch (Exception e) {
                    getWarnings().add(new BWarning(this, e));
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
                    matchingResult.type = SearchResult.Type.UPDATE_DOWNLOADED;

                if (finishedDownloadTask.isInstallSuccessful()) {
                    matchingResult.type = SearchResult.Type.UPDATE_INSTALLED;
                    YamlSection jenkinsBuildId = pluginsConfig.get(
                            pluginsConfigName, finishedDownloadTask.getPlName(), "alternatives", "jenkins", "build-id");
                    jenkinsBuildId.setValues(String.valueOf(finishedDownloadTask.searchResult.jenkinsId));
                    YamlSection version = pluginsConfig.get(
                            pluginsConfigName, finishedDownloadTask.getPlName(), "version");
                    version.setValues(finishedDownloadTask.searchResult.getLatestVersion());
                }

            }
        }

        if (new WebConfig().send_plugins_updater_results.asBoolean()) {
            setStatus("Sending update check results to AutoPlug-Web...");
            try {
                new ConPluginsUpdateResult(results, excludedPlugins)
                        .open();
            } catch (Exception e) {
                addWarning(new BWarning(this, e));
            }
        }

        pluginsConfig.save();
        if (excludedPlugins.size() > 0) {
            includedSize += excludedPlugins.size();
            finish("Checked " + results.size() + "/" + includedSize + " plugins. Some plugins were excluded.");
        } else {
            finish("Checked " + results.size() + "/" + includedSize + " plugins.");
        }

    }

    private void doDownloadLogic(@NotNull MinecraftPlugin pl, SearchResult result) {
        SearchResult.Type code = result.type;
        String type = result.getDownloadType(); // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
        String latest = result.getLatestVersion(); // The latest version as String
        String downloadUrl = result.getDownloadUrl(); // The download url for the latest version
        String resultSpigotId = result.getSpigotId();
        String resultBukkitId = result.getBukkitId();
        if (pl.getCustomDownloadURL() != null) downloadUrl = pl.getCustomDownloadURL();

        if (pl.forceUpdate && code == SearchResult.Type.UP_TO_DATE)
            code = SearchResult.Type.UPDATE_AVAILABLE;

        if (code == SearchResult.Type.UP_TO_DATE) {
            //getSummary().add("Plugin " +pl.getName()+ " is already on the latest version (" + pl.getVersion() + ")"); // Only for testing right now
        } else {
            updatesAvailable++;

            try {
                // Update the in-memory config
                YamlSection mLatest = pluginsConfig.get(pluginsConfigName, pl.getName(), "latest-version");
                mLatest.setValues(latest); // Gets saved later
            } catch (Exception e) {
                getWarnings().add(new BWarning(this, e));
            }

            if (userProfile.equals(notifyProfile)) {
                addInfo("NOTIFY: Plugin '" + pl.getName() + "' has an update available (" + pl.getVersion() + " -> " + latest + "). Download url: " + downloadUrl);
            } else {
                if (type.equals(".jar") || type.equals("external")) { // Note that "external" support is kind off random and strongly dependent on what spigot devs are doing
                    // Make sure that plName and plLatestVersion do not contain any slashes (/ or \) that could break the file name
                    UtilsFile utilsFile = new UtilsFile();
                    pl.setName(utilsFile.getValidFileName(pl.getName()));
                    latest = utilsFile.getValidFileName(latest);
                    if (!pl.isPremium()) {
                        if (userProfile.equals(manualProfile)) {
                            File cache_dest = new File(GD.WORKING_DIR + "/autoplug/downloads/" + pl.getName() + "[" + latest + "].jar");
                            TaskPluginDownload task = new TaskPluginDownload("PluginDownloader", getManager(), pl.getName(), latest, downloadUrl, pl.getIgnoreContentType(), userProfile, cache_dest);
                            task.searchResult = result;
                            downloadTasksList.add(task);
                            task.start();
                        } else {
                            File oldPl = new File(pl.getInstallationPath());
                            File dest = new File(GD.WORKING_DIR + "/plugins/" + pl.getName() + "-LATEST-" + "[" + latest + "]" + ".jar");
                            TaskPluginDownload task = new TaskPluginDownload("PluginDownloader", getManager(), pl.getName(), latest, downloadUrl, pl.getIgnoreContentType(), userProfile, dest, oldPl);
                            task.searchResult = result;
                            downloadTasksList.add(task);
                            task.start();
                        }
                    }
                } else
                    getWarnings().add(new BWarning(this, new Exception("Failed to download plugin update(" + latest + ") for " + pl.getName() + " because of unsupported type: " + type)));
            }
        }

    }

    /**
     * Returns a list containing only plugins, that contain all the information needed to perform a search. <br>
     * That means, that a plugin must have its name, its authors name and its version in its plugin.yml file.
     */
    @NotNull
    public List<MinecraftPlugin> getIncludedPlugins() {
        return includedPlugins;
    }

    @NotNull
    public List<MinecraftPlugin> getExcludedPlugins() {
        return excludedPlugins;
    }

    /**
     * Returns a list containing all plugins found in the /plugins directory. <br>
     */
    @NotNull
    public List<MinecraftPlugin> getAllPlugins() {
        return allPlugins;
    }

    static class Entry {
        public String key;
        public int usage;

        public Entry(String key, int usage) {
            this.key = key;
            this.usage = usage;
        }
    }

}
