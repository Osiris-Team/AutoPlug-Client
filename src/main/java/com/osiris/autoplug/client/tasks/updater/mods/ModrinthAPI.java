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
import com.osiris.autoplug.client.utils.UtilsCrypto;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;


public class ModrinthAPI {
    private final String baseUrl = "https://api.modrinth.com/v2";
    @FunctionalInterface interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }
    private final ConnectionFactory connections;

    public ModrinthAPI() { this(url -> (HttpURLConnection) url.openConnection()); }
    ModrinthAPI(ConnectionFactory connections) { this.connections = java.util.Objects.requireNonNull(connections); }

    /**
     * Resolve the exact installed bytes through Modrinth's version-file endpoint.
     * A 404 means the artifact is unknown; a provider failure is not a missing identity.
     * This lookup does not modify the artifact or any collection metadata.
     */
    public String findProjectForArtifact(Path installed) throws IOException {
        if (installed == null || !Files.isRegularFile(installed)) throw new IOException("Installed artifact is not a regular file: " + installed);
        String sha1 = UtilsCrypto.fastSHA1(installed.toFile());
        if (sha1 == null || !sha1.matches("[a-fA-F0-9]{40}")) throw new IOException("Could not hash installed artifact: " + installed);
        sha1 = sha1.toLowerCase(java.util.Locale.ROOT);
        HttpURLConnection connection = openConnection("/version_file/" + sha1 + "?algorithm=sha1");
        try {
            int status = connection.getResponseCode();
            if (status == 404) return null;
            if (status != 200) throw new IOException("Modrinth artifact lookup returned HTTP " + status);
            try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject version = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                com.google.gson.JsonElement project = version.get("project_id");
                if (project == null || !project.isJsonPrimitive() || !project.getAsJsonPrimitive().isString()
                        || !project.getAsString().matches("[A-Za-z0-9]{1,128}"))
                    throw new IOException("Modrinth artifact response has no valid project_id");
                return project.getAsString();
            } catch (com.google.gson.JsonParseException | IllegalStateException e) {
                throw new IOException("Invalid Modrinth artifact response", e);
            }
        } finally { connection.disconnect(); }
    }

    private HttpURLConnection openConnection(String path) throws IOException {
        HttpURLConnection connection = connections.open(new URL(baseUrl + path));
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "AutoPlug-Client/10.2 (https://github.com/Osiris-Team/AutoPlug-Client)");
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    public JsonObject projectMetadata(String id) throws IOException {
        return metadata("/project/" + checkedId(id)).getAsJsonObject();
    }
    public com.google.gson.JsonArray versionsFor(String project, String gameVersion, String loader) throws IOException {
        String query = "loaders=" + java.net.URLEncoder.encode(new com.google.gson.Gson().toJson(List.of(loader)), "UTF-8")
                + "&game_versions=" + java.net.URLEncoder.encode(new com.google.gson.Gson().toJson(List.of(gameVersion)), "UTF-8");
        return metadata("/project/" + checkedId(project) + "/version?" + query).getAsJsonArray();
    }
    public JsonObject versionMetadata(String id) throws IOException { return metadata("/version/" + checkedId(id)).getAsJsonObject(); }
    private String checkedId(String id) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new IOException("Invalid Modrinth identifier");
        return id;
    }
    private com.google.gson.JsonElement metadata(String path) throws IOException {
        HttpURLConnection connection = openConnection(path);
        try (com.osiris.autoplug.client.launcher.DownloadProgress.Transfer transfer =
                     com.osiris.autoplug.client.launcher.DownloadProgress.begin("Fetching Modrinth metadata", baseUrl + path)) {
            int status = connection.getResponseCode();
            transfer.redirect(connection.getURL().toString());
            if (status != 200) throw new IOException("Modrinth metadata returned HTTP " + status + " for " + path);
            long total = connection.getContentLengthLong();
            if (total > 8 * 1024 * 1024) throw new IOException("Modrinth metadata exceeds 8 MiB");
            try (java.io.InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream content = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Modrinth lookup cancelled");
                    if (content.size() + count > 8 * 1024 * 1024) throw new IOException("Modrinth metadata exceeds 8 MiB");
                    content.write(buffer, 0, count); transfer.update(content.size(), total);
                }
                com.google.gson.JsonElement result = com.google.gson.JsonParser.parseString(new String(content.toByteArray(), StandardCharsets.UTF_8));
                transfer.complete(content.size(), total); return result;
            } catch (com.google.gson.JsonParseException | IllegalStateException e) { throw new IOException("Invalid Modrinth metadata", e); }
        } finally { connection.disconnect(); }
    }

    /**
     * Exact target-version lookup for both upgrades and downgrades. Unlike the legacy
     * timestamp comparison, this compares artifact hashes and never treats a failed
     * request as proof that a compatible release does not exist.
     */
    public SearchResult searchCompatible(List<String> loaders, String id, String version, java.nio.file.Path installed) {
        SearchResult result = new SearchResult(null, SearchResult.Type.API_ERROR, null, null, ".jar", null, null, false);
        try {
            if (id == null || !id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("A Modrinth project id or slug is required");
            String query = "loaders=" + java.net.URLEncoder.encode(new com.google.gson.Gson().toJson(loaders), "UTF-8")
                    + "&game_versions=" + java.net.URLEncoder.encode(new com.google.gson.Gson().toJson(List.of(version)), "UTF-8");
            HttpURLConnection connection = openConnection("/project/" + id + "/version?" + query);
            try {
                if (connection.getResponseCode() == 404) {
                    result.type = SearchResult.Type.RESOURCE_NOT_FOUND;
                    return result;
                }
                if (connection.getResponseCode() != 200) throw new java.io.IOException("Modrinth returned HTTP " + connection.getResponseCode());
                try (java.io.Reader reader = new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                    SearchResult found = compatibleResult(com.google.gson.JsonParser.parseReader(reader).getAsJsonArray(), loaders, version, installed);
                    if (found.type == SearchResult.Type.UP_TO_DATE || found.type == SearchResult.Type.UPDATE_AVAILABLE)
                        found.modrinthProjectId = id;
                    return found;
                }
            } finally { connection.disconnect(); }
        } catch (Exception e) { result.exception = e; return result; }
    }

    /** Parse provider fixtures independently of network access. */
    public SearchResult compatibleResult(com.google.gson.JsonArray releases, List<String> loaders, String version, java.nio.file.Path installed) throws Exception {
        SearchResult result = new SearchResult(null, SearchResult.Type.RESOURCE_NOT_FOUND, null, null, ".jar", null, null, false);
        JsonObject selected = null;
        for (com.google.gson.JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            boolean versionMatch = false, loaderMatch = false;
            for (com.google.gson.JsonElement v : release.getAsJsonArray("game_versions")) if (version.equals(v.getAsString())) versionMatch = true;
            for (com.google.gson.JsonElement l : release.getAsJsonArray("loaders")) if (loaders.contains(l.getAsString())) loaderMatch = true;
            if (versionMatch && loaderMatch && (selected == null || Instant.parse(release.get("date_published").getAsString()).isAfter(Instant.parse(selected.get("date_published").getAsString())))) selected = release;
        }
        if (selected == null) return result;
        JsonObject file = null;
        for (com.google.gson.JsonElement element : selected.getAsJsonArray("files")) {
            JsonObject candidate = element.getAsJsonObject();
            if (!candidate.get("filename").getAsString().endsWith(".jar")) continue;
            if (file == null || candidate.has("primary") && candidate.get("primary").getAsBoolean()) file = candidate;
            if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) break;
        }
        if (file == null) throw new java.io.IOException("Compatible release has no JAR artifact");
        result.latestVersion = selected.get("version_number").getAsString();
        result.downloadUrl = file.get("url").getAsString(); result.fileName = file.get("filename").getAsString();
        result.fileSize = file.has("size") ? file.get("size").getAsLong() : -1;
        JsonObject hashes = file.getAsJsonObject("hashes");
        if (hashes != null) {
            result.sha512 = hashes.has("sha512") ? hashes.get("sha512").getAsString() : null;
            result.sha1 = hashes.has("sha1") ? hashes.get("sha1").getAsString() : null;
        }
        result.type = SearchResult.Type.UPDATE_AVAILABLE;
        String expected = result.sha512 != null ? result.sha512 : result.sha1;
        if (expected != null && java.nio.file.Files.isRegularFile(installed)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance(result.sha512 != null ? "SHA-512" : "SHA-1");
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(installed)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder actual = new StringBuilder(); for (byte b : digest.digest()) actual.append(String.format("%02x", b & 255));
            if (expected.equalsIgnoreCase(actual.toString())) result.type = SearchResult.Type.UP_TO_DATE;
        }
        return result;
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
