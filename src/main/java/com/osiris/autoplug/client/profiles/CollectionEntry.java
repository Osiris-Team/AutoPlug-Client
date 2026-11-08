package com.osiris.autoplug.client.profiles;

/** Provider identity belongs to the collection, not a global updater configuration. */
public class CollectionEntry {
    public String file, name, version, author, modrinthId, curseforgeId, githubRepo, githubAsset, customCheckUrl, customDownloadUrl;
    public int spigotId, bukkitId;
    public boolean excluded;
}
