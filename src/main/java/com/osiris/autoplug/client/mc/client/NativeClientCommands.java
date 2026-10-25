package com.osiris.autoplug.client.mc.client;

import com.osiris.autoplug.client.mc.launch.Cancellation;
import com.osiris.autoplug.client.mc.launch.MicrosoftDeviceCodeAuth;
import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileResolver;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import com.osiris.autoplug.client.mc.update.ProfileUpdateService;
import com.osiris.autoplug.client.mc.worlds.World;
import com.osiris.autoplug.client.mc.worlds.WorldSessionManager;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** CLI and desktop use the same context, repositories, update engine and process registry. */
public final class NativeClientCommands implements AutoCloseable {
    private final NativeClientContext context;
    private volatile Cancellation signInCancellation;
    private volatile Thread signInThread;
    private volatile boolean closed;

    public NativeClientCommands(NativeClientContext context) {
        this.context = java.util.Objects.requireNonNull(context);
    }

    /** Tokenizes the existing console's command text, preserving Windows paths and quoted spaces. */
    public static String[] tokenize(String command) {
        if (command == null) return new String[0];
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean active = false;
        for (int i = 0; i < command.length(); i++) {
            char character = command.charAt(i);
            if (character == '\\' && i + 1 < command.length()) {
                char next = command.charAt(i + 1);
                if (next == quote || (quote == 0 && (Character.isWhitespace(next) || next == '\'' || next == '"'))) {
                    token.append(next); active = true; i++; continue;
                }
            }
            if ((character == '\'' || character == '"') && (quote == 0 || quote == character)) {
                quote = quote == 0 ? character : 0; active = true;
            } else if (Character.isWhitespace(character) && quote == 0) {
                if (active) { tokens.add(token.toString()); token.setLength(0); active = false; }
            } else { token.append(character); active = true; }
        }
        if (quote != 0) throw new IllegalArgumentException("Unclosed command quote");
        if (active) tokens.add(token.toString());
        return tokens.toArray(new String[0]);
    }

    /** Returns false for legacy/non-native commands, which the existing dispatcher must handle. */
    public boolean dispatch(String[] arguments, PrintStream out) throws Exception {
        if (closed) throw new IOException("Native client commands are closed");
        if (arguments == null || arguments.length == 0) return false;
        String[] args = Arrays.stream(arguments).map(this::unquote).toArray(String[]::new);
        String command = args[0].startsWith(".") ? args[0].substring(1) : args[0];
        if (command.equals("profiles")) { profiles(args, out); return true; }
        if (command.equals("mc")) { minecraft(args, out); return true; }
        if ((command.equals("check") || command.equals("update")) && Arrays.stream(args).anyMatch(value -> value.equals("--profile") || value.startsWith("--profile="))) {
            update(args, out, command.equals("update")); return true;
        }
        return false;
    }

    private void profiles(String[] args, PrintStream out) throws Exception {
        if (args.length < 2 || args[1].equals("help") || args[1].equals("--help")) {
            out.println(".profiles list [--type MODS|PLUGINS|MODS-SERVER]");
            out.println(".profiles create <id> <gameVersion> <loader> [--type TYPE] [--template]");
            out.println(".profiles clone <source> <newId> [--version target] [--confirm-disable]");
            out.println(".profiles rename <id> <newId> | delete <id> | template <id> <true|false>"); return;
        }
        Options options = new Options(args, 2, "template", "confirm-disable");
        switch (args[1]) {
            case "list":
                options.allow("type"); options.count(0);
                ProfileType filter = options.has("type") ? type(options.value("type")) : null;
                for (Profile profile : context.profiles().list()) if (filter == null || filter == profile.getType()) print(profile, out);
                return;
            case "create":
                options.allow("type", "template"); options.count(3);
                print(context.profiles().create(options.position(0), options.has("type") ? type(options.value("type")) : ProfileType.MODS,
                        options.position(1), options.position(2), options.has("template")), out); return;
            case "clone":
                options.allow("version", "confirm-disable"); options.count(2);
                if (options.has("version")) {
                    ProfileUpdateService.Plan plan = context.updater().planClone(options.position(0), options.position(1), options.value("version"));
                    apply(plan, options.has("confirm-disable"), out);
                } else print(context.profiles().cloneProfile(options.position(0), options.position(1)), out);
                return;
            case "rename":
                options.allow(); options.count(2); requireUnreferenced(options.position(0));
                Profile renamed = context.profiles().rename(options.position(0), options.position(1));
                if (options.position(0).equals(context.settings().get("defaultTemplate", ""))) {
                    context.settings().set("defaultTemplate", renamed.getId()); context.settings().save();
                }
                print(renamed, out); return;
            case "delete":
                options.allow(); options.count(1); requireUnreferenced(options.position(0));
                context.profiles().delete(options.position(0));
                if (options.position(0).equals(context.settings().get("defaultTemplate", ""))) {
                    context.settings().set("defaultTemplate", ""); context.settings().save();
                }
                out.println("Deleted profile " + options.position(0)); return;
            case "template":
                options.allow(); options.count(2);
                if (parseBoolean(options.position(1))) requireUnreferenced(options.position(0));
                print(context.profiles().updateTemplate(options.position(0), parseBoolean(options.position(1))), out); return;
            default: throw usage("Unknown .profiles operation; use .profiles help");
        }
    }

