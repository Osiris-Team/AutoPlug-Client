/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.SteamCMD;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.jlib.logger.AL;
import org.apache.commons.io.FileUtils;

import java.io.File;

public class TaskSteamWorkshopModDownload extends BThread implements ModDownloadTask {
    private final SteamWorkshopMod mod;
    private final String workshopAppId;
    private final String profile;
    private final SteamCMD steamCMD;
    private final SearchResult searchResult;
    private boolean isDownloadSuccessful;
    private boolean isInstallSuccessful;

    public TaskSteamWorkshopModDownload(String name, BThreadManager manager, SteamWorkshopMod mod,
                                        String workshopAppId, String profile, SteamCMD steamCMD,
                                        SearchResult searchResult) {
        super(name, manager);
        this.mod = mod;
        this.workshopAppId = workshopAppId;
        this.profile = profile;
        this.steamCMD = steamCMD;
        this.searchResult = searchResult;
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();

        if (profile.equals("NOTIFY")) {
            setStatus("Your profile doesn't allow downloads! Profile: " + profile);
            finish(false);
            return;
        }

        setStatus("Downloading Steam Workshop mod " + mod.getName() + "...");
        boolean isSuccess = steamCMD.installOrUpdateWorkshopItem(workshopAppId, mod.getPublishedId(),
                line -> {
                    AL.debug(this.getClass(), "SteamCMD-Out: " + line);
                    setStatus(line);
                }, errLine -> {
                    AL.debug(this.getClass(), "SteamCMD-Err-Out: " + errLine);
                    setStatus(errLine);
                    addWarning(errLine);
                });
        if (!isSuccess)
            throw new Exception("Failed to update Steam Workshop mod '" + mod.getName() + "' via SteamCMD.");

        isDownloadSuccessful = true;
        File downloadedDir = steamCMD.getWorkshopItemDir(workshopAppId, mod.getPublishedId());
        if (profile.equals("MANUAL")) {
            setStatus("Downloaded Steam Workshop mod " + mod.getName() + " to " + downloadedDir.getAbsolutePath());
            return;
        }

        setStatus("Installing Steam Workshop mod " + mod.getName() + "...");
        FileUtils.copyDirectory(downloadedDir, mod.getDirectory());
        isInstallSuccessful = true;
        setStatus("Installed update for " + mod.getName() + " successfully!");
    }

    public String getPlName() {
        return mod.getName();
    }

    public SearchResult getSearchResult() {
        return searchResult;
    }

    public boolean isDownloadSuccessful() {
        return isDownloadSuccessful;
    }

    public boolean isInstallSuccessful() {
        return isInstallSuccessful;
    }
}
