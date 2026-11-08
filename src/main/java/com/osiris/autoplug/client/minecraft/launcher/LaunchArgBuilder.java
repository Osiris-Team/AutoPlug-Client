/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.launcher;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Builds the JVM + game command-line arguments for launching a Minecraft client.
 * Supports both pre-1.13 (minecraftArguments) and 1.13+ (arguments.*) JSON formats.
 * Pure MIT – no HMCL code used.
 */
public class LaunchArgBuilder {

    private final MojangManifest.VersionDetail detail;
    private final File nativesDir;
    private final List<File> classPath;
    private final File gameDir;
    private final File assetsDir;
    private final String playerName;
    private final String uuid;
    private final String accessToken;
    private final String versionType;
    private final List<String> extraJvmArgs;
    private final int maxMemMb;

    /**
     * @param detail       parsed version detail from {@link MojangManifest}
     * @param nativesDir   where extracted native libs live (e.g. .minecraft/natives/1.21.1)
     * @param classPath    ordered list of jars (client jar + libraries)
     * @param gameDir      .minecraft or profile game dir
     * @param assetsDir    .minecraft/assets (contains indexes/ and objects/)
     * @param playerName   display name
     * @param uuid         player UUID (dashes stripped for offline; with dashes for online)
     * @param accessToken  MSA access token, or "null" for offline
     * @param versionType  "release" / "snapshot" / "modified"
     * @param extraJvmArgs additional JVM flags from profile config
     * @param maxMemMb     -Xmx in megabytes
     */
    public LaunchArgBuilder(
            @NotNull MojangManifest.VersionDetail detail,
            @NotNull File nativesDir,
            @NotNull List<File> classPath,
            @NotNull File gameDir,
            @NotNull File assetsDir,
            @NotNull String playerName,
            @NotNull String uuid,
            @NotNull String accessToken,
            @NotNull String versionType,
            @Nullable List<String> extraJvmArgs,
            int maxMemMb) {
        this.detail       = detail;
        this.nativesDir   = nativesDir;
        this.classPath    = classPath;
        this.gameDir      = gameDir;
        this.assetsDir    = assetsDir;
        this.playerName   = playerName;
        this.uuid         = uuid;
        this.accessToken  = accessToken;
        this.versionType  = versionType;
        this.extraJvmArgs = extraJvmArgs != null ? extraJvmArgs : new ArrayList<>();
        this.maxMemMb     = maxMemMb;
    }

    /**
     * Builds the full command-line argument list (everything after the java executable).
     * Order: JVM args → main class → game args.
     */
    @NotNull
    public List<String> build() {
        List<String> cmd = new ArrayList<>();

        // Memory
        cmd.add("-Xmx" + maxMemMb + "m");
        cmd.add("-Xms256m");

        // Natives path (always needed)
        cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());

        // JVM args from manifest
        if (!detail.jvmArguments.isEmpty()) {
            for (MojangManifest.Argument a : detail.jvmArguments) {
                if (!a.appliesOnCurrentOs()) continue;
                if (a.value != null)       cmd.add(substituteVars(a.value));
                else if (a.values != null) for (String v : a.values) cmd.add(substituteVars(v));
            }
        } else {
            // Legacy: provide minimal JVM args that Mojang would have expected
            cmd.add("-cp");
            cmd.add(buildClassPathString());
        }

        // Extra profile JVM args
        cmd.addAll(extraJvmArgs);

        // Main class
        cmd.add(detail.mainClass);

        // Game args
        if (detail.minecraftArguments != null) {
            // Pre-1.13 flat string – split on spaces and substitute
            for (String token : detail.minecraftArguments.split(" ")) {
                cmd.add(substituteVars(token));
            }
        } else {
            for (MojangManifest.Argument a : detail.gameArguments) {
                if (!a.appliesOnCurrentOs()) continue;
                if (a.value != null)       cmd.add(substituteVars(a.value));
                else if (a.values != null) for (String v : a.values) cmd.add(substituteVars(v));
            }
        }

        return cmd;
    }

    // ── Variable substitution ────────────────────────────────────────────────

    private String substituteVars(String template) {
        return template
                .replace("${auth_player_name}",   playerName)
                .replace("${version_name}",        detail.id)
                .replace("${game_directory}",      gameDir.getAbsolutePath())
                .replace("${assets_root}",         assetsDir.getAbsolutePath())
                .replace("${assets_index_name}",   detail.assetIndex)
                .replace("${auth_uuid}",           uuid)
                .replace("${auth_access_token}",   accessToken)
                .replace("${user_type}",           "mojang")
                .replace("${version_type}",        versionType)
                .replace("${natives_directory}",   nativesDir.getAbsolutePath())
                .replace("${launcher_name}",       "AutoPlug")
                .replace("${launcher_version}",    "1.0")
                .replace("${classpath}",           buildClassPathString());
    }

    private String buildClassPathString() {
        StringBuilder sb = new StringBuilder();
        String sep = File.pathSeparator;
        for (int i = 0; i < classPath.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(classPath.get(i).getAbsolutePath());
        }
        return sb.toString();
    }
}
