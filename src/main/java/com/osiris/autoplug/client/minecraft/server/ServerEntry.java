/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single entry in {@code servers.dat}.
 * Preserves any unknown NBT string tags so round-tripping never loses data.
 */
public class ServerEntry {

    /** Display name shown in the multiplayer list. */
    @NotNull public String name;

    /** IP address or hostname (optionally with port, e.g. "play.example.com:25565"). */
    @NotNull public String ip;

    /** Icon as a Base64-encoded PNG data URI (may be null). */
    @Nullable public String icon;

    /** Accepts-texture-pack flag (0 = prompt, 1 = always, 2 = never). */
    public byte acceptTextures = 0;

    /** Pinged version string – populated by {@link ServerPinger}. */
    @Nullable public String detectedVersion;

    /** Current online players – populated by {@link ServerPinger}. */
    public int onlinePlayers = -1;

    /** Max players – populated by {@link ServerPinger}. */
    public int maxPlayers = -1;

    /** MOTD – populated by {@link ServerPinger}. */
    @Nullable public String motd;

    /** Ping latency in ms – populated by {@link ServerPinger}. */
    public long latencyMs = -1;

    /**
     * Extra string tags read from the NBT that this model does not natively handle.
     * Preserved verbatim on write so no data is lost.
     */
    public final Map<String, String> extraTags = new LinkedHashMap<>();

    public ServerEntry(@NotNull String name, @NotNull String ip) {
        this.name = name; this.ip = ip;
    }

    /** Returns host portion of the IP (without port). */
    @NotNull public String host() {
        int colon = ip.lastIndexOf(':');
        return colon > 0 ? ip.substring(0, colon) : ip;
    }

    /** Returns port, or 25565 if not specified. */
    public int port() {
        int colon = ip.lastIndexOf(':');
        if (colon <= 0) return 25565;
        try { return Integer.parseInt(ip.substring(colon + 1)); }
        catch (NumberFormatException e) { return 25565; }
    }

    @Override public String toString() { return name + " (" + ip + ")"; }
}
