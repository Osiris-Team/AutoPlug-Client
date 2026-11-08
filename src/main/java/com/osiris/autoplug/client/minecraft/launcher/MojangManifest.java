/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reads the Mojang version manifest v2 and per-version JSON files.
 * Pure MIT-licensed – no HMCL code used or referenced.
 */
public class MojangManifest {

    public static final String MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    // ── DTOs ────────────────────────────────────────────────────────────────

    /** Lightweight entry from the top-level version list. */
    public static class VersionEntry {
        public final String id;
        public final String type;   // "release","snapshot","old_beta","old_alpha"
        public final String url;
        @Nullable public final String sha1;

        public VersionEntry(String id, String type, String url, @Nullable String sha1) {
            this.id = id; this.type = type; this.url = url; this.sha1 = sha1;
        }
        @Override public String toString() { return id + " (" + type + ")"; }
    }

    /** Full version descriptor resolved from the per-version JSON. */
    public static class VersionDetail {
        public final String id;
        public final String mainClass;
        public final String assetIndex;
        public final String assetIndexUrl;
        @Nullable public final String assetIndexSha1;
        public final String clientJarUrl;
        public final String clientJarSha1;
        public final long   clientJarSize;
        /** Pre-1.13 flat arg string; null for versions that use "arguments". */
        @Nullable public final String minecraftArguments;
        public final List<Argument> jvmArguments;
        public final List<Argument> gameArguments;
        public final List<Library>  libraries;
        /** Recommended Java major version (e.g. 17 for 1.18+). */
        public final int javaVersion;

        VersionDetail(String id, String mainClass, String assetIndex, String assetIndexUrl,
                      @Nullable String assetIndexSha1, String clientJarUrl, String clientJarSha1,
                      long clientJarSize, @Nullable String minecraftArguments,
                      List<Argument> jvmArguments, List<Argument> gameArguments,
                      List<Library> libraries, int javaVersion) {
            this.id = id; this.mainClass = mainClass;
            this.assetIndex = assetIndex; this.assetIndexUrl = assetIndexUrl;
            this.assetIndexSha1 = assetIndexSha1;
            this.clientJarUrl = clientJarUrl; this.clientJarSha1 = clientJarSha1;
            this.clientJarSize = clientJarSize;
            this.minecraftArguments = minecraftArguments;
            this.jvmArguments = jvmArguments; this.gameArguments = gameArguments;
            this.libraries = libraries; this.javaVersion = javaVersion;
        }
    }

    /** A JVM or game argument that may have OS/feature rules. */
    public static class Argument {
        @Nullable public final String value;
        @Nullable public final List<String> values;
        public final List<Rule> rules;

        Argument(@Nullable String value, @Nullable List<String> values, List<Rule> rules) {
            this.value = value; this.values = values; this.rules = rules;
        }

        /** True when this argument should be emitted on the running OS. */
        public boolean appliesOnCurrentOs() {
            if (rules.isEmpty()) return true;
            for (Rule r : rules) {
                boolean m = r.os == null || r.os.equalsIgnoreCase(currentOs());
                if ("allow".equals(r.action) && m) return true;
                if ("disallow".equals(r.action) && m) return false;
            }
            return false;
        }

        /** Returns "windows", "osx", or "linux". */
        public static String currentOs() {
            String n = System.getProperty("os.name", "").toLowerCase();
            if (n.contains("win")) return "windows";
            if (n.contains("mac")) return "osx";
            return "linux";
        }
    }

    /** A single allow/disallow rule from the JSON. */
    public static class Rule {
        public final String action;
        @Nullable public final String os;
        Rule(String action, @Nullable String os) { this.action = action; this.os = os; }
    }

    /** A Maven library or native library required by the version. */
    public static class Library {
        public final String name;
        @Nullable public final String url;
        @Nullable public final String sha1;
        public final long size;
        @Nullable public final String nativeClassifier; // e.g. "natives-linux"
        @Nullable public final String nativesUrl;
        @Nullable public final String nativesSha1;
        public final List<Rule> rules;

