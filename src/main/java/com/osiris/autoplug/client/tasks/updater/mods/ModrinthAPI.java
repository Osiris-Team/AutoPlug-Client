/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.autoplug.client.utils.UtilsVersion;
import com.osiris.autoplug.core.json.Json;
import com.osiris.autoplug.core.logger.AL;


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
    public SearchResult searchUpdate(MinecraftMod mod, String mcVersion) {
        if (mod.modrinthId == null && !isInt(mod.curseforgeId)) mod.modrinthId = mod.curseforgeId; // Slug
        String url = baseUrl + "/project/" + mod.modrinthId + "/version?loaders=[\"" +
                (Server.isFabric ? "fabric" : "forge") + "\"]&game_versions=[\"" + mcVersion + "\"]";
        url = new UtilsURL().clean(url);
        Exception exception = null;
        String latest = null;
        String type = ".jar";
        String downloadUrl = null;
        byte code = 0;
        try {
            if (mod.modrinthId == null)
                throw new Exception("Modrinth-id is null!"); // Modrinth id can be slug or actual id

            AL.debug(this.getClass(), url);
            JsonObject release;
            try {
                release = Json.fromUrlAsJsonArray(url)
                        .get(0).getAsJsonObject();
            } catch (Exception e) {
                if (!isInt(mod.modrinthId)) { // Try another url, with slug replaced _ with -
                    url = baseUrl + "/project/" + mod.modrinthId.replace("_", "-")
                            + "/version?loaders=[\"" +
                            (Server.isFabric ? "fabric" : "forge") + "\"]&game_versions=[\"" + mcVersion + "\"]";
                    AL.debug(this.getClass(), url);
                    release = Json.fromUrlAsJsonArray(url)
                            .get(0).getAsJsonObject();
                } else
                    throw e;
            }

            latest = release.get("version_number").getAsString().replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots
            if (new UtilsVersion().compare(mod.getVersion(), latest))
                code = 1;
            JsonObject releaseDownload = release.getAsJsonArray("files").get(0).getAsJsonObject();
            downloadUrl = releaseDownload.get("url").getAsString();
            try {
                String fileName = releaseDownload.get("filename").getAsString();
                type = fileName.substring(fileName.lastIndexOf("."));
            } catch (Exception e) {
            }
        } catch (Exception e) {
            exception = e;
            code = 2;
        }
        SearchResult result = new SearchResult(null, code, latest, downloadUrl, type, null, null, false);
        result.mod = mod;
        result.setException(exception);
        return result;
    }
}
