/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.profile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory representation of a named launch profile.
 * Persistence is handled by {@link ProfileManager}.
 */
public class LaunchProfile {

    /** Unique profile name (used as directory name). */
    @NotNull public String name;

    /** Minecraft version string, e.g. "1.21.1". */
    @NotNull public String minecraftVersion;

    /**
     * Mod loader type: "vanilla", "fabric", "quilt", "forge", "neoforge".
     * Default is "vanilla".
     */
    @NotNull public String loaderType = "vanilla";

    /**
     * Override game directory. If null the default
     * {@code autoplug/profiles/<name>/game} is used.
     */
    @Nullable public File gameDir;

    /** Extra JVM flags, e.g. ["-Dfml.ignoreInvalidMinecraftCertificates=true"]. */
    @NotNull public List<String> jvmArgs = new ArrayList<>();

    /**
     * Maximum JVM heap in megabytes (-Xmx). 0 means use the launcher's default (2048).
     */
    public int maxMemoryMb = 0;

    /**
     * Mojang version type reported to the game: "release" or "snapshot".
     * Usually matches the version type from the manifest.
     */
    @Nullable public String versionType;

    /**
     * Optional association with a server address (host:port).
     * When set, launching this profile will auto-connect to the server.
     */
    @Nullable public String serverAddress;

    /** List of mod file names in the profile's mods directory. Informational only. */
    @NotNull public List<String> mods = new ArrayList<>();

    public LaunchProfile(@NotNull String name, @NotNull String minecraftVersion) {
        this.name = name;
        this.minecraftVersion = minecraftVersion;
    }

    /** Returns the profile root directory: {@code autoplug/profiles/<name>/}. */
    @NotNull
    public File profileDir(File workingDir) {
        return new File(workingDir, "autoplug/profiles/" + name);
    }

    /** Returns the mods directory for this profile. */
    @NotNull
    public File modsDir(File workingDir) {
        return new File(profileDir(workingDir), "mods");
    }

    @Override public String toString() {
        return name + " [" + minecraftVersion + "/" + loaderType + "]";
    }
}
