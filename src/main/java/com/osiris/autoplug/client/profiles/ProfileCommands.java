/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.tasks.updater.mods.TaskModsUpdater;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Extends existing .check/.update commands with an explicit profile; unrelated legacy commands pass through. */
public final class ProfileCommands {
    private final ProfileStore store;
    private final ProfileUpdater updater;
    public ProfileCommands(ProfileStore store, ProfileUpdater updater) { this.store = store; this.updater = updater; }
    public static boolean tryHandle(String command, Consumer<String> output) {
        if (!isProfileCommand(command)) return false;
        try {
            ProfileStore store = ProfileStore.defaultStore();
            return new ProfileCommands(store, new ProfileUpdater(store.getCache())).handle(command, output);
        } catch (Exception exception) { output.accept("Profile command failed: " + exception.getMessage()); return true; }
    }
    private static boolean isProfileCommand(String command) {
        String text = command == null ? "" : command.trim();
        return text.equals(".profiles") || text.startsWith(".profiles ") || text.matches("\\.(check|update) (mods|plugins|mods-server|mods_server)(?: .*|$)");
    }
    public boolean handle(String command, Consumer<String> output) {
        if (!isProfileCommand(command)) return false;
        try {
            List<String> words = new ArrayList<>(Arrays.asList(command.trim().split("\\s+")));
            boolean confirm = words.remove("--confirm-disabling");
            if (words.get(0).equals(".profiles")) profiles(words, confirm, output);
            else {
                if (words.size() != 2 && !(words.size() == 4 && words.get(2).equals("--profile"))) throw new IOException("Usage: .check/.update mods|plugins|mods-server [--profile <id>] [--confirm-disabling]");
                Profile profile = words.size() == 4 ? store.get(words.get(3)) : store.getActive(type(words.get(1)));
                if (profile == null) {
                    if (words.get(0).equals(".check")) return false; // Preserve current-directory server checks in the interactive console.
                    throw new IOException("Select a default " + words.get(1) + " profile first: .profiles activate <id>, or use --profile <id>");
                }
                Profile.Type requested = type(words.get(1));
                if (profile.type != requested && !(requested == Profile.Type.MODS && profile.type == Profile.Type.MODS_SERVER)) throw new IOException("Requested updater type does not match profile type");
                process(profile, words.get(0).equals(".update"), confirm, output);
            }
        } catch (Exception exception) { output.accept("Profile command failed: " + exception.getMessage()); }
        return true;
    }
    private void profiles(List<String> words, boolean confirm, Consumer<String> output) throws IOException {
        if (words.size() == 1 || words.get(1).equals("help")) { usage(output); return; }
        String action = words.get(1);
        if (action.equals("list")) {
            Profile.Type filter = null;
            if (words.size() == 4 && words.get(2).equals("--type")) filter = type(words.get(3));
            else if (words.size() != 2) throw new IOException("Usage: .profiles list [--type mods|plugins|mods-server]");
            for (Profile profile : store.list()) if (filter == null || filter == profile.type) output.accept(profile.toString());
        } else if (action.equals("create")) {
            boolean template = words.remove("--template"); Profile.Type type = Profile.Type.MODS;
            int flag = words.indexOf("--type");
            if (flag != -1) { if (flag + 1 >= words.size()) throw new IOException("Missing profile type"); type = type(words.remove(flag + 1)); words.remove(flag); }
            if (words.size() != 5) throw new IOException("Usage: .profiles create <id> <version> <loader> [--type mods|plugins|mods-server] [--template]");
            output.accept("Created " + store.create(words.get(2), type, words.get(3), words.get(4), template));
        } else if (action.equals("clone")) {
            if (words.size() != 4 && words.size() != 5) throw new IOException("Usage: .profiles clone <source> <version> [new-id] [--confirm-disabling]");
            Profile cloned = words.size() == 5 ? store.clone(words.get(2), words.get(4), words.get(3)) : store.clone(words.get(2), words.get(3));
            try {
                ProfileUpdater.Plan plan = process(cloned, false, false, output);
                updater.apply(plan, confirm); output.accept("Cloned and migrated " + cloned);
            } catch (IOException failure) {
                try { store.delete(cloned.id); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        } else if (action.equals("delete")) {
            if (words.size() != 3) throw new IOException("Usage: .profiles delete <id>"); store.delete(words.get(2)); output.accept("Deleted profile " + words.get(2));
        } else if (action.equals("template")) {
            if (words.size() != 4 || !words.get(3).matches("true|false")) throw new IOException("Usage: .profiles template <id> true|false");
            output.accept("Updated " + store.setTemplate(words.get(2), Boolean.parseBoolean(words.get(3))));
        } else if (action.equals("activate")) {
            if (words.size() != 3) throw new IOException("Usage: .profiles activate <id>"); output.accept("Default update profile: " + store.setActive(words.get(2)));
        } else throw new IOException("Unknown profile action: " + action);
    }
    private ProfileUpdater.Plan process(Profile profile, boolean apply, boolean confirm, Consumer<String> output) throws IOException {
        return profile.type != Profile.Type.PLUGINS ? TaskModsUpdater.processProfile(profile, updater, apply, confirm, output) : TaskPluginsUpdater.processProfile(profile, updater, apply, confirm, output);
    }
    private static Profile.Type type(String value) throws IOException {
        try { return Profile.Type.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_')); } catch (IllegalArgumentException invalid) { throw new IOException("Type must be mods, plugins or mods-server"); }
    }
    private static void usage(Consumer<String> output) {
        output.accept(".profiles list [--type mods|plugins|mods-server]\n.profiles create <id> <version> <loader> [--type mods|plugins|mods-server] [--template]\n.profiles clone <source> <version> [new-id] [--confirm-disabling]\n.profiles delete <id>\n.profiles template <id> true|false\n.profiles activate <id>\n.check/.update mods|plugins|mods-server [--profile <id>] [--confirm-disabling]");
    }
}
