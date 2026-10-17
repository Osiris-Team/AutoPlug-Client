package com.osiris.autoplug.client.mc.worlds;

import java.nio.file.Path;

/** An AutoPlug-owned world directory, separate from the global server and vanilla saves. */
public final class World {
    private final String id;
    private final String profileId;
    private final Path directory;

    World(String id, String profileId, Path directory) {
        this.id = id;
        this.profileId = profileId;
        this.directory = directory;
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public Path getDirectory() { return directory; }
}