        Library(String name, @Nullable String url, @Nullable String sha1, long size,
                @Nullable String nativeClassifier, @Nullable String nativesUrl,
                @Nullable String nativesSha1, List<Rule> rules) {
            this.name = name; this.url = url; this.sha1 = sha1; this.size = size;
            this.nativeClassifier = nativeClassifier; this.nativesUrl = nativesUrl;
            this.nativesSha1 = nativesSha1; this.rules = rules;
        }

        public boolean appliesOnCurrentOs() {
            if (rules.isEmpty()) return true;
            String os = Argument.currentOs();
            for (Rule r : rules) {
                boolean m = r.os == null || r.os.equalsIgnoreCase(os);
                if ("allow".equals(r.action) && m) return true;
                if ("disallow".equals(r.action) && m) return false;
            }
            return false;
        }

        /**
         * Converts maven coords to a Maven-layout relative path.
         * "com.example:foo:1.0" → "com/example/foo/1.0/foo-1.0[-classifier].jar"
         */
        public String toRelativePath() {
            String[] p = name.split(":");
            if (p.length < 3) return name.replace(':', '/');
            String g = p[0].replace('.', '/'), a = p[1], v = p[2];
            String c = nativeClassifier != null ? "-" + nativeClassifier : "";
            return g + "/" + a + "/" + v + "/" + a + "-" + v + c + ".jar";
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Downloads and parses the top-level version manifest.
     * @return all version entries, newest-release first (Mojang order)
     */
    @NotNull
    public static List<VersionEntry> fetchVersionList() throws IOException {
        String body = getString(MANIFEST_URL);
        JsonArray arr = new Gson().fromJson(body, JsonObject.class).getAsJsonArray("versions");
        List<VersionEntry> list = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            list.add(new VersionEntry(o.get("id").getAsString(), o.get("type").getAsString(),
                    o.get("url").getAsString(),
                    o.has("sha1") ? o.get("sha1").getAsString() : null));
        }
        return list;
    }

    /** @return matching {@link VersionEntry} or null if not found. */
    @Nullable
    public static VersionEntry findVersion(String version) throws IOException {
        for (VersionEntry e : fetchVersionList())
            if (e.id.equals(version)) return e;
        return null;
    }

    @NotNull
    public static VersionDetail fetchVersionDetail(@NotNull VersionEntry entry) throws IOException {
        return parseVersionJson(getString(entry.url));
    }

    @NotNull
    public static VersionDetail fetchVersionDetail(String version) throws IOException {
        VersionEntry e = findVersion(version);
        if (e == null) throw new IOException("Version '" + version + "' not found in manifest.");
        return fetchVersionDetail(e);
    }

    // ── JSON parsing (package-visible for unit tests) ───────────────────────

    @NotNull
    public static VersionDetail parseVersionJson(String json) {
        JsonObject root = new Gson().fromJson(json, JsonObject.class);
        String id        = root.get("id").getAsString();
        String mainClass = root.get("mainClass").getAsString();

        JsonObject ai  = root.getAsJsonObject("assetIndex");
        String aiId    = ai.get("id").getAsString();
        String aiUrl   = ai.get("url").getAsString();
        String aiSha1  = ai.has("sha1") ? ai.get("sha1").getAsString() : null;

        JsonObject cl = root.getAsJsonObject("downloads").getAsJsonObject("client");
        String cUrl  = cl.get("url").getAsString();
        String cSha1 = cl.get("sha1").getAsString();
        long   cSize = cl.get("size").getAsLong();

        int jv = 8;
        if (root.has("javaVersion")) {
            JsonObject jo = root.getAsJsonObject("javaVersion");
            if (jo.has("majorVersion")) jv = jo.get("majorVersion").getAsInt();
        }

        String legacy = null;
        List<Argument> jvmA = new ArrayList<>(), gameA = new ArrayList<>();
        if (root.has("minecraftArguments")) {
            legacy = root.get("minecraftArguments").getAsString();
        } else if (root.has("arguments")) {
            JsonObject ao = root.getAsJsonObject("arguments");
            if (ao.has("jvm"))  jvmA  = parseArgList(ao.getAsJsonArray("jvm"));
            if (ao.has("game")) gameA = parseArgList(ao.getAsJsonArray("game"));
        }

        List<Library> libs = new ArrayList<>();
        if (root.has("libraries"))
            for (JsonElement le : root.getAsJsonArray("libraries")) {
                Library l = parseLibrary(le.getAsJsonObject());
                if (l != null) libs.add(l);
            }

        return new VersionDetail(id, mainClass, aiId, aiUrl, aiSha1, cUrl, cSha1, cSize,
                legacy, jvmA, gameA, libs, jv);
    }

