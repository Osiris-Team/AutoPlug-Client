/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.ModsConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsFile;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BWarning;
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

public class TaskModsUpdater extends BThread {
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
    ModsConfig modsConfig;
    private UpdaterConfig updaterConfig;
    private String userProfile;
    private String modsConfigName;
    private Socket online_socket;
    private DataInputStream online_dis;
    private DataOutputStream online_dos;
    private int updatesAvailable = 0;

    public TaskModsUpdater(String name, BThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        updaterConfig = new UpdaterConfig();
        if (!updaterConfig.mods_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform mods update while server is running!");

        modsConfig = new ModsConfig();
        modsConfig.load(); // No lock needed, since there are no other threads that access this file
        String name = modsConfig.getFileNameWithoutExt();


        // First we get the latest mod details from the yml config.
        // The minimum required information is:
        // name, version, and author. Otherwise they won't get update-checked by AutoPlug (and are not inside the list below).
        setStatus("Fetching latest mod data...");

        userProfile = updaterConfig.mods_updater_profile.asString();
        this.allMods.addAll(new UtilsMinecraft().getMods(FileManager.convertRelativeToAbsolutePath(updaterConfig.mods_updater_path.asString())));

        for (MinecraftMod installedMod :
                allMods) {
            try {
                final String plName = installedMod.getName();
                if (installedMod.getName() == null || installedMod.getName().isEmpty())
                    throw new Exception("The mods name couldn't be determined for '" + installedMod.installationPath + "'!");

                YamlSection exclude = modsConfig.put(name, plName, "exclude").setDefValues("false");
                YamlSection version = modsConfig.put(name, plName, "version").setDefValues(installedMod.getVersion());
                YamlSection latestVersion = modsConfig.put(name, plName, "latest-version");
                YamlSection author = modsConfig.put(name, plName, "author").setDefValues(installedMod.getAuthor());
                YamlSection modrinthId = modsConfig.put(name, plName, "modrinth-id");
                YamlSection curseforgeId = modsConfig.put(name, plName, "curseforge-id");
                YamlSection ignoreContentType = modsConfig.put(name, plName, "ignore-content-type").setDefValues("false");
                YamlSection forceLatest = modsConfig.put(name, plName, "force-latest").setDefValues("false");
                YamlSection customDownloadURL = modsConfig.put(name, plName, "custom-download-url");
                YamlSection githubRepoUrl = modsConfig.put(name, plName, "alternatives", "github", "repo-name");
                YamlSection githubAssetName = modsConfig.put(name, plName, "alternatives", "github", "asset-name");
                YamlSection jenkinsProjectUrl = modsConfig.put(name, plName, "alternatives", "jenkins", "project-url");
                YamlSection jenkinsArtifactName = modsConfig.put(name, plName, "alternatives", "jenkins", "artifact-name");
                YamlSection jenkinsBuildId = modsConfig.put(name, plName, "alternatives", "jenkins", "build-id").setDefValues("0");

                if (updaterConfig.mods_update_update_id_from_jar.asBoolean()) {
                    if (installedMod.modrinthId != null) modrinthId.setValues(installedMod.modrinthId);
                    if (installedMod.curseforgeId != null) curseforgeId.setValues(installedMod.curseforgeId);
                }

                // Update the detailed mods in-memory values
                installedMod.modrinthId = modrinthId.asString();
                installedMod.curseforgeId = (curseforgeId.asString());
                installedMod.ignoreContentType = (ignoreContentType.asBoolean());
                installedMod.forceLatest = (forceLatest.asBoolean());
                installedMod.customDownloadURL = (customDownloadURL.asString());
                installedMod.githubRepoName = (githubRepoUrl.asString());
                installedMod.githubAssetName = (githubAssetName.asString());
                installedMod.jenkinsProjectUrl = (jenkinsProjectUrl.asString());
                installedMod.jenkinsArtifactName = (jenkinsArtifactName.asString());
                installedMod.jenkinsBuildId = (jenkinsBuildId.asInt());

                // Check for missing author in internal config
                if ((installedMod.getVersion() == null)
                        && (modrinthId.asString() == null)
                        && (curseforgeId.asString() == null)) {
                    exclude.setValues("true");
                    this.addWarning("Mod " + installedMod.getName() + " is missing 'version' in its internal config file and was excluded.");
                }

                // Check for missing version in internal config
                if ((installedMod.getAuthor() == null)
                        && (modrinthId.asString() == null)
                        && (curseforgeId.asString() == null)
                        && jenkinsArtifactName.asString() == null
                        && githubAssetName.asString() == null) {
                    exclude.setValues("true");
                    this.addWarning("Mod " + installedMod.getName() + " is missing 'author' or 'authors' in its internal config file and was excluded.");
                }

                if (exclude.asBoolean())
                    excludedMods.add(installedMod);
                else
                    includedMods.add(installedMod);
            } catch (DuplicateKeyException e) {
                addWarning(new BWarning(this, e, "Duplicate mod '" + installedMod.getName() + "' (or mod name from its internal config) found in your mods directory. " +
                        "Its recommended to remove it."));
            } catch (Exception e) {
                addWarning(new BWarning(this, e));
            }
        }

        if (modsConfig.keep_removed.asBoolean())
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


        String mcVersion = updaterConfig.mods_updater_version.asString();
        if (mcVersion == null) mcVersion = Server.getMCVersion();

        ExecutorService executorService;
        if (updaterConfig.mods_updater_async.asBoolean())
            executorService = Executors.newFixedThreadPool(includedSize);
        else
            executorService = Executors.newSingleThreadExecutor();
        InstalledModLoader modLoader = new InstalledModLoader();
        List<Future<SearchResult>> activeFutures = new ArrayList<>();
        for (MinecraftMod mod :
                includedMods) {
            try {
                setStatus("Initialising update check for  " + mod.getName() + "...");
                if (mod.jenkinsProjectUrl != null) { // JENKINS MOD
                    sizeJenkinsMods++;
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findByJenkinsUrl(mod)));
                } else if (mod.githubRepoName != null) { // GITHUB MOD
                    sizeGithubMods++;
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findByGithubUrl(mod)));
                } else {
                    sizeUnknownMods++; // MODRINTH OR CURSEFORGE MOD
                    mod.ignoreContentType = true; // TODO temporary workaround for xamazon-json content type curseforge/bukkit issue: https://github.com/Osiris-Team/AutoPlug-Client/issues/109
                    String finalMcVersion = mcVersion;
                    activeFutures.add(executorService.submit(() -> new ResourceFinder().findByModrinthOrCurseforge(modLoader, mod, finalMcVersion, updaterConfig.mods_update_check_name_for_mod_loader.asBoolean())));
                }
            } catch (Exception e) {
                this.getWarnings().add(new BWarning(this, e, "Critical error while searching for update for '" + mod.getName() + "' mod!"));
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
                MinecraftMod mod = result.mod;
                byte code = result.getResultCode();
                String type = result.getDownloadType(); // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
                String latest = result.getLatestVersion(); // The latest version as String
                String downloadUrl = result.getDownloadUrl(); // The download url for the latest version
                String resultmodrinthId = result.getSpigotId();
                String resultBukkitId = result.getBukkitId();
                this.setStatus("Checked '" + mod.getName() + "' mod (" + results.size() + "/" + includedSize + ")");
                if (code == 0 || code == 1) {
                    doDownloadLogic(mod, result);
                } else if (code == 2)
                    if (result.getException() != null)
                        getWarnings().add(new BWarning(this, result.getException(), "There was an api-error for " + mod.getName() + "!"));
                    else
                        getWarnings().add(new BWarning(this, new Exception("There was an api-error for " + mod.getName() + "!")));
                else if (code == 3)
                    getWarnings().add(new BWarning(this, new Exception("Mod " + mod.getName() + " was not found by the search-algorithm! Specify an id in /autoplug/mods.yml file.")));
                else
                    getWarnings().add(new BWarning(this, new Exception("Unknown error occurred! Code: " + code + "."), "Notify the developers. Fastest way is through discord (https://discord.gg/GGNmtCC)."));

                try {
                    YamlSection mmodrinthId = modsConfig.get(modsConfigName, mod.getName(), "modrinth-id");
                    if (resultmodrinthId != null
                            && (mmodrinthId.asString() == null || mmodrinthId.asInt() == 0)) // Because we can get a "null" string from the server
                        mmodrinthId.setValues(resultmodrinthId);

                    YamlSection mBukkitId = modsConfig.get(modsConfigName, mod.getName(), "bukkit-id");
                    if (resultBukkitId != null
                            && (mmodrinthId.asString() == null || mmodrinthId.asInt() == 0)) // Because we can get a "null" string from the server
                        mBukkitId.setValues(resultBukkitId);

                    // The config gets saved at the end of the runAtStart method.
                } catch (Exception e) {
                    getWarnings().add(new BWarning(this, e));
                }
            }
        }

        // Wait until all download tasks have finished.
        while (!downloadTasksList.isEmpty()) {
            Thread.sleep(1000);
            TaskModDownload download = null;
            for (TaskModDownload task :
                    downloadTasksList) {
                if (!task.isAlive()) {
                    download = task;
                    break;
                }
            }

            if (download != null) {
                downloadTasksList.remove(download);
                SearchResult matchingResult = null;
                for (SearchResult result :
                        results) {
                    if (result.mod.getName().equals(download.getPlName())) {
                        matchingResult = result;
                        break;
                    }
                }
                if (matchingResult == null)
                    throw new Exception("This should not happen! Please report to the devs!");

                if (updaterConfig.mods_update_update_id_from_jar.asBoolean()) {
                    if (download.mod.modrinthId != null)
                        modsConfig.put(modsConfigName, download.mod.getName(), "modrinth-id").setValues(download.mod.modrinthId);
                    if (download.mod.curseforgeId != null)
                        modsConfig.put(modsConfigName, download.mod.getName(), "curseforge-id").setValues(download.mod.curseforgeId);
                }

                if (download.isDownloadSuccessful())
                    matchingResult.setResultCode((byte) 5);

                if (download.isInstallSuccessful()) {
                    matchingResult.setResultCode((byte) 6);
                    YamlSection jenkinsBuildId = modsConfig.get(
                            modsConfigName, download.getPlName(), "alternatives", "jenkins", "build-id");
                    jenkinsBuildId.setValues(String.valueOf(download.searchResult.jenkinsId));
                    YamlSection version = modsConfig.get(
                            modsConfigName, download.getPlName(), "version");
                    version.setValues(download.searchResult.getLatestVersion());
                }

            }
        }

        modsConfig.save();

        /* // TODO
        if (new WebConfig().send_mods_updater_results.asBoolean()) {
            setStatus("Sending update check results to AutoPlug-Web...");
            try {
                new ConModsUpdateResult(results, excludedMods)
                        .open();
            } catch (Exception e) {
                addWarning(new BWarning(this, e));
            }
        }
         */

        if (excludedMods.size() > 0) {
            includedSize += excludedMods.size();
            finish("Checked " + results.size() + "/" + includedSize + " mods. Some mods were excluded.");
        } else {
            finish("Checked " + results.size() + "/" + includedSize + " mods.");
        }

    }

    private void doDownloadLogic(@NotNull MinecraftMod mod, SearchResult result) {
        byte code = result.getResultCode();
        String type = result.getDownloadType(); // The file type to download (Note: When 'external' is returned nothing will be downloaded. Working on a fix for this!)
        String latest = result.getLatestVersion(); // The latest version as String
        String downloadUrl = result.getDownloadUrl(); // The download url for the latest version
        if (mod.customDownloadURL != null) downloadUrl = mod.customDownloadURL;

        if (code == 0) {
            //getSummary().add("Mod " +pl.getName()+ " is already on the latest version (" + pl.getVersion() + ")"); // Only for testing right now
        } else {
            updatesAvailable++;

            try {
                // Update the in-memory config
                YamlSection mLatest = modsConfig.get(modsConfigName, mod.getName(), "latest-version");
                mLatest.setValues(latest); // Gets saved later
            } catch (Exception e) {
                getWarnings().add(new BWarning(this, e));
            }

            if (userProfile.equals(notifyProfile)) {
                addInfo("NOTIFY: Mod '" + mod.getName() + "' has an update available (" + mod.getVersion() + " -> " + latest + "). Download url: " + downloadUrl);
            } else {
                // Make sure that plName and plLatestVersion do not contain any slashes (/ or \) that could break the file name
                UtilsFile utilsFile = new UtilsFile();
                mod.setName(utilsFile.getValidFileName(mod.getName()));
                latest = utilsFile.getValidFileName(latest);
                if (type.equals(".jar") || type.equals("external")) { // Note that "external" support is kind off random and strongly dependent on what modrinth devs are doing
                    if (userProfile.equals(manualProfile)) {
                        File cache_dest = new File(GD.WORKING_DIR + "/autoplug/downloads/" + mod.getName() + "[" + latest + "].jar");
                        TaskModDownload task = new TaskModDownload("ModDownloader", getManager(), mod.getName(),
                                latest, downloadUrl, mod.ignoreContentType, userProfile, cache_dest);
                        task.mod = mod;
                        task.searchResult = result;
                        downloadTasksList.add(task);
                        task.start();
                    } else {
                        File oldPl = new File(mod.installationPath);
                        File dest = new File(GD.WORKING_DIR + "/mods/" + mod.getName() + "-LATEST-" + "[" + latest + "]" + ".jar");
                        TaskModDownload task = new TaskModDownload("ModDownloader", getManager(), mod.getName(),
                                latest, downloadUrl, mod.ignoreContentType, userProfile, dest, oldPl);
                        task.mod = mod;
                        task.searchResult = result;
                        downloadTasksList.add(task);
                        task.start();
                    }
                } else
                    getWarnings().add(new BWarning(this, new Exception("Failed to download mod update(" + latest + ") for " + mod.getName() + " because of unsupported type: " + type)));
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
