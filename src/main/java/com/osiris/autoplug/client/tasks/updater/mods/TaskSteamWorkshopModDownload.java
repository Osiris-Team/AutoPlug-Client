/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.SteamCMD;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
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

        boolean success = steamCMD.installOrUpdateWorkshopItem(workshopAppId, mod.getPublishedId(), this::setStatus, this::addWarning);
        if (!success) {
            setStatus("Failed to download Steam Workshop mod " + mod.getName() + ".");
            finish(false);
            return;
        }
        isDownloadSuccessful = true;

        File downloadedDir = steamCMD.getWorkshopItemDir(workshopAppId, mod.getPublishedId());
        if (!downloadedDir.exists())
            throw new Exception("SteamCMD reported success, but the Workshop item directory does not exist: " + downloadedDir);

        File finalDest;
        if (profile.equals("MANUAL")) {
            finalDest = new File(GD.DOWNLOADS_DIR + "/steam-workshop/" + workshopAppId + "/" + mod.getPublishedId());
        } else {
            finalDest = mod.getDirectory();
        }

        if (finalDest.exists()) FileUtils.deleteDirectory(finalDest);
        FileUtils.copyDirectory(downloadedDir, finalDest);
        if (profile.equals("MANUAL")) {
            setStatus("Downloaded Steam Workshop update for " + mod.getName() + " successfully!");
            return;
        }

        isInstallSuccessful = true;
        setStatus("Installed Steam Workshop update for " + mod.getName() + " successfully!");
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
