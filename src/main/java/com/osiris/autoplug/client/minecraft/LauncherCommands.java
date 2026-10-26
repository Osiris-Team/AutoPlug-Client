/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft;

import com.osiris.autoplug.client.minecraft.launcher.MinecraftLauncher;
import com.osiris.autoplug.client.minecraft.profile.LaunchProfile;
import com.osiris.autoplug.client.minecraft.profile.ProfileManager;
import com.osiris.autoplug.client.minecraft.server.ServerBrowser;
import com.osiris.autoplug.client.minecraft.server.ServerEntry;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * Handles AutoPlug dot-commands for the Minecraft launcher, profiles, and server browser.
 * <p>
 * Commands are routed here from {@code Commands.execute()} in the main console handler.
 * <p>
 * Supported commands:
 * <pre>
 *   .mc launch <profile-name> [playerName] [uuid] [token]
 *   .mc profile create <name> <mc-version>
 *   .mc profile list
 *   .mc profile delete <name>
 *   .mc server add <name> <ip>
 *   .mc server list
 *   .mc server remove <ip>
 *   .mc server ping <ip>
 *   .mc server join <ip> [playerName]
 * </pre>
 */
public class LauncherCommands {

    /**
     * Entry point. Returns true if the command was recognised and handled.
     *
     * @param args tokens after ".mc" (already trimmed and split on spaces)
     */
    public static boolean execute(@NotNull String[] args) {
        if (args.length == 0) { printHelp(); return true; }
        switch (args[0]) {
            case "launch":  return doLaunch(args);
            case "profile": return doProfile(args);
            case "server":  return doServer(args);
            case "help":    printHelp(); return true;
            default:        return false;
        }
    }

    // ── .mc launch ────────────────────────────────────────────────────────────

    private static boolean doLaunch(String[] args) {
        if (args.length < 2) { AL.info("Usage: .mc launch <profile-name> [playerName] [uuid] [token]"); return true; }
        String profileName = args[1];
        String playerName  = args.length > 2 ? args[2] : "Player";
        String uuid        = args.length > 3 ? args[3] : null;
        String token       = args.length > 4 ? args[4] : null;
        new Thread(() -> {
            try {
                LaunchProfile p = ProfileManager.load(profileName);
                if (p == null) { AL.warn("Profile '" + profileName + "' not found. Use .mc profile create first."); return; }
                Process proc = MinecraftLauncher.launch(p, playerName, uuid, token,
                        (done, total) -> AL.info("[Download] " + done + "/" + total));
                AL.info("[Launcher] Minecraft launched (PID: " + proc.pid() + "). Waiting...");
                int exit = proc.waitFor();
                AL.info("[Launcher] Minecraft exited with code " + exit);
            } catch (Exception e) {
                AL.warn("[Launcher] Launch failed: " + e.getMessage(), e);
            }
        }, "mc-launch-" + profileName).start();
        return true;
    }

    // ── .mc profile ───────────────────────────────────────────────────────────

    private static boolean doProfile(String[] args) {
        if (args.length < 2) { AL.info("Usage: .mc profile <create|list|delete> [args...]"); return true; }
        switch (args[1]) {
            case "create": {
                if (args.length < 4) { AL.info("Usage: .mc profile create <name> <mc-version>"); return true; }
                try {
                    LaunchProfile p = ProfileManager.create(args[2], args[3]);
                    AL.info("Created profile: " + p);
                } catch (Exception e) { AL.warn("Profile create failed: " + e.getMessage(), e); }
                return true;
            }
            case "list": {
                List<String> names = ProfileManager.listNames();
                if (names.isEmpty()) { AL.info("No profiles found. Use .mc profile create <name> <version>."); return true; }
                AL.info("Profiles:");
                for (String n : names) {
                    try {
                        LaunchProfile p = ProfileManager.load(n);
                        AL.info("  " + (p != null ? p : n));
                    } catch (Exception e) { AL.info("  " + n + " (error loading)"); }
                }
                return true;
            }
            case "delete": {
                if (args.length < 3) { AL.info("Usage: .mc profile delete <name>"); return true; }
                boolean ok = ProfileManager.delete(args[2]);
                AL.info(ok ? "Deleted profile '" + args[2] + "'." : "Profile '" + args[2] + "' not found.");
                return true;
            }
            default: AL.info("Unknown profile sub-command: " + args[1]); return true;
        }
    }

