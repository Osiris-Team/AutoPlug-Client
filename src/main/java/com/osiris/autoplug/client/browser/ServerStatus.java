package com.osiris.autoplug.client.browser;

public final class ServerStatus {
    public final boolean online;
    public final String motd, version, message;
    public final int players, capacity, protocol;
    public final long latency;
    public ServerStatus(boolean online, String motd, String version, int players, int capacity, int protocol, long latency, String message) {
        this.online = online; this.motd = motd; this.version = version; this.players = players;
        this.capacity = capacity; this.protocol = protocol; this.latency = latency; this.message = message;
    }
}