    private void update(String[] args, PrintStream out, boolean apply) throws Exception {
        Options options = new Options(args, 1, "confirm-disable");
        options.allow("profile", "version", "confirm-disable"); options.count(1);
        if (!options.has("profile")) throw usage("Specify --profile <id>");
        Profile profile = context.profiles().require(options.value("profile"));
        boolean mods = options.position(0).equals("mods");
        boolean plugins = options.position(0).equals("plugins");
        if ((!mods && !plugins) || plugins != (profile.getType() == ProfileType.PLUGINS)) {
            throw usage("Use mods for MODS/MODS_SERVER and plugins for PLUGINS");
        }
        ProfileUpdateService.Plan plan = context.updater().check(profile.getId(), options.has("version") ? options.value("version") : profile.getGameVersion());
        if (apply) apply(plan, options.has("confirm-disable"), out);
        else out.println(plan.getSummary());
    }

    private void apply(ProfileUpdateService.Plan plan, boolean confirmed, PrintStream out) throws IOException {
        out.println(plan.getSummary());
        if (!plan.canApply()) throw new IOException("Resolve the reported check errors before applying changes");
        if (!plan.getDisabledAssets().isEmpty() && !confirmed) {
            out.println("Nothing changed. Review the summary, then repeat with --confirm-disable to approve disabling these files."); return;
        }
        print(context.updater().apply(plan, confirmed), out);
    }

    private void minecraft(String[] args, PrintStream out) throws Exception {
        if (args.length < 2 || args[1].equals("help") || args[1].equals("--help")) {
            out.println(".mc servers list|add|import|ping|join; .mc worlds list|create|launch|stop|share");
            out.println(".mc launch <profile> [--server host:port] | stop <profile>");
            out.println(".mc signin [--client-id registeredId] | device login | signin cancel | signout");
            out.println(".mc loader <profile> [loaderVersion] | settings show|set <key> <value>");
            out.println(".mc offline <name> enables local fixture mode only; no Microsoft authentication."); return;
        }
        if (args[1].equals("servers")) { servers(args, out); return; }
        if (args[1].equals("worlds")) { worlds(args, out); return; }
        Options options = new Options(args, 2);
        switch (args[1]) {
            case "launch":
                options.allow("server"); options.count(1);
                context.launch(options.position(0), options.has("server") ? options.value("server") : null, new Cancellation());
                out.println("Minecraft client started for " + options.position(0)); return;
            case "stop":
                options.allow(); options.count(1); context.stopClient(options.position(0)); out.println("Client stop requested for " + options.position(0)); return;
            case "signin": case "login": case "device":
                options.allow("client-id");
                if (args[1].equals("device")) {
                    if (options.positions.isEmpty() || (!options.position(0).equals("login") && !options.position(0).equals("cancel"))) throw usage(".mc device login [--client-id registeredId] | .mc device cancel");
                    if (options.position(0).equals("login")) options.positions.remove(0);
                }
                if (options.positions.size() == 1 && options.position(0).equals("cancel")) { cancelSignIn(); out.println("Sign-in cancellation requested"); return; }
                options.count(0);
                if (options.has("client-id")) { context.settings().set("clientId", options.value("client-id")); context.settings().save(); }
                beginSignIn(out); return;
            case "signout": case "logout":
                options.allow(); options.count(0); cancelSignIn(); context.signOut(); out.println("Signed out; no token file was created"); return;
            case "offline":
                options.allow(); options.count(1); context.launcher().offlineSession(options.position(0));
                context.settings().set("username", options.position(0)); context.settings().set("offlineFixtureMode", "true"); context.settings().save();
                out.println("Offline fixture mode enabled for local test servers; this is not Microsoft authentication"); return;
            case "loader":
                options.allow();
                if (options.positions.size() < 1 || options.positions.size() > 2) throw usage(".mc loader <profile> [loaderVersion]");
                String installed = context.installLoader(options.position(0), options.positions.size() == 2 ? options.position(1) : null, new Cancellation());
                out.println("Installed loader version " + installed + " for " + options.position(0)); return;
            case "settings":
                options.allow();
                if (options.positions.size() == 1 && options.position(0).equals("show")) {
                    for (String key : new String[]{"clientId", "javaHomes", "defaultTemplate", "username", "offlineFixtureMode", "cacheDirectory"}) {
                        out.println(key + "=" + context.settings().get(key, ""));
                    }
                } else {
                    options.count(3);
                    if (!options.position(0).equals("set")) throw usage(".mc settings show | set <key> <value>");
                    context.settings().set(options.position(1), options.position(2)); context.settings().save();
                    out.println("Saved non-secret setting " + options.position(1) + "; changing cacheDirectory takes effect after restart");
                }
                return;
            default: throw usage("Unknown .mc operation; use .mc help");
        }
    }

