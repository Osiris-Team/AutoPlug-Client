/*
 * Copyright (c) 2022-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.time.Instant;
import java.util.List;


public class ModrinthAPI {
    private final String baseUrl = "https://api.modrinth.com/v2";

    /** Injectable transport keeps profile compatibility selection testable without a network. */
    public interface ReleaseLoader { JsonArray load(String url) throws Exception; }
    private final ReleaseLoader profileReleases;

    public ModrinthAPI() { this(ModrinthAPI::loadProfileReleases); }
    public ModrinthAPI(ReleaseLoader loader) { profileReleases = java.util.Objects.requireNonNull(loader); }

    /** Strict target-version selection, independent of jar timestamps and force-latest. */
    public SearchResult searchCompatible(List<String> loaders, String id, String mcVersion) {
        SearchResult result = new SearchResult(null, SearchResult.Type.API_ERROR, null, null, ".jar", null, null, false);
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            result.type = SearchResult.Type.RESOURCE_NOT_FOUND;
            return result;
        }
        try {
            String query = "[\"" + String.join("\",\"", loaders) + "\"]";
            String url = baseUrl + "/project/" + id + "/version?loaders="
                    + java.net.URLEncoder.encode(query, "UTF-8") + "&game_versions="
                    + java.net.URLEncoder.encode("[\"" + mcVersion + "\"]", "UTF-8");
            JsonArray releases = profileReleases.load(url);
            for (JsonElement element : releases) {
                JsonObject release = element.getAsJsonObject();
                if (!contains(release.getAsJsonArray("game_versions"), List.of(mcVersion))
                        || !contains(release.getAsJsonArray("loaders"), loaders)) continue;
                JsonArray files = release.getAsJsonArray("files");
                JsonObject selected = null;
                for (JsonElement fileElement : files) {
                    JsonObject file = fileElement.getAsJsonObject();
                    if (!file.get("filename").getAsString().endsWith(".jar")) continue;
                    if (selected == null) selected = file;
                    if (file.has("primary") && file.get("primary").getAsBoolean()) { selected = file; break; }
                }
                if (selected == null) throw new java.io.IOException("Compatible release has no jar artifact");
                result.type = SearchResult.Type.UPDATE_AVAILABLE;
                result.latestVersion = release.get("version_number").getAsString();
                result.downloadUrl = selected.get("url").getAsString();
                result.fileName = selected.get("filename").getAsString();
                JsonObject hashes = selected.getAsJsonObject("hashes");
                if (hashes != null && hashes.has("sha256")) result.sha256 = hashes.get("sha256").getAsString();
                return result;
            }
            result.type = SearchResult.Type.NO_COMPATIBLE_RELEASE;
        } catch (Exception error) {
            result.exception = error;
            result.type = SearchResult.Type.API_ERROR;
        }
        return result;
    }

    private static boolean contains(JsonArray values, List<String> expected) {
        if (values == null) return false;
        for (JsonElement value : values) if (expected.contains(value.getAsString())) return true;
        return false;
    }

    private static JsonArray loadProfileReleases(String url) throws Exception {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder().followSslRedirects(false)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS).build();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).header("User-Agent", "AutoPlug-Client").build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("Modrinth HTTP " + response.code());
            return com.google.gson.JsonParser.parseString(response.body().string()).getAsJsonArray();
        }
    }

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
        SearchResult res = searchUpdate((modLoader.isFabric || modLoader.isQuilt ? List.of("fabric") : List.of("forge")),mod.modrinthId,mcVersion, mod.installationPath, mod.forceLatest);
        res.mod = mod;
        return res;
    }
    public SearchResult searchUpdatePlugin(List<String> loaders, MinecraftPlugin plugin, String mcVersion) {
        return searchUpdate(loaders, plugin.getModrinthId(), mcVersion, plugin.getInstallationPath(), false);
    }
    private SearchResult searchUpdate(List<String> loaders, String id, String mcVersion, String installPath, boolean forceLatest) {
        String url = baseUrl + "/project/" + id + "/version?loaders=[\"" + String.join( "\",\"", loaders) + "\"]&game_versions=[\"" + mcVersion + "\"]";
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
                            String.join( "\",\"", loaders) + "\"]" + (forceLatest ? "" : "&game_versions=[\"" + mcVersion + "\"]");
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
