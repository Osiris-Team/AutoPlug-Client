package com.osiris.autoplug.client.mc.profiles;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable metadata; its directory is assigned by the repository, not by a metadata file. */
public final class Profile {
    private final String id;
    private final ProfileType type;
    private final String gameVersion;
    private final String loader;
    private final Path directory;
    private final boolean template;

    Profile(String id, ProfileType type, String gameVersion, String loader, Path directory, boolean template) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,79}")) {
            throw new IllegalArgumentException("Profile ID must contain 1-80 lowercase letters, digits, dots, underscores or hyphens and start with a letter or digit");
        }
        if (gameVersion == null || !gameVersion.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}")) {
            throw new IllegalArgumentException("A non-empty Minecraft version is required");
        }
        if (loader == null || !loader.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")) {
            throw new IllegalArgumentException("A non-empty loader identifier is required");
        }
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.gameVersion = gameVersion;
        this.loader = loader;
        this.directory = Objects.requireNonNull(directory, "directory");
        this.template = template;
    }

    public String getId() {
        return id;
    }

    public ProfileType getType() {
        return type;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getLoader() {
        return loader;
    }

    public Path getDirectory() {
        return directory;
    }

    public Path getAssetsDirectory() {
        return directory.resolve(type.getAssetDirectoryName());
    }

    public Path getConfigDirectory() {
        return directory.resolve("config");
    }

    public boolean isTemplate() {
        return template;
    }
}