    private void servers(String[] args, PrintStream out) throws Exception {
        if (args.length < 3) throw usage(".mc servers list | add <name> <address> <loader> | import <servers.dat> <loader> | ping <id|address> | join <id|address> [--profile id]");
        Options options = new Options(args, 3, "confirm-disable");
        switch (args[2]) {
            case "list":
                options.allow(); options.count(0);
                for (SavedServerRepository.SavedServer server : context.servers().list()) out.println(server.getId() + " | " + server + " | " + server.getLoader()); return;
            case "add":
                options.allow(); options.count(3); out.println(context.servers().add(options.position(0), options.position(1), options.position(2))); return;
            case "import":
                options.allow(); options.count(2); out.println("Imported " + context.servers().importVanilla(Paths.get(options.position(0)), options.position(1)) + " servers; servers.dat unchanged"); return;
            case "ping":
                options.allow(); options.count(1); out.println(context.ping(serverAddress(options.position(0)))); return;
            case "join":
                options.allow("profile", "version", "loader", "clone", "confirm-disable"); options.count(1); join(options, out); return;
            default: throw usage("Unknown .mc servers operation");
        }
    }

    private void join(Options options, PrintStream out) throws Exception {
        String requested = options.position(0);
        String address = serverAddress(requested);
        Cancellation cancellation = new Cancellation();
        if (options.has("profile")) {
            if (options.has("clone")) throw usage("Choose --profile or --clone, not both");
            Profile profile = requireClientProfile(options.value("profile"));
            if (options.has("loader") && !profile.getLoader().equalsIgnoreCase(options.value("loader"))) throw usage("Selected profile does not match --loader");
            if (options.has("version") && !profile.getGameVersion().equals(options.value("version"))) throw usage("Selected profile does not match --version");
            context.ensureLoader(profile.getId(), cancellation);
            context.launch(profile.getId(), address, cancellation); out.println("Minecraft client started"); return;
        }
        String loader = options.has("loader") ? options.value("loader") : null;
        for (SavedServerRepository.SavedServer entry : context.servers().list()) if (entry.getId().equals(requested)) loader = options.has("loader") ? loader : entry.getLoader();
        if (loader == null) throw usage("Supply --loader explicitly, or use a saved server with an explicit loader");
        String version = options.has("version") ? options.value("version") : pingVersion(address);
        ProfileResolver.Resolution resolution = new ProfileResolver(context.profiles()).resolve(version, loader, context.settings().get("defaultTemplate", "").isEmpty() ? null : context.settings().get("defaultTemplate", ""));
        switch (resolution.getState()) {
            case READY:
                Profile selected = requireClientProfile(resolution.getProfiles().get(0).getId());
                context.ensureLoader(selected.getId(), cancellation);
                context.launch(selected.getId(), address, cancellation); out.println("Minecraft client started"); return;
            case CHOOSE_PROFILE:
                out.println("Several profiles match; repeat join with --profile <id>:");
                for (Profile profile : resolution.getProfiles()) print(profile, out); return;
            case CONFIRM_TEMPLATE_CLONE:
                if (!options.has("clone")) { out.println("Template clone and migration require approval; repeat with --clone <newProfileId> after reviewing the template."); return; }
                ProfileUpdateService.Plan plan = context.updater().planClone(resolution.getProfiles().get(0).getId(), options.value("clone"), version);
                out.println(plan.getSummary());
                if (!plan.canApply()) throw new IOException("Resolve migration errors before joining");
                if (!plan.getDisabledAssets().isEmpty() && !options.has("confirm-disable")) { out.println("Nothing changed; review the disabled-file summary and repeat with --confirm-disable to approve."); return; }
                Profile clone = context.updater().apply(plan, options.has("confirm-disable"));
                print(clone, out);
                requireClientProfile(clone.getId());
                context.ensureLoader(clone.getId(), cancellation);
                context.launch(clone.getId(), address, cancellation);
                out.println("Minecraft client started with cloned profile " + clone.getId()); return;
            default: out.println("No compatible profile; create one for Minecraft " + version + " / " + loader + " before joining.");
        }
    }

