package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Native Mojang launch planning; only PreparedLaunch.start() can start a process. */
public final class MinecraftLauncher {
    private final ArtifactCache cache;
    private final MojangMetadata metadata;
    private final LaunchPlatform platform;
    private final JavaRuntimeSelector runtimes;

    public MinecraftLauncher(ArtifactCache cache, MojangMetadata metadata, LaunchPlatform platform, JavaRuntimeSelector runtimes) {
        this.cache = cache;
        this.metadata = metadata;
        this.platform = platform;
        this.runtimes = runtimes;
    }

    public MinecraftSession offlineSession(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid offline player name");
        String id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
        return new MinecraftSession(username, id, "0", "legacy");
    }

    public PreparedLaunch prepare(String versionId, Path gameDirectory, MinecraftSession session,
                                  String serverHost, int port, Cancellation cancellation) throws IOException {
        cancellation.check();
        if (session == null) throw new IOException("A Minecraft session is required");
        if (serverHost != null && (serverHost.trim().isEmpty() || serverHost.matches(".*[\\s\\p{Cntrl}].*") || port < 1 || port > 65535)) {
            throw new IOException("Invalid direct-connect server or port");
        }
        Files.createDirectories(gameDirectory);
        Path game = gameDirectory.toRealPath();
        JsonObject version = metadata.version(versionId, cancellation);
        int javaMajor = version.has("javaVersion") ? version.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
        Path java = runtimes.select(javaMajor, platform.getArchitecture());
        Path internal = game.resolve(".autoplug");
        Files.createDirectories(internal);
        Path natives = Files.createTempDirectory(internal, "natives-");
        boolean prepared = false;
        try {
            Set<Path> classpath = libraries(version, natives, cancellation);
            String clientVersion = version.has("autoplugClientVersion") ? version.get("autoplugClientVersion").getAsString() : version.get("id").getAsString();
            JsonObject client = version.getAsJsonObject("downloads").getAsJsonObject("client");
            classpath.add(artifact(client, "versions/" + clientVersion + "/" + clientVersion + ".jar", cancellation));
            String assetId = assets(version, game, cancellation);
            String separator = platform.getName().equals("windows") ? ";" : ":";
            List<String> classpathStrings = new ArrayList<>();
            for (Path path : classpath) classpathStrings.add(path.toString());
            Map<String, String> values = new HashMap<>();
            values.put("auth_player_name", session.getUsername());
            values.put("auth_uuid", session.getUuid());
            values.put("auth_access_token", session.getAccessToken());
            values.put("auth_session", "token:" + session.getAccessToken() + ":" + session.getUuid());
            values.put("auth_xuid", session.getXuid());
            values.put("clientid", session.getClientId());
            values.put("user_type", session.getUserType());
            values.put("user_properties", "{}");
            values.put("version_name", versionId);
            values.put("version_type", version.has("type") ? version.get("type").getAsString() : "custom");
            values.put("game_directory", game.toString());
            values.put("assets_root", cache.resolve("assets").toString());
            values.put("assets_index_name", assetId);
            values.put("game_assets", cache.resolve("assets/virtual/" + assetId).toString());
            values.put("natives_directory", natives.toString());
            values.put("library_directory", cache.resolve("libraries").toString());
            values.put("classpath_separator", separator);
            values.put("classpath", String.join(separator, classpathStrings));
            values.put("launcher_name", "AutoPlug");
            values.put("launcher_version", "10.2.0");
            List<String> command = new ArrayList<>();
            command.add(java.toString());
            if (version.has("arguments")) {
                addArguments(version.getAsJsonObject("arguments").getAsJsonArray("jvm"), values, command);
            } else {
                command.add("-Djava.library.path=" + natives);
                if (platform.getName().equals("osx")) command.add("-XstartOnFirstThread");
            }
            if (command.stream().noneMatch(value -> value.startsWith("-Xmx"))) command.add("-Xmx2G");
            if (!command.contains("-cp") && !command.contains("-classpath") && !command.contains("--class-path")) {
                command.add("-cp"); command.add(values.get("classpath"));
            }
            addLogging(version, command, cancellation);
            command.add(version.get("mainClass").getAsString());
            if (version.has("arguments") && version.getAsJsonObject("arguments").has("game")) {
                addArguments(version.getAsJsonObject("arguments").getAsJsonArray("game"), values, command);
            } else if (version.has("minecraftArguments")) {
                for (String token : splitLegacy(version.get("minecraftArguments").getAsString())) command.add(expand(token, values));
            }
            if (serverHost != null) {
                command.add("--server"); command.add(serverHost);
                command.add("--port"); command.add(Integer.toString(port));
            }
            cancellation.check();
            PreparedLaunch result = new PreparedLaunch(command, game, cancellation, natives);
            prepared = true;
            return result;
        } catch (RuntimeException error) {
            throw new IOException("Invalid Minecraft launch metadata", error);
        } finally {
            if (!prepared) new LaunchFiles().removeTree(natives);
        }
    }