    private static List<Argument> parseArgList(JsonArray arr) {
        List<Argument> r = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive()) {
                r.add(new Argument(el.getAsString(), null, new ArrayList<>()));
            } else {
                JsonObject o = el.getAsJsonObject();
                List<Rule> rules = parseRules(o);
                JsonElement v = o.get("value");
                if (v == null) continue;
                if (v.isJsonPrimitive()) {
                    r.add(new Argument(v.getAsString(), null, rules));
                } else {
                    List<String> vals = new ArrayList<>();
                    for (JsonElement x : v.getAsJsonArray()) vals.add(x.getAsString());
                    r.add(new Argument(null, vals, rules));
                }
            }
        }
        return r;
    }

    private static List<Rule> parseRules(JsonObject o) {
        List<Rule> r = new ArrayList<>();
        if (!o.has("rules")) return r;
        for (JsonElement re : o.getAsJsonArray("rules")) {
            JsonObject ro = re.getAsJsonObject();
            String action = ro.get("action").getAsString();
            String os = null;
            if (ro.has("os")) {
                JsonObject osObj = ro.getAsJsonObject("os");
                if (osObj.has("name")) os = osObj.get("name").getAsString();
            }
            r.add(new Rule(action, os));
        }
        return r;
    }

    @Nullable
    private static Library parseLibrary(JsonObject o) {
        if (!o.has("name")) return null;
        String name = o.get("name").getAsString();
        List<Rule> rules = parseRules(o);
        String url = null, sha1 = null; long size = 0;
        if (o.has("downloads")) {
            JsonObject dl = o.getAsJsonObject("downloads");
            if (dl.has("artifact")) {
                JsonObject a = dl.getAsJsonObject("artifact");
                url  = a.has("url")  ? a.get("url").getAsString()  : null;
                sha1 = a.has("sha1") ? a.get("sha1").getAsString() : null;
                size = a.has("size") ? a.get("size").getAsLong()   : 0;
            }
        }
        String nCls = null, nUrl = null, nSha1 = null;
        if (o.has("natives")) {
            String osKey = Argument.currentOs();
            JsonObject nm = o.getAsJsonObject("natives");
            if (nm.has(osKey)) {
                nCls = nm.get(osKey).getAsString().replace("${arch}",
                        System.getProperty("os.arch","x64").contains("64") ? "64" : "32");
                if (o.has("downloads")) {
                    JsonObject classif = o.getAsJsonObject("downloads").getAsJsonObject("classifiers");
                    if (classif != null && classif.has(nCls)) {
                        JsonObject nc = classif.getAsJsonObject(nCls);
                        nUrl  = nc.has("url")  ? nc.get("url").getAsString()  : null;
                        nSha1 = nc.has("sha1") ? nc.get("sha1").getAsString() : null;
                    }
                }
            }
        }
        return new Library(name, url, sha1, size, nCls, nUrl, nSha1, rules);
    }

    static String getString(String url) throws IOException {
        Request req = new Request.Builder().url(url)
                .header("User-Agent","AutoPlug-Client - https://autoplug.one").build();
        try (Response resp = HTTP.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " for " + url);
            ResponseBody b = resp.body();
            if (b == null) throw new IOException("Null body for " + url);
            return b.string();
        }
    }
}
