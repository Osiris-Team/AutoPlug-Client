package com.osiris.autoplug.client.profiles;

import java.nio.file.Path;

/** Persisted profile identity; its directory is always derived by the store. */
public class Profile {
    public String id, name, gameVersion, loader, loaderVersion;
    public ProfileType type;
    public boolean template;
    public boolean migrationPending;
    public String migrationSummary = "";
    /** A bundled recipe is installed once; later user edits are never re-seeded. */
    public String builtinPreset;
    public boolean builtinPresetInstalled;
    private transient Path directory;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getGameVersion() { return gameVersion; }
    public String getLoader() { return loader; }
    public String getLoaderVersion() { return loaderVersion; }
    public ProfileType getType() { return type; }
    public boolean isTemplate() { return template; }
    public Path getDirectory() { return directory; }
    void setDirectory(Path directory) { this.directory = directory; }
}
