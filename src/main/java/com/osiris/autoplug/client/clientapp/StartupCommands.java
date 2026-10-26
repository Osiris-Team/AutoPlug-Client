package com.osiris.autoplug.client.clientapp;

import java.util.*;
import java.util.function.Consumer;
import com.osiris.autoplug.client.profiles.ProfileCommands;

/** Startup commands are separated by a dot at a token boundary, not dots inside versions or hosts. */
public final class StartupCommands {
    private StartupCommands() { }
    public static List<String> parse(List<String> arguments) {
        String input = String.join(" ", arguments).trim();
        List<String> commands = new ArrayList<>();
        for (String candidate : input.split("(?=\\s+\\.[A-Za-z])")) {
            String text = candidate.trim(); if (text.startsWith(".")) commands.add(text);
        }
        return Collections.unmodifiableList(commands);
    }
    public static boolean clientOnly(List<String> commands) {
        if (commands.isEmpty()) return false;
        for (String command : commands) if (!(command.equals(".mc") || command.startsWith(".mc ") || command.equals(".client") || command.startsWith(".client ") || command.equals(".profiles") || command.startsWith(".profiles ") || command.matches("\\.(check|update) (mods|plugins|mods-server|mods_server)(?: .*|$)"))) return false;
        return true;
    }
    /** One-shot client/profile work skips server startup, background recurring tasks and online console. */
    public static boolean executeClientOnly(List<String> arguments, Consumer<String> output) {
        List<String> commands = parse(arguments); if (!clientOnly(commands)) return false;
        for (String command : commands) {
            if (!ProfileCommands.tryHandle(command, output) && !WorldCommands.tryHandle(command, output, true) && !NativeClientCommands.tryHandle(command, output)) output.accept("No active isolated profile for: " + command + ". Use .profiles activate <id>, or run the legacy server console for current-directory checks.");
        }
        return true;
    }
}
