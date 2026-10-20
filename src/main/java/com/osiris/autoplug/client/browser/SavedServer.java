package com.osiris.autoplug.client.browser;

import java.util.Locale;
import java.util.Objects;

public final class SavedServer {
    public final String name;
    public final ServerAddress address;
    /** Standard server status does not identify a loader; this is the user's local choice. */
    public final String loaderHint;
    public SavedServer(String name, ServerAddress address, String loaderHint) {
        if (name == null || name.trim().isEmpty() || name.length() > 255 || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid server name");
        String loader = loaderHint == null ? "VANILLA" : loaderHint.toUpperCase(Locale.ROOT);
        if (!loader.matches("VANILLA|FABRIC|QUILT|FORGE|NEOFORGE")) throw new IllegalArgumentException("Choose a supported client loader");
        this.name = name.trim(); this.address = Objects.requireNonNull(address); this.loaderHint = loader;
    }
    @Override public String toString() { return name + " — " + address + " (" + loaderHint + ")"; }
}
