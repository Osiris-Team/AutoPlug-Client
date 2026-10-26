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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.jlib.logger.AL;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads and caches Minecraft assets (index JSON + individual object files),
 * libraries, native libraries, and the client JAR.
 * <p>
 * Each file is verified against its expected SHA-1 before being accepted.
 * Already-correct files are skipped. Thread-safe; uses an internal thread pool.
 */
public class AssetDownloader {

    private static final String RESOURCES_BASE = "https://resources.download.minecraft.net/";
    private static final String LIBRARIES_BASE  = "https://libraries.minecraft.net/";
    private static final int    PARALLELISM     = 8;

    private final OkHttpClient http;
    private final File assetsDir;   // .minecraft/assets  (contains indexes/ and objects/)
    private final File librariesDir;// .minecraft/libraries
    private final File nativesDir;  // .minecraft/natives/<version>
    private final File clientJar;   // .minecraft/versions/<id>/<id>.jar

    /**
     * @param assetsDir    root assets directory (indexes/ and objects/ will be created inside)
     * @param librariesDir root libraries directory
     * @param nativesDir   per-version directory where native zips are extracted
     * @param clientJar    destination for the versioned client.jar
     */
    public AssetDownloader(@NotNull File assetsDir, @NotNull File librariesDir,
                           @NotNull File nativesDir, @NotNull File clientJar) {
        this.assetsDir    = assetsDir;
        this.librariesDir = librariesDir;
        this.nativesDir   = nativesDir;
        this.clientJar    = clientJar;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .followRedirects(true)
                .build();
    }

    /**
     * Downloads everything required by the given version detail.
     *
     * @param detail parsed version info from {@link MojangManifest}
     * @param progressListener called with (downloadedCount, totalCount) after each file completes
     */
    public void downloadAll(@NotNull MojangManifest.VersionDetail detail,
                            ProgressListener progressListener) throws Exception {
        // 1. Client JAR
        clientJar.getParentFile().mkdirs();
        downloadIfNeeded(detail.clientJarUrl, clientJar, detail.clientJarSha1, detail.clientJarSize);

        // 2. Libraries + natives
        for (MojangManifest.Library lib : detail.libraries) {
            if (!lib.appliesOnCurrentOs()) continue;

            // Main artifact
            if (lib.url != null) {
                File dest = new File(librariesDir, lib.toRelativePath());
                dest.getParentFile().mkdirs();
                downloadIfNeeded(lib.url, dest, lib.sha1, lib.size);
            }

            // Native artifact – download then extract
            if (lib.nativesUrl != null) {
                String relPath = lib.name.replace(':', '/') + "-natives.jar";
                File nativeJar = new File(nativesDir, relPath);
                nativeJar.getParentFile().mkdirs();
                downloadIfNeeded(lib.nativesUrl, nativeJar, lib.nativesSha1, 0);
                extractNatives(nativeJar, nativesDir);
            }
        }

        // 3. Asset index JSON
        File indexFile = new File(assetsDir, "indexes/" + detail.assetIndex + ".json");
        indexFile.getParentFile().mkdirs();
        downloadIfNeeded(detail.assetIndexUrl, indexFile, detail.assetIndexSha1, 0);

        // 4. Asset objects (parallel)
        downloadAssetObjects(indexFile, progressListener);
    }

    private void downloadAssetObjects(File indexFile, ProgressListener listener) throws Exception {
        JsonObject objects = new Gson()
                .fromJson(new FileReader(indexFile), JsonObject.class)
                .getAsJsonObject("objects");

        int total = objects.size();
        AtomicInteger done = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(PARALLELISM);
        CompletionService<Void> cs = new ExecutorCompletionService<>(pool);
        int submitted = 0;

        for (java.util.Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj  = entry.getValue().getAsJsonObject();
            String hash     = obj.get("hash").getAsString();
            long size       = obj.get("size").getAsLong();
            String prefix   = hash.substring(0, 2);
            File dest       = new File(assetsDir, "objects/" + prefix + "/" + hash);
            String url      = RESOURCES_BASE + prefix + "/" + hash;

            cs.submit(() -> {
                try {
                    dest.getParentFile().mkdirs();
                    downloadIfNeeded(url, dest, hash, size);
                } catch (Exception e) {
                    AL.warn("Asset download failed: " + url, e);
                }
                int n = done.incrementAndGet();
                if (listener != null) listener.onProgress(n, total);
                return null;
            });
            submitted++;
        }

        pool.shutdown();
        for (int i = 0; i < submitted; i++) cs.take(); // wait for all
        pool.awaitTermination(30, TimeUnit.MINUTES);
    }

    /** Downloads {@code url} to {@code dest} only if missing or SHA-1 mismatch. */
    void downloadIfNeeded(String url, File dest, String expectedSha1, long expectedSize)
            throws IOException {
        if (dest.exists() && dest.length() > 0) {
            if (expectedSha1 == null || expectedSha1.equalsIgnoreCase(sha1(dest))) return;
        }
        File tmp = new File(dest.getAbsolutePath() + ".tmp");
        Request req = new Request.Builder().url(url)
                .header("User-Agent","AutoPlug-Client - https://autoplug.one").build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException("HTTP " + resp.code() + " downloading " + url);
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("Null body for " + url);
            try (InputStream in = body.byteStream();
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
        }
        // Verify
        if (expectedSha1 != null && !expectedSha1.equalsIgnoreCase(sha1(tmp))) {
            tmp.delete();
            throw new IOException("SHA-1 mismatch for " + dest.getName() + " (url: " + url + ")");
        }
        Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void extractNatives(File nativeJar, File nativesDir) throws IOException {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(nativeJar)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith("/") || entry.getName().contains("META-INF")) continue;
                File out = new File(nativesDir, new File(entry.getName()).getName());
                out.getParentFile().mkdirs();
                try (InputStream in = zf.getInputStream(entry);
                     OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                }
            }
        }
    }

    private static String sha1(File f) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            return DigestUtils.sha1Hex(in);
        }
    }

    /** Callback for reporting download progress. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int downloaded, int total);
    }
}
