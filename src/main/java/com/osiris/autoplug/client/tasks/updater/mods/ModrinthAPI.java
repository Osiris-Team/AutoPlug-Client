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
import java.util.List;


public class ModrinthAPI {
    private final String baseUrl;

    public ModrinthAPI() { this("https://api.modrinth.com/v2"); }
    public ModrinthAPI(String baseUrl) { this.baseUrl = baseUrl; }

    /** Identify exact installed bytes, rather than guessing a project from a display name. A 404 is a genuine unknown file. */
    public JsonObject identifyProfileFile(java.nio.file.Path file) throws java.io.IOException {
        String hash = com.osiris.autoplug.client.profiles.SharedArtifactCache.hash(file, "SHA-1");
        com.google.gson.JsonElement result = profileGet("/version_file/" + hash + "?algorithm=sha1", true);
        return result == null ? null : result.getAsJsonObject();
    }
    public JsonObject profileVersion(String versionId) throws java.io.IOException {
        if (versionId == null || !versionId.matches("[A-Za-z0-9_-]{1,128}")) throw new java.io.IOException("Invalid Modrinth version id");
        com.google.gson.JsonElement result = profileGet("/version/" + versionId, true);
        return result == null ? null : result.getAsJsonObject();
    }

    /** Explicit target version and loader resolution also works for downgrades, independent of local timestamps. */
    public JsonObject compatibleProfileRelease(String projectId, List<String> loaders, String mcVersion, String pinnedVersionId) throws java.io.IOException {
        if (projectId == null || !projectId.matches("[A-Za-z0-9_-]{1,128}")) throw new java.io.IOException("Invalid Modrinth project id");
        if (pinnedVersionId != null) {
            if (!pinnedVersionId.matches("[A-Za-z0-9_-]{1,128}")) throw new java.io.IOException("Invalid Modrinth version id");
            com.google.gson.JsonElement result = profileGet("/version/" + pinnedVersionId, true);
            if (result == null) return null;
            JsonObject version = result.getAsJsonObject();
            return projectId.equals(version.get("project_id").getAsString()) && profileCompatible(version, loaders, mcVersion) ? version : null;
        }
        String query = "/project/" + projectId + "/version?loaders=" + java.net.URLEncoder.encode(new com.google.gson.Gson().toJson(loaders), "UTF-8")
                + "&game_versions=" + java.net.URLEncoder.encode(new com.google.gson.Gson().toJson(java.util.Collections.singletonList(mcVersion)), "UTF-8");
        com.google.gson.JsonElement result = profileGet(query, true);
        if (result == null) return null;
        JsonObject selected = null;
        for (com.google.gson.JsonElement element : result.getAsJsonArray()) {
            JsonObject candidate = element.getAsJsonObject();
            if (!profileCompatible(candidate, loaders, mcVersion)) continue;
            if (selected == null || java.time.Instant.parse(candidate.get("date_published").getAsString()).isAfter(java.time.Instant.parse(selected.get("date_published").getAsString()))) selected = candidate;
        }
        return selected;
    }
    private static boolean profileCompatible(JsonObject version, List<String> loaders, String mcVersion) {
        boolean game = false, loader = false;
        for (com.google.gson.JsonElement value : version.getAsJsonArray("game_versions")) if (mcVersion.equals(value.getAsString())) game = true;
        for (com.google.gson.JsonElement value : version.getAsJsonArray("loaders")) if (loaders.contains(value.getAsString())) loader = true;
        return game && loader;
    }
    private com.google.gson.JsonElement profileGet(String relative, boolean missingAllowed) throws java.io.IOException {
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(baseUrl + relative).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "AutoPlug-Client/10.2.0 (native profile updater)"); connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == 404 && missingAllowed) return null;
            if (status != 200) throw new java.io.IOException("Modrinth API returned HTTP " + status);
            try (java.io.Reader input = new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                return com.google.gson.JsonParser.parseReader(input);
            } catch (RuntimeException invalid) { throw new java.io.IOException("Invalid Modrinth API response", invalid); }
        } finally { connection.disconnect(); }
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
