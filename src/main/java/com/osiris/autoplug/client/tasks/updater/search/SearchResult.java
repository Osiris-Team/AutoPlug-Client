/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;

/**
 * Contains all the relevant information
 * after finishing a search.
 */
public class SearchResult {
    // TODO remove getters and setters and make everything public for easier future coding
    public MinecraftPlugin plugin;
    public MinecraftMod mod;
    public String latestVersion;
    public String downloadUrl;
    public String downloadType;
    public String spigotId;
    public String bukkitId;
    public int jenkinsId;
    public boolean isPremium;
    public byte resultCode;
    public Exception exception;
    public String fileName;

    /**
     * @param resultCode    All codes: <br>
     *                      0 = already on latest version. <br>
     *                      1 = update available. <br>
     *                      2 = api error. <br>
     *                      3 = plugin not found (author not found). <br>
     *                      4 = plugin was excluded. <br>
     *                      5 = plugin update was downloaded. <br>
     *                      6 = plugin update was installed. <br>
     * @param latestVersion
     * @param downloadUrl
     * @param downloadType
     * @param spigotId      only !=null after an algorithm search where no id(spigot/bukkit) was provided.
     * @param bukkitId      only !=null after an algorithm search where no id(spigot/bukkit) was provided.
     */
    public SearchResult(MinecraftPlugin plugin, byte resultCode, String latestVersion, String downloadUrl,
                        String downloadType, String spigotId, String bukkitId, boolean isPremium) {
        this.plugin = plugin;
        this.resultCode = resultCode;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
        this.downloadType = downloadType;
        this.spigotId = spigotId;
        this.bukkitId = bukkitId;
        this.isPremium = isPremium;
    }

    public boolean isUpdateAvailable() {
        return resultCode == 1;
    }

    public boolean isError() {
        return resultCode == 2;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public MinecraftPlugin getPlugin() {
        return plugin;
    }

    public byte getResultCode() {
        return resultCode;
    }

    public void setResultCode(byte resultCode) {
        this.resultCode = resultCode;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getDownloadType() {
        return downloadType;
    }

    public String getSpigotId() {
        return spigotId;
    }

    public String getBukkitId() {
        return bukkitId;
    }
}
