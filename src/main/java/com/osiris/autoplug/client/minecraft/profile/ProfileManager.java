/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.profile;

import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates, loads, lists, and deletes {@link LaunchProfile} instances.
 * Profiles are stored under {@code autoplug/profiles/<name>/profile.yml}.
 */
public class ProfileManager {

    private static final File PROFILES_ROOT = new File(GD.WORKING_DIR, "autoplug/profiles");

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Creates a new profile with the given name and Minecraft version.
     * Fails if a profile with that name already exists.
     *
     * @throws IllegalArgumentException if name is blank or already taken
     */
    @NotNull
    public static LaunchProfile create(@NotNull String name, @NotNull String mcVersion)
            throws Exception {
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Profile name must not be blank.");
        File dir = profileDir(name);
        if (dir.exists()) throw new IllegalArgumentException("Profile '" + name + "' already exists.");
        dir.mkdirs();

        LaunchProfile p = new LaunchProfile(name, mcVersion);
        save(p);
        try { AL.info("[Profiles] Created profile: " + p); } catch (Exception ignored) {}
        return p;
    }

    /**
     * Loads a profile by name from disk.
     *
     * @return the profile, or null if it does not exist
     */
    @Nullable
    public static LaunchProfile load(@NotNull String name) throws Exception {
        File yml = profileYml(name);
        if (!yml.exists()) return null;
        ProfileConfig cfg = new ProfileConfig(yml);
        LaunchProfile p   = cfg.toProfile();
        p.name = name; // ensure name matches directory
        return p;
    }

    /**
     * Persists a profile to disk, overwriting any existing config.
     */
    public static void save(@NotNull LaunchProfile p) throws Exception {
        File dir = profileDir(p.name);
        dir.mkdirs();
        new ProfileConfig(profileYml(p.name)).fromProfile(p);
    }

    /**
     * Deletes a profile and its entire directory.
     *
     * @return true if the directory was successfully deleted
     */
    public static boolean delete(@NotNull String name) {
        File dir = profileDir(name);
        if (!dir.exists()) return false;
        return deleteRecursive(dir);
    }

    /**
     * Lists all profile names present in the profiles root directory.
     */
    @NotNull
    public static List<String> listNames() {
        List<String> names = new ArrayList<>();
        File[] dirs = PROFILES_ROOT.listFiles(File::isDirectory);
        if (dirs == null) return names;
        for (File d : dirs) {
            if (new File(d, "profile.yml").exists()) names.add(d.getName());
        }
        return names;
    }

    /**
     * Loads all existing profiles from disk.
     */
    @NotNull
    public static List<LaunchProfile> loadAll() {
        List<LaunchProfile> profiles = new ArrayList<>();
        for (String name : listNames()) {
            try {
                LaunchProfile p = load(name);
                if (p != null) profiles.add(p);
            } catch (Exception e) {
                try { AL.warn("[Profiles] Failed to load profile '" + name + "': " + e.getMessage(), e); } catch (Exception ignored) {}
            }
        }
        return profiles;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @NotNull public static File profileDir(@NotNull String name) {
        return new File(PROFILES_ROOT, name);
    }

    @NotNull public static File profileYml(@NotNull String name) {
        return new File(profileDir(name), "profile.yml");
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        return f.delete();
    }
}