    private Profile requireClientProfile(String id) throws IOException {
        Profile profile = context.profiles().require(id);
        if (profile.getType() != ProfileType.MODS || profile.isTemplate()) throw new IOException("Select a non-template client profile");
        if (!Arrays.asList("VANILLA", "FABRIC", "QUILT", "FORGE", "NEOFORGE").contains(profile.getLoader().toUpperCase(Locale.ROOT))) {
            throw new IOException("Unsupported client loader: " + profile.getLoader());
        }
        return profile;
    }

    private String pingVersion(String address) throws IOException {
        NativeClientContext.ServerStatus status = context.ping(address);
        if (!status.isOnline()) throw new IOException("Server status unavailable; specify --version and --loader explicitly");
        if (status.getVersion() == null) throw new IOException("Server version unavailable; specify --version explicitly");
        Matcher match = Pattern.compile("(?<![0-9.])((?:1|[2-9][0-9])\\.[0-9]+(?:\\.[0-9]+)?)(?![0-9.])").matcher(status.getVersion());
        if (!match.find()) throw new IOException("Server reports an ambiguous version; specify --version explicitly");
        String selected = match.group(1);
        while (match.find()) if (!selected.equals(match.group(1))) throw new IOException("Server advertises several versions; specify --version explicitly");
        return selected;
    }

    private String serverAddress(String value) throws IOException {
        for (SavedServerRepository.SavedServer server : context.servers().list()) if (server.getId().equals(value)) return server.getAddress();
        return new ServerAddress(value).toString();
    }

    private void worlds(String[] args, PrintStream out) throws Exception {
        if (args.length < 3) throw usage(".mc worlds list | create <worldId> <serverProfile> | configure <worldId> --client <profile> --server-jar <path> | launch <worldId> [--share] | stop <worldId> | share <worldId>");
        Options options = new Options(args, 3, "share");
        switch (args[2]) {
            case "list":
                options.allow(); options.count(0);
                for (World world : context.worlds().list()) out.println(world.getId() + " | " + world.getProfileId() + " | " + world.getDirectory()); return;
            case "create":
                options.allow(); options.count(2); World world = context.worlds().create(options.position(0), options.position(1));
                out.println("Created world " + world.getId() + "; review Minecraft's EULA and set eula=true yourself before starting its server"); return;
            case "launch":
                options.allow("client", "server-jar", "share"); options.count(1);
                if (options.has("client") != options.has("server-jar")) throw usage("Supply both --client and --server-jar, or use the saved world launch settings");
                if (options.has("client")) context.configureWorld(options.position(0), options.value("client"), Paths.get(options.value("server-jar")));
                WorldSessionManager.Session session = context.playWorld(options.position(0), options.has("share"), new Cancellation());
                out.println("World started; " + session.getShareMessage()); return;
            case "configure":
                options.allow("client", "server-jar"); options.count(1);
                if (!options.has("client") || !options.has("server-jar")) throw usage("World configuration requires --client <profile> and --server-jar <officialJar>");
                context.configureWorld(options.position(0), options.value("client"), Paths.get(options.value("server-jar")));
                out.println("Saved world launch settings; no server started and no EULA was accepted"); return;
            case "stop":
                options.allow(); options.count(1); context.stopWorld(options.position(0)); out.println("World stop requested"); return;
            case "share":
                options.allow(); options.count(1); WorldSessionManager.Session active = context.activeWorld(options.position(0));
                if (active == null) throw new IOException("Start the world before explicitly sharing it");
                out.println(active.share()); return;
            default: throw usage("Unknown .mc worlds operation");
        }
    }

