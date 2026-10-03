package com.osiris.autoplug.client.launcher;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** A launch identity. Credentials are deliberately excluded from diagnostic output. */
public final class MinecraftAccount {
    public final String username;
    public final String uuid;
    public final String accessToken;
    public final String refreshToken;
    public final String clientId;
    public final String xuid;
    public final boolean offline;
    public final Instant expiresAt;

    public MinecraftAccount(String username, String uuid, String accessToken, String refreshToken,
                            String clientId, String xuid, boolean offline, Instant expiresAt) {
        if (username == null || !username.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Minecraft names must contain 1-16 letters, digits or underscores.");
        if (uuid == null || !uuid.replace("-", "").matches("[a-fA-F0-9]{32}"))
            throw new IllegalArgumentException("Invalid Minecraft account UUID.");
        if (!offline && (accessToken == null || accessToken.isEmpty()))
            throw new IllegalArgumentException("A Microsoft account requires a Minecraft access token.");
        this.username = username;
        this.uuid = uuid.replace("-", "");
        this.accessToken = accessToken == null ? "0" : accessToken;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        this.clientId = clientId == null ? "" : clientId;
        this.xuid = xuid == null ? "" : xuid;
        this.offline = offline;
        this.expiresAt = expiresAt == null ? Instant.EPOCH : expiresAt;
    }

    public static MinecraftAccount offline(String username) {
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        return new MinecraftAccount(username, id.toString(), "0", "", "", "", true, Instant.MAX);
    }

    public boolean needsRefresh() { return !offline && expiresAt.isBefore(Instant.now().plusSeconds(60)); }
    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public boolean isOffline() { return offline; }
    @Override public String toString() { return username + (offline ? " (offline)" : " (Microsoft)"); }
}
