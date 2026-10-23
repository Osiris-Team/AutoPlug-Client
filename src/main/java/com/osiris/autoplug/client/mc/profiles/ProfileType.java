package com.osiris.autoplug.client.mc.profiles;

/** Versioned collections may belong to a client or a server. */
public enum ProfileType {
    MODS("mods"),
    PLUGINS("plugins"),
    MODS_SERVER("mods");

    private final String assetDirectoryName;

    ProfileType(String assetDirectoryName) {
        this.assetDirectoryName = assetDirectoryName;
    }

    public String getAssetDirectoryName() {
        return assetDirectoryName;
    }
}
