/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.network.online.connections.ConModsUpdateResult;
import com.osiris.autoplug.client.tasks.updater.mods.ModUpdateFinder;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
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

public class TaskModsUpdater extends BetterThread {
    //private final ModsUpdateResultConnection con;
    private final String notifyProfile = "NOTIFY";
    private final String manualProfile = "MANUAL";
    private final String automaticProfile = "AUTOMATIC";
    private final int updatesDownloaded = 0;
    private final List<TaskModDownload> downloadTasksList = new ArrayList<>();
    @NotNull
    private final List<MinecraftMod> includedMods = new ArrayList<>();
    @NotNull
    private final List<MinecraftMod> allMods = new ArrayList<>();
    @NotNull
    private final List<MinecraftMod> excludedMods = new ArrayList<>();
    Yaml modsConfig;
    private UpdaterConfig updaterConfig;
    private String userProfile;
    private String modsConfigName;
    private Socket online_socket;
    private DataInputStream online_dis;
    private DataOutputStream online_dos;
    private int updatesAvailable = 0;

    public TaskModsUpdater(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        modsConfig = new Yaml(System.getProperty("user.dir") + "/autoplug/mods-config.yml");
        modsConfig.load(); // No lock needed, since there are no other threads that access this file
        String name = modsConfig.getFileNameWithoutExt();
        modsConfig.put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Mods-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################\n" +
                        "This file contains detailed information about your installed mods. It is fetched from each mods config file (located inside their jars).\n" +
                        "The data gets refreshed before performing an update-check. To exclude a mod from the check set exclude=true.\n" +
                        "If a name/author/version is missing, the mod gets excluded automatically.\n" +
                        "If there are mods that weren't found by the search-algorithm, you can add an id (spigot or bukkit) and a custom link (optional & must be a static link to the latest mod jar).\n" +
                        //TODO    "modrinth-id:  Can be found directly in the url. Example URLs id is 78414. Example URL: https://www.spigotmc.org/resources/autoplug-automatic-mod-updater.78414/\n" +
                        "bukkit-id: Is the 'Project-ID' and can be found on the mods bukkit site inside of the 'About' box at the right.\n" +
                        "custom-download-url: must be a static url to the mods latest jar file.\n" +
                        "alternatives.github.repo-name: Example: 'EssentialsX/Essentials' (can be found in its url: https://github.com/EssentialsX/Essentials)\n" +
                        "alternatives.github.asset-name: Example: 'EssentialsX' (wrong: 'EssentialsX-1.7.23.jar', we discard the version info).\n" +
                        "alternatives.jenkins.project-url: Example: 'https://ci.ender.zone/job/EssentialsX/'\n" +
                        "alternatives.jenkins.artifact-name: Example: 'EssentialsX' (wrong: 'EssentialsX-1.7.23.jar', we discard the version info).\n" +
                        "alternatives.jenkins.build-id: The currently installed build identifier. Don't touch this.\n" +
                        "If a modrinth-id is not given, AutoPlug will try and find the matching id by using its unique search-algorithm (if it succeeds the modrinth-id gets set, else it stays 0).\n" +
                        "If both (bukkit and modrinth) ids are provided, the modrinth-id will be used.\n" +
                        "The configuration for uninstalled mods wont be removed from this file, but they are automatically excluded from future checks (the exclude value is ignored).\n" +
                        "If multiple authors are provided, only the first author will be used by the search-algorithm.\n" +
                        "Note: Remember, that the values for exclude, version and author get overwritten if new data is available.\n");

        YamlSection keep_removed = modsConfig.put(name, "general", "keep-removed").setDefValues("true")
                .setComments("Keep the mods entry in this file even after its removal/uninstallation?");

        UtilsMinecraft man = new UtilsMinecraft();
        updaterConfig = new UpdaterConfig();
        userProfile = updaterConfig.mods_updater_profile.asString();
        this.allMods.addAll(man.getMods(FileManager.convertRelativeToAbsolutePath(updaterConfig.mods_updater_path.asString())));

