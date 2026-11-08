package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.browser.SavedServer;
import com.osiris.autoplug.client.browser.ServerAddress;
import com.osiris.autoplug.client.ui.LauncherActions;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** CLI and GUI call the same application services; arguments containing dots remain intact. */
public class LauncherCommands {
    private final LauncherServices services;
    private final Consumer<String> output;
    public LauncherCommands(LauncherServices services, Consumer<String> output) { this.services = services; this.output = output; }
    public boolean handles(List<String> args) {
        if (args.isEmpty()) return false;
        return args.get(0).equals(".profiles") || args.get(0).equals(".mc")
                || (args.get(0).equals(".check") || args.get(0).equals(".update")) && args.contains("--profile");
    }
    public void execute(List<String> args) throws Exception {
        if (!handles(args)) throw new IOException("Not a profile or Minecraft launcher command");
        if (args.get(0).equals(".profiles")) profiles(args);
        else if (args.get(0).equals(".mc")) minecraft(args);
        else {
            String id = option(args, "--profile", null); require(id, "--profile <id> is required");
            String collection = at(args, 1, "mods or plugins is required");
            Profile profile = services.getProfiles().get(id);
            if (!collection.equals(profile.type.collectionDirectory())) throw new IOException("Collection type does not match the selected profile");
            if (args.get(0).equals(".check")) output.accept(services.checkProfile(id));
            else {
                output.accept(services.checkProfile(id));
                if (profile.migrationPending && !args.contains("--yes")) throw new IOException("Review .check " + collection + " --profile " + id + " then repeat .update with --yes to confirm migration");
                output.accept(services.updateProfile(id));
            }
        }
    }
    private void profiles(List<String> args) throws Exception {
        String verb = at(args, 1, help());
        switch (verb) {
            case "list":
                String type = option(args, "--type", null);
                for (LauncherActions.ProfileInfo p : services.profiles()) if (type == null || normalizeType(type).equals(p.type))
                    output.accept(p.id + " | " + p.name + " | " + p.type + " | " + p.gameVersion + " | " + p.loader + (p.template ? " | template" : "") + (p.launchable ? "" : " | migration pending"));
                break;
            case "create":
                LauncherActions.ProfileInfo created = services.createProfile(at(args, 2, "Name required"), at(args, 3, "Game version required"), at(args, 4, "Loader required"), normalizeType(option(args, "--type", "mods")), args.contains("--template"));
                output.accept("Created " + created.id + " in " + created.directory); break;
            case "clone":
                String id = at(args, 2, "Source id required"), version = at(args, 3, "Target game version required");
                Profile source = services.getProfiles().get(id);
                LauncherActions.ProfileInfo cloned = services.cloneProfile(id, option(args, "--name", source.name + " " + version), version, option(args, "--loader", source.loader));
                output.accept("Cloned " + cloned.id + "\n" + cloned.migrationSummary);
                if (!cloned.launchable && args.contains("--yes")) output.accept(services.updateProfile(cloned.id));
                else if (!cloned.launchable) output.accept("Review this plan and use .update " + source.type.collectionDirectory() + " --profile " + cloned.id + " --yes to finish migration.");
                break;
            case "delete":
                if (!args.contains("--yes")) throw new IOException("Repeat with --yes to move this profile into recoverable trash");
                services.deleteProfile(at(args, 2, "Profile id required")); output.accept("Profile moved to trash."); break;
            case "template": services.setTemplate(at(args, 2, "Profile id required"), !args.contains("--off")); output.accept("Template updated."); break;
            case "add":
                services.addArtifact(at(args, 2, "Profile id required"), Paths.get(at(args, 3, "JAR path required")), option(args, "--modrinth", null));
                output.accept("Artifact added to profile collection."); break;
            default: throw new IOException(help());
        }
    }
    private void minecraft(List<String> args) throws Exception {
        String verb = at(args, 1, help());
        switch (verb) {
            case "launch":
                String host = option(args, "--server", null); int port = Integer.parseInt(option(args, "--port", "25565"));
                if (host != null) { ServerAddress address = ServerAddress.parse(host); host = address.host; if (!args.contains("--port")) port = address.port; }
                services.launchProfile(at(args, 2, "Client profile id required"), host, port); break;
            case "servers":
                String action = at(args, 2, "Use .mc servers list, import, or add <name> <host[:port]>");
                if (action.equals("list")) {
                    services.getServers().importVanilla();
                    for (SavedServer s : services.getServers().list()) {
                        com.osiris.autoplug.client.browser.ServerStatus ping = services.getServers().ping(s);
                        output.accept(s.name + " | " + s.address + " | " + (ping.online ? ping.version + " | " + ping.players + "/" + ping.capacity + " | " + ping.latency + " ms | " + ping.motd : ping.message));
                    }
                }
                else if (action.equals("import")) output.accept("Imported " + services.getServers().importVanilla() + " servers.");
                else if (action.equals("add")) services.getServers().add(at(args, 3, "Name required"), at(args, 4, "Address required"));
                else throw new IOException("Unknown server action"); break;
            case "worlds": worlds(args); break;
            case "account":
                if (at(args, 2, "Use offline <name> or microsoft <registered-client-id>").equals("offline")) services.useOfflineAccount(at(args, 3, "Offline name required"));
                else if (args.get(2).equals("microsoft")) {
                    LauncherActions.SettingsInfo settings = services.settings(); settings.microsoftClientId = at(args, 3, "Registered Microsoft application client ID required");
                    settings.rememberAccount = args.contains("--remember"); services.saveSettings(settings); services.signInMicrosoft(output);
                } else throw new IOException("Unknown account action");
                break;
            default: throw new IOException(help());
        }
    }
    private void worlds(List<String> args) throws Exception {
        String action = at(args, 2, "Use .mc worlds list/create/launch/share/stop");
        switch (action) {
            case "list": for (LauncherActions.WorldInfo w : services.worlds()) output.accept(w.id + " | " + w.name + " | "
                    + (w.local ? "singleplayer | " + (w.gameVersion.isEmpty() ? "version unknown" : w.gameVersion) : "managed server")
                    + " | " + (w.running ? "running" : "stopped")); break;
            case "create":
                LauncherActions.WorldInfo w = services.createWorld(at(args, 3, "Name required"), at(args, 4, "Server profile id required"), at(args, 5, "Client profile id required"));
                if (args.contains("--accept-eula")) services.setWorldEulaAccepted(w.id, true);
                output.accept("Created world " + w.id + (args.contains("--accept-eula") ? "" : "; EULA acceptance required before launch (https://aka.ms/MinecraftEULA).")); break;
            case "launch":
                String id = at(args, 3, "World id required");
                LauncherActions.WorldInfo selected = world(id);
                if (selected.local) {
                    if (args.contains("--share") || args.contains("--accept-eula"))
                        throw new IOException("Singleplayer saves do not use AutoPlug server sharing or server EULA flags.");
                    String version = option(args, "--version", selected.gameVersion);
                    require(version, "This save has no recorded version. Use --version <original Minecraft version>; no upgrade is selected automatically.");
                    output.accept(services.launchLocalWorld(id, version));
                } else {
                    if (args.contains("--version")) throw new IOException("Managed worlds use their attached profiles; --version is for singleplayer saves.");
                    if (args.contains("--accept-eula")) services.setWorldEulaAccepted(id, true);
                    services.launchWorld(id, args.contains("--share"));
                }
                break;
            case "share": output.accept(services.shareWorld(managedWorld(at(args, 3, "World id required")).id)); break;
            case "stop": services.getWorldService().stopWorld(managedWorld(at(args, 3, "World id required")).id); break;
            default: throw new IOException("Unknown world action");
        }
    }
    private LauncherActions.WorldInfo world(String id) throws Exception {
        for (LauncherActions.WorldInfo world : services.worlds()) {
            if (world.id.equals(id)) return world;
            // Owning a save gives its visible entry a persistent world-UUID, but
            // the documented local:<folder> CLI alias continues to identify it.
            if (world.local && id.startsWith("local:") && id.equals("local:" + Paths.get(world.directory).getFileName())) return world;
        }
        throw new IOException("World not found: " + id);
    }
    private LauncherActions.WorldInfo managedWorld(String id) throws Exception {
        LauncherActions.WorldInfo world = world(id);
        if (world.local) throw new IOException("This is a singleplayer save. Use Minecraft's menu to leave it or open it to LAN.");
        return world;
    }
    public String help() {
        return ".profiles list/create/clone/delete/template/add; .check|.update mods|plugins --profile <id>; "
                + ".mc launch <profile> [--server host[:port]]; .mc servers list/import/add; "
                + ".mc worlds list/create/launch/share/stop; .mc account offline|microsoft";
    }
    private String normalizeType(String value) { return value.toUpperCase(Locale.ROOT).replace('-', '_'); }
    private String option(List<String> args, String option, String fallback) throws IOException {
        int index = args.indexOf(option); return index < 0 ? fallback : at(args, index + 1, option + " requires a value");
    }
    private String at(List<String> args, int index, String message) throws IOException { if (index >= args.size() || args.get(index).startsWith("--")) throw new IOException(message); return args.get(index); }
    private void require(String value, String message) throws IOException { if (value == null || value.isEmpty()) throw new IOException(message); }
    /** Quoting for interactive console input; argv passed to main needs no re-tokenization. */
    public List<String> tokenize(String input) throws IOException {
        List<String> result = new ArrayList<>(); StringBuilder token = new StringBuilder(); char quote = 0; boolean started = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quote != 0) { if (c == quote) quote = 0; else token.append(c); started = true; }
            else if (c == '"' || c == '\'') { quote = c; started = true; }
            else if (Character.isWhitespace(c)) { if (started) { result.add(token.toString()); token.setLength(0); started = false; } }
            else { token.append(c); started = true; }
        }
        if (quote != 0) throw new IOException("Unclosed quote"); if (started) result.add(token.toString()); return result;
    }
}
