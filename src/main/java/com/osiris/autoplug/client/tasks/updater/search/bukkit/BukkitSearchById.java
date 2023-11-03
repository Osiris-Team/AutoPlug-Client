/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search.bukkit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.search.Version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BukkitSearchById {

    public SearchResult search(MinecraftPlugin plugin) {
        AL.debug(this.getClass(), "[" + plugin.getName() + "] Performing bukkit search by id");
        int bukkitId = plugin.getBukkitId();

        String url = "https://api.curseforge.com/servermods/files?projectIds=" + bukkitId;
        AL.debug(this.getClass(), "[" + plugin.getName() + "] Fetching latest release... (" + url + ")");

        Exception exception = null;
        JsonArray versions = null;
        JsonObject json = null;
        String latest = null;
        String downloadUrl = null;
        String downloadType = "unknown";
        byte code = 0;
        try {
            versions = Json.getAsJsonArray(url);
            json = versions.get(versions.size() - 1).getAsJsonObject();
            latest = json.get("name").getAsString();
            if (latest != null)
                latest = latest.replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots
            downloadUrl = json.get("downloadUrl").getAsString();
            Matcher m = Pattern.compile("[.][^.]+$")
                    .matcher(json.get("fileName").getAsString());
            if (m.find()) {
                downloadType = m.group(0);
            } else
                throw new Exception("[" + plugin.getName() + "] Couldn't find a downloadType in fileName: " + json.get("fileName").getAsString());
        } catch (Exception e) {
            exception = e;
            code = 2;
        }

        if (Version.isLatestBigger(plugin.getVersion(), latest == null ? "0" : latest)) code = 1;

        AL.debug(this.getClass(), "[" + plugin.getName() + "] Finished check with results: code:" + code + " latest:" + latest + " downloadURL:" + downloadUrl + " type:" + downloadType + " ");
        SearchResult result = new SearchResult(plugin, code, latest, downloadUrl, downloadType, null, String.valueOf(bukkitId), false);
        result.setException(exception);
        return result;
    }

}
