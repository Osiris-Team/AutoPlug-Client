package com.osiris.autoplug.client.browser;

import com.osiris.autoplug.client.utils.MineStat;
import java.util.regex.*;

/** Server browser adapter for AutoPlug's existing MineStat utility. */
public final class ServerStatus {
    public final SavedServer server;
    public final boolean online;
    public final String motd, version, failure;
    public final int protocol, players, capacity;
    public final long latency;
    public ServerStatus(SavedServer server, boolean online, String motd, String version, int protocol, int players, int capacity, long latency, String failure) {
        this.server = server; this.online = online; this.motd = motd; this.version = version;
        this.protocol = protocol; this.players = players; this.capacity = capacity; this.latency = latency; this.failure = failure;
    }
    public static ServerStatus ping(SavedServer server) {
        MineStat ping = new MineStat(server.address.host, server.address.port, 3, MineStat.Request.JSON);
        return new ServerStatus(server, ping.isServerUp(), ping.getStrippedMotd(), ping.getVersion(), ping.getProtocol(),
                ping.getCurrentPlayers(), ping.getMaximumPlayers(), ping.getLatency(), ping.pingResult.name());
    }
    /** Vanilla identifiers embedded in Paper/Forge labels; ambiguous proxy ranges require a user choice. */
    public String gameVersion() {
        if (!online || version == null) throw new IllegalStateException("Server did not return a version");
        Matcher match = Pattern.compile("(?<![0-9.])(?:[1-9][0-9]*\\.[0-9]+(?:\\.[0-9]+)?(?:-pre-?[0-9]+|-rc-?[0-9]+|-snapshot-[0-9]+)?|[0-9]{2}w[0-9]{2}[a-z])(?![0-9.])").matcher(version);
        if (!match.find()) throw new IllegalStateException("Cannot determine a release from '" + version + "'. Select a profile explicitly.");
        String result = match.group();
        if (match.find()) throw new IllegalStateException("Server advertises multiple versions. Select a profile explicitly.");
        return result;
    }
}
