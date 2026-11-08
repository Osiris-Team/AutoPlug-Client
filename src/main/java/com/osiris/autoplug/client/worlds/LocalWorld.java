package com.osiris.autoplug.client.worlds;

import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** A read-only view of an existing Minecraft save; it is never a managed server. */
public final class LocalWorld {
    public final String id, name, gameVersion;
    public final Path directory, gameDirectory, icon;
    public final boolean modded;
    public final boolean cheats, hardcore, quickPlayEligible, sizeComplete;
    public final long lastPlayed, sizeBytes;
    public final List<String> dataPacks;

    LocalWorld(String id, String name, String gameVersion, Path directory, Path gameDirectory, Path icon, boolean modded) {
        this(id, name, gameVersion, directory, gameDirectory, icon, modded, -1, -1, false, Collections.emptyList(), false, false);
    }
    LocalWorld(String id, String name, String gameVersion, Path directory, Path gameDirectory, Path icon, boolean modded,
               long lastPlayed, long sizeBytes, boolean sizeComplete, List<String> dataPacks, boolean cheats, boolean hardcore) {
        this.id = id; this.name = name; this.gameVersion = gameVersion;
        this.directory = directory; this.gameDirectory = gameDirectory; this.icon = icon; this.modded = modded;
        this.lastPlayed = lastPlayed; this.sizeBytes = sizeBytes; this.sizeComplete = sizeComplete;
        this.dataPacks = Collections.unmodifiableList(new ArrayList<>(dataPacks)); this.cheats = cheats; this.hardcore = hardcore;
        this.quickPlayEligible = !modded && supportsQuickPlay(gameVersion);
    }
    LocalWorld withId(String id) { return new LocalWorld(id, name, gameVersion, directory, gameDirectory, icon, modded, lastPlayed, sizeBytes, sizeComplete, dataPacks, cheats, hardcore); }
    private static boolean supportsQuickPlay(String version) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.\\d+)?(?:[- ].*)?$").matcher(version);
        if (!match.matches()) return false;
        try { return Integer.parseInt(match.group(1)) > 1 || (Integer.parseInt(match.group(1)) == 1 && Integer.parseInt(match.group(2)) >= 20); }
        catch (NumberFormatException e) { return false; }
    }

    /** The directory name, rather than LevelName, is what Minecraft Quick Play accepts. */
    public String saveName() { return directory.getFileName().toString(); }
}
