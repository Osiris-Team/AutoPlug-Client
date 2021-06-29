/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins.search;

import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;

/**
 * Contains all the relevant information
 * after finishing a search.
 */
public class SearchResult {
    private final DetailedPlugin plugin;
    private final String latestVersion;
    private final String downloadUrl;
    private final String downloadType;
    private final String spigotId;
    private final String bukkitId;
    private byte resultCode;
    private Exception exception;

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
    public SearchResult(DetailedPlugin plugin, byte resultCode, String latestVersion, String downloadUrl,
                        String downloadType, String spigotId, String bukkitId) {
        this.plugin = plugin;
        this.resultCode = resultCode;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
        this.downloadType = downloadType;
        this.spigotId = spigotId;
        this.bukkitId = bukkitId;
    }

    public DetailedPlugin getPlugin() {
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