    private synchronized void beginSignIn(PrintStream out) {
        if (closed) throw new IllegalStateException("Native client commands are closed");
        if (signInThread != null && signInThread.isAlive()) throw usage("Sign-in already pending; use .mc signin cancel first");
        MicrosoftDeviceCodeAuth authenticator = context.authenticator(); // Validate configuration before starting a worker.
        Cancellation cancellation = new Cancellation(); signInCancellation = cancellation;
        signInThread = new Thread(() -> {
            try {
                MicrosoftDeviceCodeAuth.DeviceAuthorization device = authenticator.begin(cancellation);
                out.println("Open " + device.getVerificationUri() + " and enter " + device.getUserCode() + "; expires " + device.getExpiresAt());
                MicrosoftDeviceCodeAuth.AuthResult result = authenticator.poll(device, cancellation);
                cancellation.check(); context.signedIn(result);
                context.settings().set("offlineFixtureMode", "false"); context.settings().save();
                out.println("Signed in as " + result.getSession().getUsername() + "; tokens remain in memory only");
            } catch (Exception error) {
                // Provider bodies/exception chains can contain tokens: never print them.
                out.println(cancellation.isCancelled() ? "Sign-in cancelled" : "Sign-in failed; verify client ID, application access and account eligibility, then retry");
            }
        }, "AutoPlug-Microsoft-signin");
        signInThread.setDaemon(true); signInThread.start();
    }

    private void cancelSignIn() { Cancellation cancellation = signInCancellation; if (cancellation != null) cancellation.cancel(); }

    @Override public synchronized void close() {
        closed = true; cancelSignIn();
        Thread worker = signInThread;
        if (worker != null && worker != Thread.currentThread()) {
            worker.interrupt();
            try { worker.join(2000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }
    }

    private void requireUnreferenced(String id) throws IOException {
        for (World world : context.worlds().list()) {
            if (world.getProfileId().equals(id)) throw new IOException("Profile is referenced by world " + world.getId() + "; preserve that reference before renaming/deleting it");
            java.util.Properties config = new ClientStorage().read(world.getDirectory().resolve("world-launch.properties"));
            if (!config.isEmpty() && !"1".equals(config.getProperty("formatVersion"))) throw new IOException("Invalid launch settings for world " + world.getId());
            if (id.equals(config.getProperty("clientProfileId"))) throw new IOException("Profile is the configured client for world " + world.getId() + "; change its launch settings before renaming/deleting it");
        }
    }

    private ProfileType type(String value) {
        try { return ProfileType.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_')); }
        catch (IllegalArgumentException error) { throw usage("Type must be MODS, PLUGINS or MODS-SERVER"); }
    }
    private boolean parseBoolean(String value) {
        if (!value.equals("true") && !value.equals("false")) throw usage("Use true or false explicitly"); return Boolean.parseBoolean(value);
    }
    private void print(Profile profile, PrintStream out) {
        out.println(profile.getId() + " | " + profile.getType() + " | " + profile.getGameVersion() + " | " + profile.getLoader() + (profile.isTemplate() ? " | template" : ""));
    }
    private String unquote(String value) {
        if (value != null && value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1);
        return value;
    }
    private IllegalArgumentException usage(String message) { return new IllegalArgumentException(message); }

    private final class Options {
        private final List<String> positions = new ArrayList<>();
        private final Map<String, String> values = new HashMap<>();
        Options(String[] args, int start, String... booleanFlags) {
            Set<String> flags = new HashSet<>(Arrays.asList(booleanFlags));
            for (int i = start; i < args.length; i++) {
                String argument = args[i];
                if (!argument.startsWith("--")) { positions.add(argument); continue; }
                String key = argument.substring(2);
                int equals = key.indexOf('=');
                if (equals >= 0) {
                    String value = key.substring(equals + 1); key = key.substring(0, equals);
                    if (value.isEmpty() || flags.contains(key)) throw usage("Invalid value for --" + key);
                    if (values.putIfAbsent(key, value) != null) throw usage("Duplicate option --" + key);
                    continue;
                }
                if (values.containsKey(key)) throw usage("Duplicate option --" + key);
                if (flags.contains(key)) values.put(key, "true");
                else {
                    if (i + 1 == args.length || args[i + 1].startsWith("--")) throw usage("Missing value for --" + key);
                    values.put(key, args[++i]);
                }
            }
        }
        void allow(String... keys) {
            Set<String> permitted = new HashSet<>(Arrays.asList(keys));
            for (String key : values.keySet()) if (!permitted.contains(key)) throw usage("Unknown option --" + key);
        }
        void count(int expected) { if (positions.size() != expected) throw usage("Expected " + expected + " positional arguments; use .mc help or .profiles help"); }
        boolean has(String key) { return values.containsKey(key); }
        String value(String key) { return values.get(key); }
        String position(int index) { return positions.get(index); }
    }
}
