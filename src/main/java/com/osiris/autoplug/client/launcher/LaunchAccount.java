package com.osiris.autoplug.client.launcher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Credentials exist only in memory. Do not log a launch command containing the access token. */
public final class LaunchAccount {
    private final String name, uuid, accessToken, userType, xuid, clientId;

    private LaunchAccount(String name, String uuid, String token, String type, String xuid, String clientId) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Minecraft name must contain 1–16 letters, digits or underscores");
        this.name = name;
        this.uuid = uuid.replace("-", "");
        if (!this.uuid.matches("[a-fA-F0-9]{32}")) throw new IllegalArgumentException("Invalid Minecraft UUID");
        this.accessToken = token;
        this.userType = type;
        this.xuid = xuid == null ? "" : xuid;
        this.clientId = clientId == null ? "" : clientId;
    }

    public static LaunchAccount offline(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new LaunchAccount(name, uuid.toString(), "0", "legacy", "", "");
    }

    public static LaunchAccount microsoft(String name, String uuid, String accessToken, String xuid, String clientId) {
        if (accessToken == null || accessToken.isEmpty()) throw new IllegalArgumentException("Missing Minecraft access token");
        return new LaunchAccount(name, uuid, accessToken, "msa", xuid, clientId);
    }

    public String getName() { return name; }
    public String getUuid() { return uuid; }
    public String getAccessToken() { return accessToken; }
    public String getUserType() { return userType; }
    public String getXuid() { return xuid; }
    public String getClientId() { return clientId; }
    public boolean isOffline() { return "legacy".equals(userType); }
    @Override public String toString() { return "LaunchAccount{" + name + ", " + userType + "}"; }
}
