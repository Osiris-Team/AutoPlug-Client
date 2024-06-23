/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search.spigot;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.search.Version;

public class SpigotSearchById {

    public SearchResult search(MinecraftPlugin plugin) {
        int spigotId = plugin.getSpigotId();
        Exception exception = null;

        String url = "https://api.spiget.org/v2/resources/" + spigotId +
                "/versions?size=1&sort=-releaseDate";
        AL.debug(this.getClass(), "[" + plugin.getName() + "] Fetching latest release... (" + url + ")");
        String latest = null;
        String type = null;
        String downloadUrl = null;
        SearchResult.Type code = SearchResult.Type.UP_TO_DATE;
        boolean isPremium = false;
        try {
            // Get the latest version
            latest = Json.getAsJsonArray(url).get(0).getAsJsonObject().get("name").getAsString();
            if (latest != null)
                latest = latest.replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots

            // Get the file type and downloadUrl
            String url1 = "https://api.spiget.org/v2/resources/" + spigotId;
            AL.debug(this.getClass(), "[" + plugin.getName() + "] Fetching resource details... (" + url1 + ")");
            JsonObject json = Json.getAsObject(url1).getAsJsonObject("file");
            isPremium = Boolean.parseBoolean(Json.getAsObject(url1).get("premium").getAsString());
            type = json.get("type").getAsString();
            downloadUrl = "https://www.spigotmc.org/" + json.get("url").getAsString();

            // If not external download over the spiget api
            downloadUrl = "https://api.spiget.org/v2/resources/" + spigotId + "/download";

            if (Version.isLatestBigger(plugin.getVersion(), latest == null ? "0" : latest))
                code = SearchResult.Type.UPDATE_AVAILABLE;
        } catch (Exception e) {
            exception = e;
            code = SearchResult.Type.API_ERROR;
        }

        AL.debug(this.getClass(), "[" + plugin.getName() + "] Finished check with results: code:" + code + " latest:" + latest + " downloadURL:" + downloadUrl + " type:" + type + " ");
        SearchResult result = new SearchResult(plugin, code, latest, downloadUrl, type, String.valueOf(spigotId), null, isPremium);
        result.setException(exception);
        return result;
    }
}
