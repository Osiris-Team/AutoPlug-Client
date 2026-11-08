/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.server;

import com.osiris.autoplug.client.minecraft.launcher.MinecraftLauncher;
import com.osiris.autoplug.client.minecraft.profile.LaunchProfile;
import com.osiris.autoplug.client.minecraft.profile.ModVersionAdapter;
import com.osiris.autoplug.client.minecraft.profile.ProfileManager;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * High-level API for the AutoPlug Server Browser feature.
 * <ul>
 *   <li>Read / write {@code servers.dat} (NBT)</li>
 *   <li>Add / remove / edit server entries</li>
 *   <li>Ping servers asynchronously</li>
 *   <li>Join a server: detect required version → select / create profile → adapt mods → launch</li>
 * </ul>
 */
public class ServerBrowser {

    private static final Executor ASYNC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ServerBrowser-worker");
        t.setDaemon(true);
        return t;
    });

    private final File serversDatFile;

    /**
     * @param serversDatFile path to {@code servers.dat} (typically inside the profile's game dir)
     */
    public ServerBrowser(@NotNull File serversDatFile) {
        this.serversDatFile = serversDatFile;
    }

    // ── servers.dat CRUD ─────────────────────────────────────────────────────

    /** Reads all server entries from {@code servers.dat}. Returns empty list if file absent. */
    @NotNull
    public List<ServerEntry> readServers() throws java.io.IOException {
        return ServersDat.read(serversDatFile);
    }

    /** Persists the full server list to {@code servers.dat}. */
    public void writeServers(@NotNull List<ServerEntry> servers) throws java.io.IOException {
        ServersDat.write(serversDatFile, servers);
    }

    /** Adds a server and saves. */
    public void addServer(@NotNull ServerEntry entry) throws java.io.IOException {
        List<ServerEntry> list = readServers();
        list.add(entry);
        writeServers(list);
    }

    /** Removes the first server whose IP matches and saves. */
    public boolean removeServer(@NotNull String ip) throws java.io.IOException {
        List<ServerEntry> list = readServers();
        boolean removed = list.removeIf(e -> e.ip.equalsIgnoreCase(ip));
        if (removed) writeServers(list);
        return removed;
    }

    // ── Ping ─────────────────────────────────────────────────────────────────

    /**
     * Pings all known servers asynchronously, writing results into each {@link ServerEntry}.
     *
     * @param onDone called (possibly from a background thread) when all pings complete
     */
    public CompletableFuture<List<ServerEntry>> pingAllAsync() throws java.io.IOException {
        List<ServerEntry> servers = readServers();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ServerEntry s : servers) {
            futures.add(CompletableFuture.runAsync(() -> ServerPinger.ping(s), ASYNC));
        }
        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> servers);
    }

    /**
     * Pings a single server synchronously.
     *
     * @return true if the ping succeeded
     */
    public boolean ping(@NotNull ServerEntry entry) {
        return ServerPinger.ping(entry);
    }

    // ── Join flow ────────────────────────────────────────────────────────────

    /**
     * Joins a server:
     * <ol>
     *   <li>Pings the server to detect the required Minecraft version.</li>
     *   <li>Finds an existing profile matching that version, or clones the base template.</li>
     *   <li>Runs {@link ModVersionAdapter} to upgrade/downgrade mods for that MC version.</li>
     *   <li>Launches the game process pointing at the server.</li>
     * </ol>
     *
     * @param entry       server to join
     * @param playerName  Minecraft player name
     * @param uuid        player UUID (null = offline)
     * @param accessToken MSA access token (null = offline)
     * @return the launched {@link Process}
     */
    @NotNull
    public Process join(
            @NotNull ServerEntry entry,
            @NotNull String playerName,
            @Nullable String uuid,
            @Nullable String accessToken) throws Exception {

        // 1. Ping for version
        AL.info("[ServerBrowser] Pinging " + entry.ip + "...");
        ServerPinger.ping(entry);
        String detectedVersion = entry.detectedVersion;
        if (detectedVersion == null) {
            AL.warn("[ServerBrowser] Could not detect MC version from ping; attempting join with latest known profile.");
        }

        // 2. Find or create a suitable profile
        LaunchProfile profile = findOrCreateProfile(detectedVersion);

        // 3. Adapt mods for target version
        File modsDir = profile.modsDir(GD.WORKING_DIR);
        modsDir.mkdirs();
        if (modsDir.list() != null && modsDir.list().length > 0 && detectedVersion != null) {
            AL.info("[ServerBrowser] Adapting mods for Minecraft " + detectedVersion + "...");
            List<String> results = ModVersionAdapter.adapt(modsDir, detectedVersion, profile.loaderType);
            for (String r : results) AL.info("[Mods] " + r);
        }

        // 4. Set server address in profile so vanilla auto-connects
        profile.serverAddress = entry.ip;
        ProfileManager.save(profile);

        // 5. Launch
        AL.info("[ServerBrowser] Launching profile '" + profile.name + "' → " + entry.ip);
        return MinecraftLauncher.launch(profile, playerName, uuid, accessToken,
                (done, total) -> AL.info("[Download] " + done + "/" + total));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @NotNull
    private LaunchProfile findOrCreateProfile(@Nullable String mcVersion) throws Exception {
        if (mcVersion != null) {
            // Strip extra info like "1.21.1 Paper" → "1.21.1"
            String ver = extractCleanVersion(mcVersion);
            for (LaunchProfile p : ProfileManager.loadAll()) {
                if (p.minecraftVersion.equals(ver)) return p;
            }
            // Clone default template if it exists
            LaunchProfile template = ProfileManager.load("default");
            String name = "auto-" + ver;
            LaunchProfile p;
            if (template != null) {
                p = new LaunchProfile(name, ver);
                p.loaderType    = template.loaderType;
                p.jvmArgs       = new ArrayList<>(template.jvmArgs);
                p.maxMemoryMb   = template.maxMemoryMb;
            } else {
                p = new LaunchProfile(name, ver);
            }
            ProfileManager.save(p);
            return p;
        }
        // Fallback: use "default" profile or create one for the latest release
        LaunchProfile def = ProfileManager.load("default");
        if (def != null) return def;
        return ProfileManager.create("default", "1.21.1");
    }

    private static String extractCleanVersion(String raw) {
        // Strip pre/post info – keep only major.minor.patch
        return raw.replaceAll("[^0-9.].*", "").trim();
    }
}