        if (!updaterConfig.mods_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform mods update while server is running!");

        if (!allMods.isEmpty())
            for (MinecraftMod installedMod :
                    allMods) {
                try {
                    final String plName = installedMod.name;
                    if (installedMod.name == null || installedMod.name.isEmpty())
                        throw new Exception("The mods name couldn't be determined for '" + installedMod.installationPath + "'!");

                    YamlSection exclude = modsConfig.put(name, plName, "exclude").setDefValues("false");
                    YamlSection version = modsConfig.put(name, plName, "version").setDefValues(installedMod.version);
                    YamlSection latestVersion = modsConfig.put(name, plName, "latest-version");
                    YamlSection author = modsConfig.put(name, plName, "author").setDefValues(installedMod.author);
                    YamlSection modrinthId = modsConfig.put(name, plName, "modrinth-id");
                    YamlSection curseforgeId = modsConfig.put(name, plName, "curseforge-id");
                    YamlSection ignoreContentType = modsConfig.put(name, plName, "ignore-content-type").setDefValues("false");
                    YamlSection customDownloadURL = modsConfig.put(name, plName, "custom-download-url");
                    YamlSection githubRepoUrl = modsConfig.put(name, plName, "alternatives", "github", "repo-name");
                    YamlSection githubAssetName = modsConfig.put(name, plName, "alternatives", "github", "asset-name");
                    YamlSection jenkinsProjectUrl = modsConfig.put(name, plName, "alternatives", "jenkins", "project-url");
                    YamlSection jenkinsArtifactName = modsConfig.put(name, plName, "alternatives", "jenkins", "artifact-name");
                    YamlSection jenkinsBuildId = modsConfig.put(name, plName, "alternatives", "jenkins", "build-id").setDefValues("0");

                    if (installedMod.modrinthId != null) modrinthId.setValues(installedMod.modrinthId);
                    if (installedMod.curseforgeId != null) curseforgeId.setValues(installedMod.curseforgeId);

                    // Update the detailed mods in-memory values
                    installedMod.modrinthId = modrinthId.asString();
                    installedMod.curseforgeId = (curseforgeId.asString());
                    installedMod.ignoreContentType = (ignoreContentType.asBoolean());
                    installedMod.customDownloadURL = (customDownloadURL.asString());
                    installedMod.githubRepoName = (githubRepoUrl.asString());
                    installedMod.githubAssetName = (githubAssetName.asString());
                    installedMod.jenkinsProjectUrl = (jenkinsProjectUrl.asString());
                    installedMod.jenkinsArtifactName = (jenkinsArtifactName.asString());
                    installedMod.jenkinsBuildId = (jenkinsBuildId.asInt());

                    // Check for missing author in internal config
                    if ((installedMod.version == null)
                            && (modrinthId.asString() == null)
                            && (curseforgeId.asString() == null)) {
                        exclude.setValues("true");
                        this.addWarning("Mod " + installedMod.name + " is missing 'version' in its internal config file and was excluded.");
                    }

                    // Check for missing version in internal config
                    if ((installedMod.author == null)
                            && (modrinthId.asString() == null)
                            && (curseforgeId.asString() == null)) {
                        exclude.setValues("true");
                        this.addWarning("Mod " + installedMod.name + " is missing 'author' or 'authors' in its internal config file and was excluded.");
                    }

                    if (exclude.asBoolean())
                        excludedMods.add(installedMod);
                    else
                        includedMods.add(installedMod);
                } catch (DuplicateKeyException e) {
                    addWarning(new BetterWarning(this, e, "Duplicate mod '" + installedMod.name + "' (or mod name from its internal config) found in your mods directory. " +
                            "Its recommended to remove it."));
                } catch (Exception e) {
                    addWarning(new BetterWarning(this, e));
                }
            }

        if (keep_removed.asBoolean())
            modsConfig.save();
        else {
            modsConfig.save(true); // This overwrites the file and removes everything else that wasn't added via the add/put method before.
        }
        modsConfigName = modsConfig.getFileNameWithoutExt();

        // TODO DO COOL-DOWN CHECK STUFF LOCALLY
        /*
        long msLeft = online_dis.readLong(); // 0 if the last mods check was over 4 hours ago, else it returns the time left, till a new check is allowed
        if (msLeft != 0) {
            skip("Skipped. Cool-down still active (" + (msLeft / 60000) + " minutes remaining).");
            return;
        }

         */

        // First we get the latest mod details from the yml config.
        // The minimum required information is:
        // name, version, and author. Otherwise they won't get update-checked by AutoPlug (and are not inside the list below).
        setStatus("Fetching latest mod data...");
        int includedSize = includedMods.size();
        if (includedSize == 0)
            throw new Exception("No mods found in " + updaterConfig.mods_updater_path.asString() + "! Nothing to check...");
        setMax(includedSize);

        // TODO USE THIS FOR RESULT REPORT
        int sizeJenkinsMods = 0;
        int sizeGithubMods = 0;
        int sizemodrinthMods = 0;
        int sizeBukkitMods = 0;
        int sizeUnknownMods = 0;

        ExecutorService executorService;
        if (updaterConfig.mods_updater_async.asBoolean())
            executorService = Executors.newFixedThreadPool(includedSize);
        else
            executorService = Executors.newSingleThreadExecutor();
        List<Future<SearchResult>> activeFutures = new ArrayList<>();
        for (MinecraftMod pl :
                includedMods) {
            try {
                setStatus("Initialising update check for  " + pl.name + "...");
                if (pl.jenkinsProjectUrl != null) { // JENKINS PLUGIN
                    sizeJenkinsMods++;
                    activeFutures.add(executorService.submit(() -> new ModUpdateFinder().searchByJenkinsUrl(pl)));
                } else if (pl.githubRepoName != null) { // GITHUB PLUGIN
                    sizeGithubMods++;
                    activeFutures.add(executorService.submit(() -> new ModUpdateFinder().searchByGithubUrl(pl)));
                } else if (pl.modrinthId != null) {
                    sizemodrinthMods++; // modrinth
                    activeFutures.add(executorService.submit(() -> new ModUpdateFinder().searchBymodrinthId(pl)));
                } else if (pl.curseforgeId != null) {
                    sizeBukkitMods++; // BUKKIT PLUGIN
                    activeFutures.add(executorService.submit(() -> new ModUpdateFinder().searchByBukkitId(pl)));
                } else {
                    sizeUnknownMods++; // UNKNOWN PLUGIN
                    activeFutures.add(executorService.submit(() -> new ModUpdateFinder().unknownSearch(pl)));
                }
            } catch (Exception e) {
                this.getWarnings().add(new BetterWarning(this, e, "Critical error while searching for update for '" + pl.name + "' mod!"));
            }
        }

        List<SearchResult> updatablePremiummodrinthMods = new ArrayList<>();
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
                MinecraftMod pl = result.getMod();
                byte code = result.getResultCode();
                String type = result.getDownloadType(); // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
                String latest = result.getLatestVersion(); // The latest version as String
                String downloadUrl = result.getDownloadUrl(); // The download url for the latest version
                String resultmodrinthId = result.getSpigotId();
                String resultBukkitId = result.getBukkitId();
                this.setStatus("Checked '" + pl.name + "' mod (" + results.size() + "/" + includedSize + ")");
                if (code == 0 || code == 1) {

                    if (code == 1 && pl.isPremium())
                    else
                    doDownloadLogic(pl, result);

                } else if (code == 2)
                    if (result.getException() != null)
                        getWarnings().add(new BetterWarning(this, result.getException(), "There was an api-error for " + pl.name + "!"));
                    else
                        getWarnings().add(new BetterWarning(this, new Exception("There was an api-error for " + pl.name + "!")));
                else if (code == 3)
                    getWarnings().add(new BetterWarning(this, new Exception("Mod " + pl.name + " was not found by the search-algorithm! Specify an id in the mods config file.")));
                else
                    getWarnings().add(new BetterWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));

