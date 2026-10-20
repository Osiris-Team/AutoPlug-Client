package com.osiris.autoplug.client.clientapp;

import com.osiris.autoplug.client.worlds.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/** .mc is the combined client/world interface; .client remains a supported alias. */
public final class WorldCommands {
    private final WorldStore worlds;
    private final VirtualWorldService lifecycle;
    public WorldCommands(WorldStore worlds, VirtualWorldService lifecycle) { this.worlds = worlds; this.lifecycle = lifecycle; }
    public static boolean tryHandle(String command, Consumer<String> output, boolean waitForClient) {
        if (command == null || !(command.trim().equals(".mc") || command.trim().startsWith(".mc "))) return false;
        String text = command.trim();
        if (!(text.equals(".mc") || text.equals(".mc help") || text.startsWith(".mc worlds"))) return NativeClientCommands.tryHandle(".client" + text.substring(3), output);
        try { WorldClientContext context = WorldClientContext.of(NativeClientService.getDefault()); return new WorldCommands(context.worlds, context.lifecycle).handle(text, output, waitForClient); }
        catch (Exception failure) { output.accept("World command failed: " + failure.getMessage()); return true; }
    }
    public boolean handle(String command, Consumer<String> output, boolean waitForClient) {
        if (command == null || !(command.trim().equals(".mc") || command.trim().startsWith(".mc worlds") || command.trim().equals(".mc help"))) return false;
        try {
            String[] words = command.trim().split("\\s+");
            if (words.length <= 2 || words[1].equals("help")) { usage(output); return true; }
            String action = words[2];
            if (action.equals("list") && words.length == 3) for (WorldDefinition world : worlds.list()) output.accept(world + (lifecycle.isRunning(world.id) ? " | running" : " | stopped") + (world.acceptedEula ? "" : " | EULA acceptance needed"));
            else if (action.equals("create") && words.length >= 5) output.accept("Created world " + worlds.create(String.join(" ", Arrays.copyOfRange(words, 3, words.length - 1)), words[words.length - 1]));
            else if (action.equals("client") && words.length == 5) output.accept("World client: " + worlds.setClientProfile(words[3], words[4].equals("default") ? null : words[4]));
            else if (action.equals("jar") && words.length >= 5) output.accept("World server executable: " + worlds.setServerExecutable(words[3], Paths.get(String.join(" ", Arrays.copyOfRange(words, 4, words.length)))));
            else if (action.equals("accept-eula") && words.length == 4) output.accept("Minecraft EULA acceptance saved for " + worlds.acceptEula(words[3], true).id);
            else if (action.equals("revoke-eula") && words.length == 4) output.accept("Minecraft EULA acceptance removed for " + worlds.acceptEula(words[3], false).id);
            else if (action.equals("delete") && words.length == 5 && words[4].equals("--confirm")) { if (lifecycle.isRunning(words[3])) throw new IllegalStateException("Stop the world before deleting its save data"); worlds.delete(words[3]); output.accept("Deleted world " + words[3]); }
            else if (action.equals("stop") && words.length == 4) { VirtualWorldService.RunningWorld running = lifecycle.running(words[3]); lifecycle.stop(words[3]); output.accept("Stopped world " + words[3]); if (running != null && running.cleanupFailure() != null) output.accept(running.cleanupFailure()); }
            else if (action.equals("share") && words.length == 4) output.accept("Join address: " + lifecycle.share(words[3]));
            else if (action.equals("launch") && (words.length == 4 || words.length == 5 && words[4].equals("--share"))) {
                VirtualWorldService.RunningWorld running = lifecycle.launch(words[3], words.length == 5);
                try {
                    output.accept("World client started: " + running.address);
                    String address = running.publicAddress(); if (address != null) output.accept("Join address: " + address);
                    if (running.shareFailure() != null) output.accept("World is running locally; sharing failed: " + running.shareFailure());
                    if (waitForClient) running.clientProcess().waitFor();
                } finally { if (waitForClient) { running.close(); if (running.cleanupFailure() != null) output.accept(running.cleanupFailure()); } }
            } else throw new IllegalArgumentException("Enter .mc worlds for usage; deleting a world requires --confirm");
        } catch (Exception failure) { if (failure instanceof InterruptedException) Thread.currentThread().interrupt(); output.accept("World command failed: " + failure.getMessage()); }
        return true;
    }
    private static void usage(Consumer<String> output) {
        output.accept(".mc worlds list\n.mc worlds create <name> <server-profile-id>\n.mc worlds client <world-id> <client-profile-id|default>\n.mc worlds jar <world-id> <server-jar-path>\n.mc worlds accept-eula <world-id>  (review https://www.minecraft.net/en-us/eula first)\n.mc worlds revoke-eula <world-id>\n.mc worlds launch <world-id> [--share]\n.mc worlds share <world-id>\n.mc worlds stop <world-id>\n.mc worlds delete <world-id> --confirm\n.mc launch <profile-id> [--server <host[:port]>]\n.mc servers list|import|export\nSharing is an explicit request for a temporary TCP router mapping and requires authenticated online play. A loopback-only world must be restarted with --share. Client/profile-only startup waits for a world's client to exit and then cleans up its server/mapping.");
    }
}
