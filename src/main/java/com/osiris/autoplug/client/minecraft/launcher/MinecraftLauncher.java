/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.launcher;

import com.osiris.autoplug.client.minecraft.profile.LaunchProfile;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.io.AsyncInputStream;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Orchestrates downloading Minecraft assets/libraries and launching the game process.
 * <p>
 * Offline mode: UUID is derived from player name (consistent with the vanilla launcher).
 * Online mode:  pass a valid MSA access-token and the authenticated UUID.
 * <p>
 * Pure MIT – no HMCL code used or referenced.
 */
public class MinecraftLauncher {

    /** Global cache root – mirrors vanilla .minecraft layout under autoplug/mc-cache/ */
    public static File cacheRoot() {
        return new File(GD.WORKING_DIR, "autoplug/mc-cache");
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Launches Minecraft for the given profile.
     *
     * @param profile      the profile to launch (contains MC version, game dir, JVM args, etc.)
     * @param playerName   display name
     * @param uuid         player UUID (with dashes); pass null to generate an offline UUID
     * @param accessToken  MSA access token; pass null for offline mode
     * @param progressListener may be null
     * @return the launched {@link Process}; callers should call {@link Process#waitFor()} or destroy
     */
    @NotNull
    public static Process launch(
            @NotNull LaunchProfile profile,
            @NotNull String playerName,
            @Nullable String uuid,
            @Nullable String accessToken,
            @Nullable AssetDownloader.ProgressListener progressListener) throws Exception {

        String version = profile.minecraftVersion;
        AL.info("[Launcher] Fetching version detail for " + version);
        MojangManifest.VersionDetail detail = MojangManifest.fetchVersionDetail(version);

        // Directories
        File cacheRoot   = cacheRoot();
        File assetsDir   = new File(cacheRoot, "assets");
        File librariesDir= new File(cacheRoot, "libraries");
        File nativesDir  = new File(cacheRoot, "natives/" + version);
        File clientJar   = new File(cacheRoot, "versions/" + version + "/" + version + ".jar");
        File gameDir     = profile.gameDir != null ? profile.gameDir
                : new File(GD.WORKING_DIR, "autoplug/profiles/" + profile.name + "/game");

        // Ensure dirs exist
        for (File d : new File[]{assetsDir, librariesDir, nativesDir, clientJar.getParentFile(), gameDir})
            d.mkdirs();

        // Download everything (skips files already cached with correct SHA-1)
        AL.info("[Launcher] Downloading/verifying assets and libraries...");
        AssetDownloader dl = new AssetDownloader(assetsDir, librariesDir, nativesDir, clientJar);
        dl.downloadAll(detail, progressListener);

        // Build classpath: libraries (main artifact) + client jar
        List<File> classPath = new ArrayList<>();
        for (MojangManifest.Library lib : detail.libraries) {
            if (!lib.appliesOnCurrentOs()) continue;
            if (lib.url != null) classPath.add(new File(librariesDir, lib.toRelativePath()));
        }
        classPath.add(clientJar);

        // Auth
        if (uuid == null)          uuid = offlineUUID(playerName);
        if (accessToken == null)   accessToken = "null";

        // Build args
        LaunchArgBuilder argBuilder = new LaunchArgBuilder(
                detail, nativesDir, classPath, gameDir, assetsDir,
                playerName, uuid, accessToken,
                profile.versionType != null ? profile.versionType : "release",
                profile.jvmArgs,
                profile.maxMemoryMb > 0 ? profile.maxMemoryMb : 2048);
        List<String> args = argBuilder.build();

        // Find java executable
        String javaExe = resolveJavaExecutable(detail.javaVersion);

        // Assemble full command
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.addAll(args);

        AL.info("[Launcher] Launching: " + cmd.get(0) + " " + detail.mainClass);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir);
        pb.redirectErrorStream(false);

        Process proc = pb.start();

        // Stream stdout/stderr via AsyncInputStream so they appear in the AutoPlug console
        AsyncInputStream stdout = new AsyncInputStream(proc.getInputStream());
        stdout.listeners.add(line -> AL.info("[MC] " + line));
        AsyncInputStream stderr = new AsyncInputStream(proc.getErrorStream());
        stderr.listeners.add(line -> AL.warn("[MC] " + line));

        return proc;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Generates a consistent offline UUID from a player name.
     * Uses the same algorithm as the vanilla launcher (UUID v3 / name-based).
     */
    @NotNull
    public static String offlineUUID(@NotNull String playerName) {
        UUID uuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
        return uuid.toString();
    }

    /**
     * Resolves the java executable to use. Prefers AutoPlug's bundled JRE if present
     * and its major version satisfies the version requirement.
     */
    @NotNull
    static String resolveJavaExecutable(int requiredMajor) {
        // 1. AutoPlug-managed JRE
        File apJre = new File(GD.WORKING_DIR, "autoplug/system/jre");
        if (apJre.exists()) {
            File[] bins = apJre.listFiles(f -> f.isDirectory() && new File(f,"bin/java").exists()
                    || new File(f,"bin/java.exe").exists());
            if (bins == null || bins.length == 0) {
                // Try one level deeper (JRE extracted into a single sub-dir)
                File candidate = findJreInSubdirs(apJre);
                if (candidate != null) return candidate.getAbsolutePath();
            } else {
                String exe = System.getProperty("os.name","").toLowerCase().contains("win")
                        ? "java.exe" : "java";
                return new File(bins[0], "bin/" + exe).getAbsolutePath();
            }
        }
        // 2. System java
        return "java";
    }

    @Nullable
    private static File findJreInSubdirs(File root) {
        File[] subs = root.listFiles(File::isDirectory);
        if (subs == null) return null;
        String exe = System.getProperty("os.name","").toLowerCase().contains("win")
                ? "java.exe" : "java";
        for (File sub : subs) {
            File bin = new File(sub, "bin/" + exe);
            if (bin.exists()) return bin;
        }
        return null;
    }
}
