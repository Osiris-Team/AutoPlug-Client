package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** A prepared installation. Command arguments are a list, never a shell command. */
public final class LaunchPlan {
    private final JsonObject metadata;
    private final Path directory, natives, assets, libraries, client;
    private final List<Path> classpath;
    private final LaunchAccount account;
    private final LauncherPlatform platform;
    private final String gameVersion;
    private final Path loggingConfig;
    LaunchPlan(JsonObject metadata, String gameVersion, Path directory, Path natives, Path assets,
               Path libraries, Path client, List<Path> classpath, LaunchAccount account,
               LauncherPlatform platform, Path loggingConfig) {
        this.metadata = metadata.deepCopy(); this.gameVersion = gameVersion; this.directory = directory;
        this.natives = natives; this.assets = assets; this.libraries = libraries; this.client = client;
        this.classpath = Collections.unmodifiableList(new ArrayList<>(classpath));
        this.account = Objects.requireNonNull(account); this.platform = platform; this.loggingConfig = loggingConfig;
    }
    public int getJavaMajor() {
        return metadata.has("javaVersion") ? metadata.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
    }
    public Path getGameDirectory() { return directory; }
    public String getVersion() { return metadata.get("id").getAsString(); }
    public String getGameVersion() { return gameVersion; }
    public List<Path> getClasspath() { return classpath; }
    public List<String> buildCommand(Path javaExecutable, String host, int port) {
        if (host != null && (host.isEmpty() || host.indexOf('\0') >= 0 || host.indexOf('\n') >= 0 || host.startsWith("-")))
            throw new IllegalArgumentException("Invalid server host");
        if (host != null && (port < 1 || port > 65535)) throw new IllegalArgumentException("Invalid server port");
        Map<String, String> values = new HashMap<>();
        String cp = String.join(File.pathSeparator, pathStrings(classpath));
        values.put("auth_player_name", account.getName()); values.put("auth_uuid", account.getUuid());
        values.put("auth_access_token", account.getAccessToken()); values.put("auth_xuid", account.getXuid());
        values.put("clientid", account.getClientId()); values.put("user_type", account.getUserType());
        values.put("auth_session", "token:" + account.getAccessToken() + ":" + account.getUuid());
        values.put("user_properties", "{}"); values.put("profile_name", account.getName());
        values.put("version_name", getVersion()); values.put("version_type", NativeLauncher.string(metadata, "type", "release"));
        values.put("game_directory", directory.toString()); values.put("assets_root", assets.toString());
        String assetId = metadata.has("assetIndex") ? metadata.getAsJsonObject("assetIndex").get("id").getAsString()
                : NativeLauncher.string(metadata, "assets", "legacy");
        values.put("assets_index_name", assetId);
        values.put("game_assets", assets.resolve("virtual").resolve(assetId).toString());
        values.put("natives_directory", natives.toString()); values.put("library_directory", libraries.toString());
        values.put("classpath_separator", File.pathSeparator); values.put("classpath", cp);
        values.put("launcher_name", "AutoPlug"); values.put("launcher_version", "10.2.0");
        values.put("resolution_width", "854"); values.put("resolution_height", "480");
        values.put("quickPlayPath", directory.resolve("autoplug-quick-play.json").toString());
        List<String> command = new ArrayList<>(); command.add(javaExecutable.toAbsolutePath().toString());
        command.add("-Xmx2G");
        JsonObject arguments = metadata.has("arguments") ? metadata.getAsJsonObject("arguments") : null;
        List<String> jvm = arguments != null && arguments.has("jvm")
                ? readArguments(arguments.getAsJsonArray("jvm"), platform, Collections.emptyMap()) : new ArrayList<>();
        if (jvm.isEmpty()) { jvm.add("-Djava.library.path=${natives_directory}"); jvm.add("-cp"); jvm.add("${classpath}"); }
        for (String argument : jvm) command.add(substitute(argument, values));
        if (loggingConfig != null && metadata.has("logging")) {
            String argument = metadata.getAsJsonObject("logging").getAsJsonObject("client").get("argument").getAsString();
            command.add(substitute(argument.replace("${path}", loggingConfig.toString()), values));
        }
        command.add(metadata.get("mainClass").getAsString());
        List<String> game = arguments != null && arguments.has("game")
                ? readArguments(arguments.getAsJsonArray("game"), platform, Collections.emptyMap())
                : splitLegacy(NativeLauncher.string(metadata, "minecraftArguments", ""));
        for (String argument : game) command.add(substitute(argument, values));
        if (host != null) {
            // Mojang removed --server/--port in 23w14a (released in 1.20); use its replacement.
            boolean metadataQuickPlay = arguments != null && arguments.has("game")
                    && arguments.getAsJsonArray("game").toString().contains("--quickPlayMultiplayer");
            if (metadataQuickPlay || supportsQuickPlayOnly(gameVersion)) {
                command.add("--quickPlayMultiplayer");
                command.add((host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host) + ":" + port);
            } else { command.add("--server"); command.add(host); command.add("--port"); command.add(Integer.toString(port)); }
        }
        return Collections.unmodifiableList(command);
    }
    public Process launch(Path javaExecutable, String host, int port) throws IOException {
        LauncherDownloads.checkInterrupted();
        LauncherDownloads.rejectLinks(directory);
        if (!Files.isRegularFile(javaExecutable)) throw new IOException("Selected Java executable does not exist");
        ProcessBuilder builder = new ProcessBuilder(buildCommand(javaExecutable, host, port));
        builder.directory(directory.toFile()); builder.redirectErrorStream(true);
        // Never persist the command or tokens. Inherit output rather than leaving an unread pipe that can deadlock.
        builder.inheritIO(); return builder.start();
    }
    private static List<String> pathStrings(List<Path> paths) { List<String> list = new ArrayList<>(); for (Path p : paths) list.add(p.toString()); return list; }
    static boolean supportsQuickPlayOnly(String version) {
        String[] pieces = version.split("\\.");
        try { return pieces.length >= 2 && (Integer.parseInt(pieces[0]) > 1 || Integer.parseInt(pieces[1]) >= 20); }
        catch (NumberFormatException e) { return false; }
    }
    static List<String> readArguments(JsonArray array, LauncherPlatform platform, Map<String, Boolean> features) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) result.add(element.getAsString());
            else {
                JsonObject conditional = element.getAsJsonObject(); if (!platform.allows(conditional, features)) continue;
                JsonElement value = conditional.get("value");
                if (value.isJsonArray()) for (JsonElement item : value.getAsJsonArray()) result.add(item.getAsString());
                else result.add(value.getAsString());
            }
        }
        return result;
    }
    static String substitute(String text, Map<String, String> values) {
        Matcher matcher = Pattern.compile("\\$\\{([^}]+)}").matcher(text); StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) throw new IllegalArgumentException("Unsupported launcher variable: " + matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result); return result.toString();
    }
    static List<String> splitLegacy(String text) {
        List<String> result = new ArrayList<>(); StringBuilder token = new StringBuilder(); char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) { if (c == quote) quote = 0; else token.append(c); }
            else if (c == '\'' || c == '"') quote = c;
            else if (Character.isWhitespace(c)) { if (token.length() > 0) { result.add(token.toString()); token.setLength(0); } }
            else token.append(c);
        }
        if (quote != 0) throw new IllegalArgumentException("Unterminated legacy argument quote");
        if (token.length() > 0) result.add(token.toString()); return result;
    }
}
