package com.osiris.autoplug.client.profiles;

public enum ProfileType {
    MODS, PLUGINS, MODS_SERVER;

    public String collectionDirectory() { return this == PLUGINS ? "plugins" : "mods"; }
    public boolean isClient() { return this == MODS; }
}
