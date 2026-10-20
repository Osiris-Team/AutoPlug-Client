package com.osiris.autoplug.client.tasks.updater.plugins;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class InstalledPluginLoader {
    public Loader loader;

    private static final Set<String> BUNGEE_FORKS = Set.of(
            "bungeecord",
            "waterfall"
    );

    private static final Set<String> FOLIA_FORKS = Set.of(
            "folia",
            "luminolmc",
            "canvas",
            "shreddedpaper"
    );

    public InstalledPluginLoader(@NotNull String software) {
        String type = software.toLowerCase(Locale.ROOT);

        if (type.equals("velocity")) {
            loader = Loader.VELOCITY;
        } else if (BUNGEE_FORKS.contains(type)) {
            loader = Loader.BUNGEECORD;
        } else if (FOLIA_FORKS.contains(type)) {
            loader = Loader.FOLIA_COMPATIBLE;
        } else {
            loader = Loader.SPIGOT_AND_FORKS;
        }
    }

    public List<String> getLoaderList() {
        switch (loader) {
            case VELOCITY:
                return List.of("velocity");
            case BUNGEECORD:
                return List.of("bungeecord", "waterfall");
            case SPIGOT_AND_FORKS:
                return List.of("spigot", "paper", "purpur", "bukkit");
            case FOLIA_COMPATIBLE:
                return List.of("folia");
        }
        return List.of();
    }
    /** Exact profile target: a Bukkit/Spigot instance must not receive a Paper-only or Purpur-only jar. */
    public static List<String> forProfile(String software) {
        switch (software.toLowerCase(Locale.ROOT)) {
            case "bukkit": return List.of("bukkit");
            case "spigot": return List.of("spigot", "bukkit");
            case "paper": return List.of("paper", "spigot", "bukkit");
            case "purpur": return List.of("purpur", "paper", "spigot", "bukkit");
            case "velocity": return List.of("velocity");
            case "bungeecord": return List.of("bungeecord");
            case "waterfall": return List.of("waterfall", "bungeecord");
            default: return new InstalledPluginLoader(software).getLoaderList();
        }
    }

    public enum Loader {
        VELOCITY,
        BUNGEECORD,
        SPIGOT_AND_FORKS,
        FOLIA_COMPATIBLE
    }

}
