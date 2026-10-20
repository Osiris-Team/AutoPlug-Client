/*
 * Copyright (c) 2021-2024 Osiris-Team.
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

    /** Select a declared-compatible target release, including older game versions, without guessing from latest. */
    public SearchResult searchCompatible(MinecraftPlugin plugin, String gameVersion) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL("https://api.curseforge.com/servermods/files?projectIds=" + plugin.getBukkitId()).openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setRequestProperty("User-Agent", "AutoPlug-Client/10.2.0");
            JsonArray versions;
            try {
                if (connection.getResponseCode() != 200) throw new java.io.IOException("Bukkit API returned HTTP " + connection.getResponseCode());
                try (java.io.Reader reader = new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) { versions = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray(); }
            } finally { connection.disconnect(); }
            JsonObject selected = null;
            for (com.google.gson.JsonElement value : versions) {
                JsonObject release = value.getAsJsonObject();
                if (!release.has("gameVersion") || release.get("gameVersion").isJsonNull()) continue;
                Matcher game = Pattern.compile("([0-9]+(?:\\.[0-9]+){1,2})").matcher(release.get("gameVersion").getAsString());
                if (!game.find()) continue;
                String declared = game.group(1);
                if (declared.equals(gameVersion) || gameVersion.startsWith(declared + ".")) selected = release;
            }
            if (selected == null) return new SearchResult(plugin, SearchResult.Type.RESOURCE_NOT_FOUND, null, null, null, null, String.valueOf(plugin.getBukkitId()), false);
            SearchResult result = new SearchResult(plugin, SearchResult.Type.UPDATE_AVAILABLE, selected.get("name").getAsString(), selected.get("downloadUrl").getAsString(), ".jar", null, String.valueOf(plugin.getBukkitId()), false);
            result.fileName = selected.get("fileName").getAsString();
            if (selected.has("md5") && !selected.get("md5").isJsonNull()) result.expectedMD5 = selected.get("md5").getAsString();
            if (!result.fileName.endsWith(".jar")) throw new java.io.IOException("Compatible Bukkit release is not a jar");
            return result;
        } catch (Exception exception) {
            SearchResult result = new SearchResult(plugin, SearchResult.Type.API_ERROR, null, null, null, null, String.valueOf(plugin.getBukkitId()), false); result.exception = exception; return result;
        }
    }

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
        SearchResult.Type code = SearchResult.Type.UP_TO_DATE;
        try {
            versions = Json.getAsJsonArray(url);
            json = versions.get(versions.size() - 1).getAsJsonObject();
            latest = json.get("name").getAsString();
            if (latest != null)
                latest = latest.replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots

            if(latest == null) latest = "";
            if (Version.isLatestBigger(plugin.getVersion(), latest))
                code = SearchResult.Type.UPDATE_AVAILABLE;

            downloadUrl = json.get("downloadUrl").getAsString();
            Matcher m = Pattern.compile("[.][^.]+$")
                    .matcher(json.get("fileName").getAsString());
            if (m.find()) {
                downloadType = m.group(0);
            } else
                throw new Exception("[" + plugin.getName() + "] Couldn't find a downloadType in fileName: " + json.get("fileName").getAsString());
        } catch (Exception e) {
            exception = e;
            code = SearchResult.Type.API_ERROR;
        }

        AL.debug(this.getClass(), "[" + plugin.getName() + "] Finished check with results: code:" + code + " latest:" + latest + " downloadURL:" + downloadUrl + " type:" + downloadType + " ");
        SearchResult result = new SearchResult(plugin, code, latest, downloadUrl, downloadType, null, String.valueOf(bukkitId), false);
        result.setException(exception);
        return result;
    }

}
