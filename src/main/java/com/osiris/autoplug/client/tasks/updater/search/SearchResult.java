/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains all the relevant information
 * after finishing a search.
 */
public class SearchResult {

    public static boolean isMatchFound(SearchResult sr) {
        return sr != null && sr.type != Type.API_ERROR && sr.type != Type.RESOURCE_NOT_FOUND;
    }

    // TODO remove getters and setters and make everything public for easier future coding
    public MinecraftPlugin plugin;
    public List<MinecraftPlugin> similarPlugins = new ArrayList<>();
    public MinecraftMod mod;
    public String latestVersion;
    public String downloadUrl;
    public String downloadType;
    public String spigotId;
    public String bukkitId;
    public int jenkinsId;
    public boolean isPremium;
    public Type type;
    public Exception exception;
    public String fileName;

    /**
     * @param type    All codes: <br>
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
    public SearchResult(MinecraftPlugin plugin, Type type, String latestVersion, String downloadUrl,
                        String downloadType, String spigotId, String bukkitId, boolean isPremium) {
        this.plugin = plugin;
        this.type = type;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
        this.downloadType = downloadType;
        this.spigotId = spigotId;
        this.bukkitId = bukkitId;
        this.isPremium = isPremium;
    }

    public boolean isUpdateAvailable() {
        return type == SearchResult.Type.UPDATE_AVAILABLE;
    }

    public boolean isError() {
        return type == Type.API_ERROR;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public MinecraftPlugin getPlugin() {
        return plugin;
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

    /*
     * @param resultCode    All codes: <br>
     *                      0 = already on latest version. <br>
     *                      1 = update available. <br>
     *                      2 = api error. <br>
     *                      3 = plugin not found (author not found). <br>
     *                      4 = plugin was excluded. <br>
     *                      5 = plugin update was downloaded. <br>
     *                      6 = plugin update was installed. <br>
     */
    public enum Type {
        UP_TO_DATE((byte) 0),
        UPDATE_AVAILABLE((byte) 1),
        API_ERROR((byte) 2),
        RESOURCE_NOT_FOUND((byte) 3),
        RESOURCE_EXCLUDED((byte) 4),
        UPDATE_DOWNLOADED((byte) 5),
        UPDATE_INSTALLED((byte) 6);


        public byte id = 0;

        Type(byte id) {
            this.id = id;
        }
    }
}
