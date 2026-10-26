/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.profile;

import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.mods.ModrinthAPI;
import com.osiris.autoplug.client.tasks.updater.mods.InstalledModLoader;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Upgrades or downgrades mods in a profile's mods directory to match a target
 * Minecraft version.
 * <p>
 * Mods that have no release for the target version are renamed to
 * {@code <filename>.jar.disabled} so the game still starts cleanly.
 */
public class ModVersionAdapter {

    /**
     * Adapts all mods in {@code modsDir} for {@code targetMcVersion}.
     *
     * @param modsDir         directory containing {@code .jar} (and optionally {@code .jar.disabled}) files
     * @param targetMcVersion e.g. "1.21.1"
     * @param loaderType      "fabric", "forge", etc. – used when querying Modrinth
     * @return list of human-readable result messages (one per mod)
     */
    @NotNull
    public static List<String> adapt(@NotNull File modsDir,
                                     @NotNull String targetMcVersion,
                                     @NotNull String loaderType) {
        List<String> results = new ArrayList<>();
        File[] jars = modsDir.listFiles(f ->
                f.getName().endsWith(".jar") || f.getName().endsWith(".jar.disabled"));
        if (jars == null) {
            results.add("No mods directory or no mods found.");
            return results;
        }

        InstalledModLoader loaderInfo = new InstalledModLoader(
                "fabric".equalsIgnoreCase(loaderType), "quilt".equalsIgnoreCase(loaderType));

        for (File jar : jars) {
            String fileName = jar.getName().replace(".jar.disabled", "").replace(".jar","");
            try {
                // Re-enable disabled mod so we can inspect it
                File active = jar;
                if (jar.getName().endsWith(".jar.disabled")) {
                    active = new File(jar.getParent(), fileName + ".jar");
                    Files.copy(jar.toPath(), active.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    jar.delete();
                }

                // Build a minimal MinecraftMod from MANIFEST / fabric.mod.json in the jar
                MinecraftMod mod = readModMeta(active);
                if (mod == null) {
                    results.add("[SKIP] " + fileName + " – cannot read metadata");
                    continue;
                }

                // Query Modrinth for the target version
                SearchResult sr = new ModrinthAPI().searchUpdateMod(loaderInfo, mod, targetMcVersion);

                if (sr.type == SearchResult.Type.API_ERROR || sr.getDownloadUrl() == null) {
                    // No release for this MC version – disable
                    File disabled = new File(active.getParent(), active.getName() + ".disabled");
                    Files.move(active.toPath(), disabled.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    results.add("[DISABLED] " + fileName + " – no release for " + targetMcVersion);
                } else {
                    // Download and replace
                    File downloaded = downloadMod(sr.getDownloadUrl(), active);
                    if (downloaded != null) {
                        results.add("[UPDATED] " + fileName + " → " + sr.getLatestVersion());
                    } else {
                        results.add("[FAILED] " + fileName + " – download error");
                    }
                }
            } catch (Exception e) {
                results.add("[ERROR] " + fileName + ": " + e.getMessage());
                AL.warn("[ModAdapter] Error processing " + fileName, e);
            }
        }
        return results;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @org.jetbrains.annotations.Nullable
    private static MinecraftMod readModMeta(File jar) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar)) {
            // Try fabric.mod.json first
            java.util.zip.ZipEntry fe = zf.getEntry("fabric.mod.json");
            if (fe != null) {
                com.google.gson.JsonObject o = new com.google.gson.Gson().fromJson(
                        new java.io.InputStreamReader(zf.getInputStream(fe)),
                        com.google.gson.JsonObject.class);
                String id      = o.has("id") ? o.get("id").getAsString() : jar.getName();
                String version = o.has("version") ? o.get("version").getAsString() : "0";
                String author  = "unknown";
                if (o.has("authors") && o.getAsJsonArray("authors").size() > 0) {
                    com.google.gson.JsonElement a = o.getAsJsonArray("authors").get(0);
                    author = a.isJsonPrimitive() ? a.getAsString()
                            : a.getAsJsonObject().get("name").getAsString();
                }
                return new MinecraftMod(jar.getAbsolutePath(), id, version, author, id, null, null);
            }
            // Try MANIFEST.MF Name attribute
            java.util.zip.ZipEntry mf = zf.getEntry("META-INF/MANIFEST.MF");
            if (mf != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(zf.getInputStream(mf));
                String name = props.getProperty("Implementation-Title",
                             props.getProperty("Specification-Title", jar.getName().replace(".jar","")));
                String ver  = props.getProperty("Implementation-Version", "0");
                return new MinecraftMod(jar.getAbsolutePath(), name, ver, "unknown", null, null, null);
            }
        } catch (Exception ignored) {}
        // Fallback: use filename
        return new MinecraftMod(jar.getAbsolutePath(),
                jar.getName().replace(".jar",""), "0", "unknown", null, null, null);
    }

    @org.jetbrains.annotations.Nullable
    private static File downloadMod(String url, File dest) {
        try {
            okhttp3.OkHttpClient http = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                    .followRedirects(true).build();
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(url).header("User-Agent","AutoPlug-Client").build();
            try (okhttp3.Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) return null;
                okhttp3.ResponseBody body = resp.body();
                if (body == null) return null;
                File tmp = new File(dest.getAbsolutePath() + ".tmp");
                try (java.io.InputStream in = body.byteStream();
                     java.io.OutputStream out = new java.io.BufferedOutputStream(
                             new java.io.FileOutputStream(tmp))) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                Files.move(tmp.toPath(), dest.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return dest;
            }
        } catch (Exception e) {
            AL.warn("[ModAdapter] Download failed: " + url, e);
            return null;
        }
    }
}