    // ── .mc server ────────────────────────────────────────────────────────────

    private static boolean doServer(String[] args) {
        if (args.length < 2) { AL.info("Usage: .mc server <add|list|remove|ping|join> [args...]"); return true; }
        File datFile = new File(GD.WORKING_DIR, "autoplug/profiles/default/game/servers.dat");
        ServerBrowser browser = new ServerBrowser(datFile);
        switch (args[1]) {
            case "add": {
                if (args.length < 4) { AL.info("Usage: .mc server add <name> <ip>"); return true; }
                try {
                    browser.addServer(new ServerEntry(args[2], args[3]));
                    AL.info("Added server: " + args[2] + " (" + args[3] + ")");
                } catch (Exception e) { AL.warn("Failed to add server: " + e.getMessage(), e); }
                return true;
            }
            case "list": {
                try {
                    List<ServerEntry> servers = browser.readServers();
                    if (servers.isEmpty()) { AL.info("No servers in servers.dat."); return true; }
                    AL.info("Servers:");
                    for (ServerEntry s : servers) AL.info("  " + s);
                } catch (Exception e) { AL.warn("Failed to read servers: " + e.getMessage(), e); }
                return true;
            }
            case "remove": {
                if (args.length < 3) { AL.info("Usage: .mc server remove <ip>"); return true; }
                try {
                    boolean ok = browser.removeServer(args[2]);
                    AL.info(ok ? "Removed " + args[2] : "Server not found: " + args[2]);
                } catch (Exception e) { AL.warn("Failed to remove server: " + e.getMessage(), e); }
                return true;
            }
            case "ping": {
                if (args.length < 3) { AL.info("Usage: .mc server ping <ip>"); return true; }
                ServerEntry e = new ServerEntry(args[2], args[2]);
                boolean ok = browser.ping(e);
                if (ok) AL.info("Ping " + args[2] + ": " + e.detectedVersion
                        + " | " + e.onlinePlayers + "/" + e.maxPlayers
                        + " | " + e.latencyMs + "ms | " + e.motd);
                else   AL.info("Server " + args[2] + " is offline or unreachable.");
                return true;
            }
            case "join": {
                if (args.length < 3) { AL.info("Usage: .mc server join <ip> [playerName]"); return true; }
                String ip    = args[2];
                String player = args.length > 3 ? args[3] : "Player";
                ServerEntry e = new ServerEntry(ip, ip);
                new Thread(() -> {
                    try {
                        Process proc = browser.join(e, player, null, null);
                        int exit = proc.waitFor();
                        AL.info("[Launcher] Game exited: " + exit);
                    } catch (Exception ex) {
                        AL.warn("[Launcher] Join failed: " + ex.getMessage(), ex);
                    }
                }, "mc-join-" + ip).start();
                return true;
            }
            default: AL.info("Unknown server sub-command: " + args[1]); return true;
        }
    }

    private static void printHelp() {
        AL.info("Minecraft Launcher commands:");
        AL.info("  .mc launch <profile> [player] [uuid] [token]  – Launch a profile");
        AL.info("  .mc profile create <name> <version>           – Create a profile");
        AL.info("  .mc profile list                              – List profiles");
        AL.info("  .mc profile delete <name>                     – Delete a profile");
        AL.info("  .mc server add <name> <ip>                    – Add a server");
        AL.info("  .mc server list                               – List servers");
        AL.info("  .mc server remove <ip>                        – Remove a server");
        AL.info("  .mc server ping <ip>                          – Ping a server");
        AL.info("  .mc server join <ip> [player]                 – Join a server");
    }
}
