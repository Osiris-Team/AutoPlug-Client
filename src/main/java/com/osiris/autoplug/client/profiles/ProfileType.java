package com.osiris.autoplug.client.profiles;

import java.util.Arrays;

public enum ProfileType {
    MODS, PLUGINS, MODS_SERVER;

    public String collectionDirectory() { return this == PLUGINS ? "plugins" : "mods"; }
    public boolean isClient() { return this == MODS; }

    /** One compatibility rule for persisted profiles, CLI and the dashboard. */
    public boolean supportsLoader(String loader) {
        boolean pluginLoader = Arrays.asList("PAPER", "SPIGOT", "PURPUR", "BUKKIT", "VELOCITY", "BUNGEE").contains(loader);
        return this == PLUGINS ? pluginLoader : !pluginLoader;
    }
}
