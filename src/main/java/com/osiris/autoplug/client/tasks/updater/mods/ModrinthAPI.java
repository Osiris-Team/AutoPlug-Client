/*
 * Copyright (c) 2022-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.time.Instant;


public class ModrinthAPI {
    private final String baseUrl = "https://api.modrinth.com/v2";

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Requires a modrithId (chars or number), or curseforgeId (no number, but chars).
     * If the id contains chars its usually the mods slugs.
     */
    public SearchResult searchUpdateMod(InstalledModLoader modLoader, MinecraftMod mod, String mcVersion) {
        if (mod.modrinthId == null && !isInt(mod.curseforgeId)) mod.modrinthId = mod.curseforgeId; // Slug
        SearchResult res = searchUpdate((modLoader.isFabric || modLoader.isQuilt ? "fabric" : "forge"),mod.modrinthId,mcVersion, mod.installationPath, mod.forceLatest);
        res.mod = mod;
        return res;
    }
    public SearchResult searchUpdatePlugin(MinecraftPlugin plugin, String mcVersion) { //TODO: probably don't hardcode spigot and papermc
        return searchUpdate("spigot\",\"paper", plugin.getModrinthId(), mcVersion, plugin.getInstallationPath(), false);
    }
    private SearchResult searchUpdate(String loader, String id, String mcVersion, String installPath, boolean forceLatest) {

        String url = baseUrl + "/project/" + id + "/version?loaders=[\"" + loader + "\"]&game_versions=[\"" + mcVersion + "\"]";
        url = new UtilsURL().clean(url);
        Exception exception = null;
        String latest = null;
        String type = ".jar";
        String downloadUrl = null;
        SearchResult.Type resultType = SearchResult.Type.UP_TO_DATE;
        try {
            if (id == null)
                throw new Exception("Modrinth-id is null!"); // Modrinth id can be slug or actual id

            AL.debug(this.getClass(), url);
            JsonObject release;
            try {
                release = Json.getAsJsonArray(url)
                        .get(0).getAsJsonObject();
            } catch (Exception e) {
                if (!isInt(id)) { // Try another url, with slug replaced _ with -
                    url = baseUrl + "/project/" + id.replace("_", "-")
                            + "/version?loaders=[\"" +
                            loader + "\"]" + (forceLatest ? "" : "&game_versions=[\"" + mcVersion + "\"]");
                    AL.debug(this.getClass(), url);
                    release = Json.getAsJsonArray(url)
                            .get(0).getAsJsonObject();
                } else
                    throw e;
            }

            latest = release.get("version_number").getAsString().replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots
            if (new File(installPath).lastModified() < Instant.parse(release.get("date_published").getAsString()).toEpochMilli())
                resultType = SearchResult.Type.UPDATE_AVAILABLE;
            JsonObject releaseDownload = release.getAsJsonArray("files").get(0).getAsJsonObject();
            downloadUrl = releaseDownload.get("url").getAsString();
            try {
                String fileName = releaseDownload.get("filename").getAsString();
                type = fileName.substring(fileName.lastIndexOf("."));
            } catch (Exception e) {
            }
        } catch (Exception e) {
            exception = e;
            resultType = SearchResult.Type.API_ERROR;
        }
        SearchResult result = new SearchResult(null, resultType, latest, downloadUrl, type, null, null, false);
        result.setException(exception);
        return result;
    }
}