                try {
                    YamlSection mmodrinthId = modsConfig.get(modsConfigName, pl.name, "modrinth-id");
                    if (resultmodrinthId != null
                            && (mmodrinthId.asString() == null || mmodrinthId.asInt() == 0)) // Because we can get a "null" string from the server
                        mmodrinthId.setValues(resultmodrinthId);

                    YamlSection mBukkitId = modsConfig.get(modsConfigName, pl.name, "bukkit-id");
                    if (resultBukkitId != null
                            && (mmodrinthId.asString() == null || mmodrinthId.asInt() == 0)) // Because we can get a "null" string from the server
                        mBukkitId.setValues(resultBukkitId);

                    // The config gets saved at the end of the runAtStart method.
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e));
                }
            }
        }

        setStatus("Checking Premium mods...");
        for (SearchResult result :
                updatablePremiummodrinthMods) {
            if (result.isPremium()) {
                getWarnings().add(new BetterWarning(this,
                        result.getMod().name + " (" + result.getLatestVersion() + ") is a premium mod and thus not supported by the regular mod updater!"));
            }
        }
        modsConfig.save();

        // Wait until all download tasks have finished.
        while (!downloadTasksList.isEmpty()) {
            Thread.sleep(1000);
            TaskModDownload finishedDownloadTask = null;
            for (TaskModDownload task :
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
                    if (result.getMod().name.equals(finishedDownloadTask.getPlName())) {
                        matchingResult = result;
                        break;
                    }
                }
                if (matchingResult == null)
                    throw new Exception("This should not happen! Please report to the devs!");

                if (finishedDownloadTask.isDownloadSuccessful())
                    matchingResult.setResultCode((byte) 5);

                if (finishedDownloadTask.isInstallSuccessful()) {
                    matchingResult.setResultCode((byte) 6);
                    YamlSection jenkinsBuildId = modsConfig.get(
                            modsConfigName, finishedDownloadTask.getPlName(), "alternatives", "jenkins", "build-id");
                    jenkinsBuildId.setValues("" + finishedDownloadTask.searchResult.jenkinsId);
                    YamlSection version = modsConfig.get(
                            modsConfigName, finishedDownloadTask.getPlName(), "version");
                    version.setValues(finishedDownloadTask.searchResult.getLatestVersion());
                }

            }
        }

        if (new WebConfig().send_mods_updater_results.asBoolean()) {
            setStatus("Sending update check results to AutoPlug-Web...");
            try {
                new ConModsUpdateResult(results, excludedMods)
                        .open();
            } catch (Exception e) {
                addWarning(new BetterWarning(this, e));
            }
        }

        modsConfig.save();
        if (excludedMods.size() > 0) {
            includedSize += excludedMods.size();
            finish("Checked " + results.size() + "/" + includedSize + " mods. Some mods were excluded.");
        } else {
            finish("Checked " + results.size() + "/" + includedSize + " mods.");
        }

    }

    private void doDownloadLogic(@NotNull MinecraftMod pl, SearchResult result) {
        byte code = result.getResultCode();
        String type = result.getDownloadType(); // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
        String latest = result.getLatestVersion(); // The latest version as String
        String downloadUrl = result.getDownloadUrl(); // The download url for the latest version
        String resultmodrinthId = result.getmodrinthId();
        String resultBukkitId = result.getBukkitId();
        if (pl.getCustomDownloadURL() != null) downloadUrl = pl.getCustomDownloadURL();

        if (code == 0) {
            //getSummary().add("Mod " +pl.name+ " is already on the latest version (" + pl.getVersion() + ")"); // Only for testing right now
        } else {
            updatesAvailable++;

            try {
                // Update the in-memory config
                YamlSection mLatest = modsConfig.get(modsConfigName, pl.name, "latest-version");
                mLatest.setValues(latest); // Gets saved later
            } catch (Exception e) {
                getWarnings().add(new BetterWarning(this, e));
            }

            if (userProfile.equals(notifyProfile)) {
                addInfo("NOTIFY: Mod '" + pl.name + "' has an update available (" + pl.getVersion() + " -> " + latest + "). Download url: " + downloadUrl);
            } else {
                if (type.equals(".jar") || type.equals("external")) { // Note that "external" support is kind off random and strongly dependent on what modrinth devs are doing
                    if (!pl.isPremium()) {
                        if (userProfile.equals(manualProfile)) {
                            File cache_dest = new File(GD.WORKING_DIR + "/autoplug/downloads/" + pl.name + "[" + latest + "].jar");
                            TaskModDownload task = new TaskModDownload("ModDownloader", getManager(), pl.name, latest, downloadUrl, pl.getIgnoreContentType(), userProfile, cache_dest);
                            task.mod = pl;
                            task.searchResult = result;
                            downloadTasksList.add(task);
                            task.start();
                        } else {
                            File oldPl = new File(pl.installationPath);
                            File dest = new File(GD.WORKING_DIR + "/mods/" + pl.name + "-LATEST-" + "[" + latest + "]" + ".jar");
                            TaskModDownload task = new TaskModDownload("ModDownloader", getManager(), pl.name, latest, downloadUrl, pl.getIgnoreContentType(), userProfile, dest, oldPl);
                            task.mod = pl;
                            task.searchResult = result;
                            downloadTasksList.add(task);
                            task.start();
                        }
                    }
                } else
                    getWarnings().add(new BetterWarning(this, new Exception("Failed to download mod update(" + latest + ") for " + pl.name + " because of unsupported type: " + type)));
            }
        }

    }

    /**
     * Returns a list containing only mods, that contain all the information needed to perform a search. <br>
     * That means, that a mod must have its name, its authors name and its version in its internal config file.
     */
    @NotNull
    public List<MinecraftMod> getIncludedMods() {
        return includedMods;
    }

    @NotNull
    public List<MinecraftMod> getExcludedMods() {
        return excludedMods;
    }

    /**
     * Returns a list containing all mods found in the /mods directory. <br>
     */
    @NotNull
    public List<MinecraftMod> getAllMods() {
        return allMods;
    }

}
