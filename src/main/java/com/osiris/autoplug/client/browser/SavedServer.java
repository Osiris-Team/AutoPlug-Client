package com.osiris.autoplug.client.browser;

public final class SavedServer {
    public final String name, address;
    /** Last successful client launch targeting this favorite, in epoch milliseconds. */
    public final long lastJoinedAt;
    public SavedServer(String name, String address) {
        this(name, address, 0);
    }
    public SavedServer(String name, String address, long lastJoinedAt) {
        this.address = ServerAddress.parse(address).toString();
        this.name = name == null || name.trim().isEmpty() || "Minecraft Server".equalsIgnoreCase(name.trim()) ? this.address : name.trim();
        this.lastJoinedAt = Math.max(0, lastJoinedAt);
    }
    @Override public String toString() { return name + " (" + address + ")"; }
}
