package com.osiris.autoplug.client.browser;

import java.awt.image.BufferedImage;

public final class ServerStatus {
    public final boolean online;
    public final String motd, version, message;
    public final int players, capacity, protocol;
    public final long latency;
    /** Validated 64x64 PNG from the status response, decoded off the Swing thread. */
    public final BufferedImage icon;
    public ServerStatus(boolean online, String motd, String version, int players, int capacity, int protocol, long latency, String message) {
        this(online, motd, version, players, capacity, protocol, latency, message, null);
    }
    public ServerStatus(boolean online, String motd, String version, int players, int capacity, int protocol, long latency, String message, BufferedImage icon) {
        this.online = online; this.motd = motd; this.version = version; this.players = players;
        this.capacity = capacity; this.protocol = protocol; this.latency = latency; this.message = message;
        this.icon = icon;
    }
}
