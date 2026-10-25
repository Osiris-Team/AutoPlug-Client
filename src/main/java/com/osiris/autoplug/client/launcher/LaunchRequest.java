package com.osiris.autoplug.client.launcher;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Profile-scoped inputs; no launcher operation changes user.dir or the global server. */
public final class LaunchRequest {
    public final Path gameDir;
    public final String version;
    public final String loader;
    public final String loaderVersion;
    public final MinecraftAccount account;
    public final String serverHost;
    public final int serverPort;
    public final String singleplayerWorld;
    public final boolean fullscreen;

    public LaunchRequest(Path gameDir, String version, String loader, String loaderVersion,
                         MinecraftAccount account, String serverHost, int serverPort) {
        this(gameDir, version, loader, loaderVersion, account, serverHost, serverPort, null);
    }

    public LaunchRequest(Path gameDir, String version, String loader, String loaderVersion,
                         MinecraftAccount account, String serverHost, int serverPort, String singleplayerWorld) {
        this(gameDir, version, loader, loaderVersion, account, serverHost, serverPort, singleplayerWorld, true);
    }

    public LaunchRequest(Path gameDir, String version, String loader, String loaderVersion,
                         MinecraftAccount account, String serverHost, int serverPort, boolean fullscreen) {
        this(gameDir, version, loader, loaderVersion, account, serverHost, serverPort, null, fullscreen);
    }

    public LaunchRequest(Path gameDir, String version, String loader, String loaderVersion,
                         MinecraftAccount account, String serverHost, int serverPort, String singleplayerWorld, boolean fullscreen) {
        this.gameDir = Objects.requireNonNull(gameDir, "gameDir").toAbsolutePath().normalize();
        this.version = safeVersion(version);
        this.loader = loader == null ? "VANILLA" : loader.toUpperCase(Locale.ROOT);
        if (!Arrays.asList("VANILLA", "FABRIC", "QUILT", "FORGE", "NEOFORGE").contains(this.loader))
            throw new IllegalArgumentException("Unsupported client loader: " + loader);
        this.loaderVersion = loaderVersion == null || loaderVersion.trim().isEmpty() ? null : safeVersion(loaderVersion);
        this.account = Objects.requireNonNull(account, "account");
        this.serverHost = serverHost == null || serverHost.trim().isEmpty() ? null : serverHost.trim();
        if (this.serverHost != null && (serverPort < 1 || serverPort > 65535))
            throw new IllegalArgumentException("Server port must be between 1 and 65535.");
        if (this.serverHost != null && (this.serverHost.contains("\n") || this.serverHost.contains("\r")))
            throw new IllegalArgumentException("Invalid server address.");
        this.serverPort = serverPort;
        if (singleplayerWorld != null && (singleplayerWorld.isEmpty() || singleplayerWorld.equals(".") || singleplayerWorld.equals("..")
                || singleplayerWorld.indexOf('/') >= 0 || singleplayerWorld.indexOf('\\') >= 0 || singleplayerWorld.indexOf(':') >= 0
                || singleplayerWorld.chars().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Singleplayer world must be a save directory name.");
        if (singleplayerWorld != null && this.serverHost != null)
            throw new IllegalArgumentException("Choose either a local world or a multiplayer server.");
        this.singleplayerWorld = singleplayerWorld;
        this.fullscreen = fullscreen;
    }

    static String safeVersion(String value) {
        if (value == null || value.isEmpty() || value.equals(".") || value.contains("..")
                || !value.matches("[A-Za-z0-9._+ -]+"))
            throw new IllegalArgumentException("Invalid Minecraft or loader version: " + value);
        return value;
    }
    public Path getGameDir() { return gameDir; }
    public String getVersion() { return version; }
    public String getLoader() { return loader; }
}