    private Set<Path> libraries(JsonObject version, Path natives, Cancellation cancellation) throws IOException {
        Set<Path> classpath = new LinkedHashSet<>();
        Set<String> nativeGroups = new java.util.HashSet<>();
        Set<String> matchingNativeGroups = new java.util.HashSet<>();
        for (JsonElement entry : version.getAsJsonArray("libraries")) {
            cancellation.check();
            JsonObject library = entry.getAsJsonObject();
            if (!platform.allows(library)) continue;
            String coordinate = library.get("name").getAsString();
            String[] coordinateParts = coordinate.split(":");
            if (coordinateParts.length == 4 && coordinateParts[3].startsWith("natives-")) {
                String group = coordinateParts[0] + ":" + coordinateParts[1] + ":" + coordinateParts[2];
                nativeGroups.add(group);
                if (!matchesNativeArchitecture(coordinateParts[3])) continue;
                matchingNativeGroups.add(group);
            }
            JsonObject downloads = library.has("downloads") ? library.getAsJsonObject("downloads") : new JsonObject();
            if (downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                Path jar = artifact(artifact, "libraries/" + artifact.get("path").getAsString(), cancellation);
                classpath.add(jar);
                if (coordinateParts.length == 4 && coordinateParts[3].startsWith("natives-")) {
                    extractNatives(jar, natives, java.util.Collections.singletonList("META-INF/"), cancellation);
                }
            } else if (!library.has("natives")) {
                String relative = coordinatePath(library.get("name").getAsString());
                String base = library.has("url") ? library.get("url").getAsString() : "https://libraries.minecraft.net/";
                Path jar = cache.obtainWithSidecar("libraries/" + relative,
                        URI.create(base.endsWith("/") ? base + relative : base + "/" + relative), cancellation);
                classpath.add(jar);
            }
            if (library.has("natives") && library.getAsJsonObject("natives").has(platform.getName())) {
                String classifier = library.getAsJsonObject("natives").get(platform.getName()).getAsString()
                        .replace("${arch}", platform.getNativeBits());
                if ((platform.getArchitecture().contains("arm") || platform.getArchitecture().contains("aarch"))
                        && !classifier.contains("arm") && !classifier.contains("aarch")) {
                    throw new IOException("This Minecraft version does not provide " + platform.getArchitecture() + " native libraries for " + platform.getName());
                }
                if (!downloads.has("classifiers") || !downloads.getAsJsonObject("classifiers").has(classifier)) {
                    throw new IOException("No Minecraft native library for " + platform.getName() + "/" + platform.getArchitecture());
                }
                JsonObject descriptor = downloads.getAsJsonObject("classifiers").getAsJsonObject(classifier);
                Path jar = artifact(descriptor, "libraries/" + descriptor.get("path").getAsString(), cancellation);
                List<String> exclusions = new ArrayList<>();
                exclusions.add("META-INF/");
                if (library.has("extract") && library.getAsJsonObject("extract").has("exclude")) {
                    for (JsonElement excluded : library.getAsJsonObject("extract").getAsJsonArray("exclude")) exclusions.add(excluded.getAsString());
                }
                extractNatives(jar, natives, exclusions, cancellation);
            }
        }
        if (!matchingNativeGroups.containsAll(nativeGroups)) throw new IOException("Minecraft native libraries do not support " + platform.getArchitecture());
        return classpath;
    }

    private boolean matchesNativeArchitecture(String classifier) {
        boolean arm = platform.getArchitecture().contains("arm") || platform.getArchitecture().contains("aarch");
        boolean bits64 = platform.getNativeBits().equals("64");
        if (classifier.endsWith("-arm64") || classifier.endsWith("-aarch64")) return arm && bits64;
        if (classifier.endsWith("-arm32")) return arm && !bits64;
        if (classifier.endsWith("-x86")) return !arm && !bits64;
        return !arm && bits64;
    }

    private Path artifact(JsonObject descriptor, String relative, Cancellation cancellation) throws IOException {
        if (!descriptor.has("sha1") || !descriptor.has("url") || descriptor.get("url").getAsString().isEmpty()) {
            Path installed = cache.installedLibrary(relative);
            if (installed != null) return installed;
            throw new IOException("Loader library must be produced by its official installer first: " + relative);
        }
        return cache.obtain(relative, URI.create(descriptor.get("url").getAsString()), descriptor.get("sha1").getAsString(),
                descriptor.has("size") ? descriptor.get("size").getAsLong() : -1, cancellation);
    }

