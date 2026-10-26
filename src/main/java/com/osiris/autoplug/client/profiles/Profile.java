/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** An immutable profile; it never changes server-wide updater configuration. */
public final class Profile {
    public enum Type { MODS, PLUGINS, MODS_SERVER }
    public final String id;
    public final Type type;
    public final String gameVersion;
    public final String loader;
    public final Path directoryPath;
    public final boolean isTemplate;

    public Profile(String id, Type type, String gameVersion, String loader, Path directoryPath, boolean isTemplate) {
        if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}")) throw new IllegalArgumentException("Invalid profile id");
        if (gameVersion == null || !gameVersion.matches("[a-zA-Z0-9][a-zA-Z0-9_.+ -]{0,79}")) throw new IllegalArgumentException("Invalid game version");
        if (loader == null || !loader.matches("[a-zA-Z0-9_-]{1,40}")) throw new IllegalArgumentException("Invalid loader");
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.gameVersion = gameVersion;
        this.loader = loader.toUpperCase(Locale.ROOT);
        this.directoryPath = Objects.requireNonNull(directoryPath, "directoryPath").toAbsolutePath().normalize();
        this.isTemplate = isTemplate;
    }
    public String getId() { return id; }
    public Type getType() { return type; }
    public String getGameVersion() { return gameVersion; }
    public String getLoader() { return loader; }
    public Path getDirectoryPath() { return directoryPath; }
    public boolean isTemplate() { return isTemplate; }
    public Path assetsDirectory() { return directoryPath.resolve(type == Type.PLUGINS ? "plugins" : "mods"); }
    @Override public String toString() { return id + " [" + type + ", " + gameVersion + ", " + loader + (isTemplate ? ", template" : "") + "]"; }
}
