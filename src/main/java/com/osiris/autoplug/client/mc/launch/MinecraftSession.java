package com.osiris.autoplug.client.mc.launch;

/** In-memory session. Tokens are never persisted or included in toString(). */
public final class MinecraftSession {
    private final String username;
    private final String uuid;
    private final String accessToken;
    private final String userType;
    private final String clientId;
    private final String xuid;

    public MinecraftSession(String username, String uuid, String accessToken, String userType) {
        this(username, uuid, accessToken, userType, "", "");
    }

    public MinecraftSession(String username, String uuid, String accessToken, String userType, String clientId, String xuid) {
        if (username == null || username.isEmpty() || uuid == null || !uuid.replace("-", "").matches("[a-fA-F0-9]{32}")
                || accessToken == null || userType == null) throw new IllegalArgumentException("Invalid Minecraft session");
        this.username = username;
        this.uuid = uuid.replace("-", "");
        this.accessToken = accessToken;
        this.userType = userType;
        this.clientId = clientId;
        this.xuid = xuid;
    }

    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public String getAccessToken() { return accessToken; }
    public String getUserType() { return userType; }
    public String getClientId() { return clientId; }
    public String getXuid() { return xuid; }
    @Override public String toString() { return "MinecraftSession[" + username + "]"; }
}