    private String assets(JsonObject version, Path game, Cancellation cancellation) throws IOException {
        if (!version.has("assetIndex")) return version.has("assets") ? version.get("assets").getAsString() : "legacy";
        JsonObject descriptor = version.getAsJsonObject("assetIndex");
        String id = descriptor.get("id").getAsString();
        if (!id.matches("[A-Za-z0-9._-]+")) throw new IOException("Invalid asset index ID");
        Path file = artifact(descriptor, "assets/indexes/" + id + ".json", cancellation);
        JsonObject index = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : index.getAsJsonObject("objects").entrySet()) {
            JsonObject object = entry.getValue().getAsJsonObject();
            String hash = object.get("hash").getAsString();
            if (!hash.matches("[a-f0-9]{40}")) throw new IOException("Invalid asset hash");
            String relative = hash.substring(0, 2) + "/" + hash;
            Path cached = cache.obtain("assets/objects/" + relative, URI.create("https://resources.download.minecraft.net/" + relative),
                    hash, object.get("size").getAsLong(), cancellation);
            if (index.has("virtual") && index.get("virtual").getAsBoolean()) copyAsset(cached, cache.resolve("assets/virtual/" + id), entry.getKey());
            if (index.has("map_to_resources") && index.get("map_to_resources").getAsBoolean()) copyAsset(cached, game.resolve("resources"), entry.getKey());
        }
        return id;
    }

    private void copyAsset(Path source, Path root, String name) throws IOException {
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new IOException("Invalid asset path");
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void addLogging(JsonObject version, List<String> command, Cancellation cancellation) throws IOException {
        if (!version.has("logging") || !version.getAsJsonObject("logging").has("client")) return;
        JsonObject logging = version.getAsJsonObject("logging").getAsJsonObject("client");
        JsonObject file = logging.getAsJsonObject("file");
        Path config = artifact(file, "assets/log_configs/" + file.get("id").getAsString(), cancellation);
        command.add(logging.get("argument").getAsString().replace("${path}", config.toString()));
    }

    private void addArguments(JsonArray arguments, Map<String, String> values, List<String> output) throws IOException {
        if (arguments == null) return;
        for (JsonElement entry : arguments) {
            if (entry.isJsonPrimitive()) output.add(expand(entry.getAsString(), values));
            else {
                JsonObject conditional = entry.getAsJsonObject();
                if (!platform.allows(conditional)) continue;
                JsonElement value = conditional.get("value");
                if (value.isJsonArray()) for (JsonElement token : value.getAsJsonArray()) output.add(expand(token.getAsString(), values));
                else output.add(expand(value.getAsString(), values));
            }
        }
    }

    private String expand(String text, Map<String, String> values) throws IOException {
        Matcher matcher = Pattern.compile("\\$\\{([^}]+)}").matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) throw new IOException("Unsupported launch placeholder: " + matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private List<String> splitLegacy(String text) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        for (char character : text.toCharArray()) {
            if ((character == '\'' || character == '"') && (quote == 0 || quote == character)) {
                quote = quote == 0 ? character : 0;
            } else if (Character.isWhitespace(character) && quote == 0) {
                if (token.length() > 0) { result.add(token.toString()); token.setLength(0); }
            } else token.append(character);
        }
        if (quote != 0) throw new IOException("Unclosed legacy launch argument quote");
        if (token.length() > 0) result.add(token.toString());
        return result;
    }

    private String coordinatePath(String coordinate) throws IOException {
        String[] extension = coordinate.split("@", -1);
        String[] parts = extension[0].split(":", -1);
        if (parts.length < 3 || parts.length > 4 || extension.length > 2) throw new IOException("Invalid library coordinate");
        String suffix = extension.length == 2 ? extension[1] : "jar";
        for (String part : parts) if (!part.matches("[A-Za-z0-9._+-]+")) throw new IOException("Invalid library coordinate component");
        if (!suffix.matches("[A-Za-z0-9]+")) throw new IOException("Invalid library extension");
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2]
                + (parts.length == 4 ? "-" + parts[3] : "") + "." + suffix;
    }

    private void extractNatives(Path archive, Path root, List<String> excluded, Cancellation cancellation) throws IOException {
        long total = 0;
        int files = 0;
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                cancellation.check();
                String name = entry.getName();
                boolean skip = false;
                for (String prefix : excluded) if (name.startsWith(prefix)) skip = true;
                if (skip || entry.isDirectory()) continue;
                if (++files > 8192) throw new IOException("Too many native files");
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root) || target.equals(root)) throw new IOException("Invalid native extraction path");
                Files.createDirectories(target.getParent());
                try (java.io.OutputStream output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    int length;
                    while ((length = zip.read(buffer)) != -1) {
                        cancellation.check();
                        total += length;
                        if (total > 256L * 1024 * 1024) throw new IOException("Native expansion limit exceeded");
                        output.write(buffer, 0, length);
                    }
                }
            }
        }
    }
}
