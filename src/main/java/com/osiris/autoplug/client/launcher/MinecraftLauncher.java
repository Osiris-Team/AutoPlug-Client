package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.utils.UtilsString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Native, profile-isolated Minecraft launcher implemented from the publishers' metadata formats. */
public class MinecraftLauncher {
    static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private final Path cacheRoot;
    private final JavaRuntimeManager runtimes;
    private final String manifestUrl;
    private final LoaderInstaller loaders;

    public MinecraftLauncher(Path cacheRoot, JavaRuntimeManager runtimes) { this(cacheRoot, runtimes, MANIFEST_URL); }
    MinecraftLauncher(Path cacheRoot, JavaRuntimeManager runtimes, String manifestUrl) {
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
        this.runtimes = Objects.requireNonNull(runtimes, "runtimes");
        this.manifestUrl = manifestUrl;
        this.loaders = new LoaderInstaller(this.cacheRoot);
    }

    public synchronized PreparedLaunch prepare(LaunchRequest request, Consumer<String> progress) throws Exception {
        Consumer<String> log = progress == null ? value -> { } : progress;
        if (request.account.needsRefresh()) throw new IOException("Microsoft session expired. Refresh the account before launching.");
        Files.createDirectories(request.gameDir);
        JsonObject vanilla = version(request.version, log);
        int major = requiredJava(vanilla);
        Path java = runtimes.resolve(major, log);
        Path client = downloadClient(vanilla, request.version, log);
        JsonObject loader = loaders.clientProfile(request, vanilla, client, java, log);
        JsonObject metadata = loader == null ? vanilla : merge(vanilla, loader);
        String id = metadata.get("id").getAsString();
        Path natives = LauncherFiles.child(request.gameDir.resolve(".autoplug").resolve("natives"), safeId(id) + "-" + osName() + "-" + System.getProperty("os.arch"));
        Files.createDirectories(natives);
        List<Path> classpath = libraries(metadata, natives, log);
        classpath.add(clientJarForProfile(client, metadata, cacheRoot));
        Path assets = cacheRoot.resolve("assets");
        String assetId = assets(metadata, assets, request.gameDir, log);
        Path logging = null;
        if (metadata.has("logging") && metadata.getAsJsonObject("logging").has("client")) {
            JsonObject descriptor = metadata.getAsJsonObject("logging").getAsJsonObject("client").getAsJsonObject("file");
            logging = LauncherFiles.child(cacheRoot.resolve("logging"), descriptor.get("id").getAsString());
            download(descriptor, logging, log);
        }
        List<String> args = buildArguments(metadata, request, classpath, natives, assets, assetId, logging);
        log.accept("Minecraft " + id + " is ready (Java " + major + ")");
        return new PreparedLaunch(java, request.gameDir, args, id, major);
    }

    /** Minecraft's output is written inside this profile and is never mixed with another world's console. */
    public Process launch(PreparedLaunch prepared) throws IOException {
        Path logs = prepared.gameDir.resolve("logs");
        Files.createDirectories(logs);
        // Loader classpaths can exceed Windows' command-line limit. Java 9+ accepts a private
        // argument file; preserve the inspectable PreparedLaunch contract and remove it on exit.
        List<String> command = prepared.command();
        Path argumentsFile = null;
        if (prepared.javaMajor >= 9) {
            Path state = prepared.gameDir.resolve(".autoplug"); Files.createDirectories(state);
            argumentsFile = Files.createTempFile(state, "launch-", ".args");
            try {
                AccountStore.restrict(argumentsFile);
                List<String> lines = new ArrayList<>();
                for (String argument : prepared.arguments) lines.add(quoteArgumentFile(argument));
                java.nio.charset.Charset encoding = java.nio.charset.Charset.forName(System.getProperty("native.encoding", java.nio.charset.Charset.defaultCharset().name()));
                Files.write(argumentsFile, lines, encoding);
                command = java.util.Arrays.asList(prepared.executable.toString(), "@" + argumentsFile);
            } catch (IOException | RuntimeException e) { Files.deleteIfExists(argumentsFile); throw e; }
        }
        final Path cleanup = argumentsFile;
        try {
            Process process = new ProcessBuilder(command).directory(prepared.gameDir.toFile()).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logs.resolve("autoplug-launcher.log").toFile())).start();
            if (cleanup != null) {
                cleanup.toFile().deleteOnExit();
                // A non-daemon session thread also makes one-shot CLI launches wait for their
                // client and guarantees cleanup before normal JVM shutdown. A daemon future can
                // be lost when the launching CLI exits immediately after the game does.
                Thread cleanupThread = new Thread(() -> {
                    try { process.waitFor(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { try { Files.deleteIfExists(cleanup); } catch (IOException ignored) { /* deleteOnExit remains registered */ } }
                }, "Minecraft session cleanup");
                cleanupThread.setDaemon(false);
                cleanupThread.start();
            }
            return process;
        } catch (IOException e) { if (cleanup != null) Files.deleteIfExists(cleanup); throw e; }
    }

    static String quoteArgumentFile(String argument) {
        return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    /** Resolves metadata only, without downloading Minecraft assets or installing Java. */
    public int requiredJavaMajor(String gameVersion, Consumer<String> progress) throws IOException {
        return requiredJava(version(LaunchRequest.safeVersion(gameVersion), progress == null ? value -> { } : progress));
    }

    public JsonObject getVersionMetadata(String gameVersion, Consumer<String> progress) throws IOException {
        return version(LaunchRequest.safeVersion(gameVersion), progress == null ? value -> { } : progress).deepCopy();
    }

    /** Installs dedicated Quilt/Forge/NeoForge servers without starting them or accepting their EULA. */
    public synchronized PreparedLaunch prepareServer(String gameVersion, String loader, String loaderVersion,
                                                     Path worldDirectory, Consumer<String> progress) throws Exception {
        String version = LaunchRequest.safeVersion(gameVersion);
        Consumer<String> log = progress == null ? value -> { } : progress;
        JsonObject metadata = version(version, log);
        int major = requiredJava(metadata);
        Path java = runtimes.resolve(major, log);
        return loaders.server(version, loader.toUpperCase(Locale.ROOT), loaderVersion, worldDirectory.toAbsolutePath().normalize(), java, major, log);
    }

    JsonObject version(String requested, Consumer<String> progress) throws IOException {
        Path versions = cacheRoot.resolve("versions");
        Path destination = LauncherFiles.child(versions, safeId(requested) + "/" + safeId(requested) + ".json");
        JsonObject manifest;
        Path cachedManifest = cacheRoot.resolve("version_manifest_v2.json");
        try {
            manifest = LauncherFiles.json(manifestUrl);
            LauncherFiles.writeJson(cachedManifest, manifest);
        } catch (IOException e) {
            if (Files.exists(cachedManifest)) manifest = LauncherFiles.readJson(cachedManifest);
            else if (Files.exists(destination)) return LauncherFiles.readJson(destination);
            else throw e;
        }
        for (JsonElement value : manifest.getAsJsonArray("versions")) {
            JsonObject entry = value.getAsJsonObject();
            if (!entry.get("id").getAsString().equals(requested)) continue;
            LauncherFiles.download(entry.get("url").getAsString(), destination, string(entry, "sha1", null), -1, progress);
            JsonObject result = LauncherFiles.readJson(destination);
            if (!result.get("id").getAsString().equals(requested)) throw new IOException("Minecraft metadata version mismatch.");
            return result;
        }
        throw new IOException("Minecraft version not found: " + requested);
    }
    static int requiredJava(JsonObject metadata) {
        return metadata.has("javaVersion") ? metadata.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
    }
    private Path downloadClient(JsonObject metadata, String id, Consumer<String> progress) throws IOException {
        if (!metadata.has("downloads") || !metadata.getAsJsonObject("downloads").has("client"))
            throw new IOException("This Minecraft version does not publish a client download.");
        Path jar = LauncherFiles.child(cacheRoot.resolve("versions"), safeId(id) + "/" + safeId(id) + ".jar");
        return download(metadata.getAsJsonObject("downloads").getAsJsonObject("client"), jar, progress);
    }
    static Path download(JsonObject descriptor, Path path, Consumer<String> progress) throws IOException {
        return LauncherFiles.download(descriptor.get("url").getAsString(), path, string(descriptor, "sha1", null),
                descriptor.has("size") ? descriptor.get("size").getAsLong() : -1, progress);
    }
    static Path clientJarForProfile(Path vanillaJar, JsonObject metadata, Path cache) throws IOException {
        if (!metadata.has("inheritsFrom")) return vanillaJar;
        // The effective version jar name must match ${version_name}. Forge/NeoForge's
        // module bootstrap ignore list uses that name to exclude the unpatched vanilla jar.
        String id = safeId(metadata.get("id").getAsString());
        Path jar = LauncherFiles.child(cache.resolve("versions"), id + "/" + id + ".jar");
        linkOrCopy(vanillaJar, jar);
        return jar;
    }
    List<Path> libraries(JsonObject metadata, Path natives, Consumer<String> progress) throws IOException {
        List<Path> classpath = new ArrayList<>();
        if (!metadata.has("libraries")) return classpath;
        for (JsonElement entry : metadata.getAsJsonArray("libraries")) {
            JsonObject library = entry.getAsJsonObject();
            if (!allowed(library, Collections.emptyMap())) continue;
            JsonObject downloads = library.has("downloads") ? library.getAsJsonObject("downloads") : new JsonObject();
            if (downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                classpath.add(download(artifact, LauncherFiles.child(cacheRoot.resolve("libraries"), artifact.get("path").getAsString()), progress));
            } else if (!library.has("natives") || !downloads.has("classifiers")) {
                String relative = mavenPath(library.get("name").getAsString());
                String base = string(library, "url", "https://libraries.minecraft.net/");
                Path artifact = LauncherFiles.child(cacheRoot.resolve("libraries"), relative);
                if (base.isEmpty()) {
                    if (!Files.isRegularFile(artifact)) throw new IOException("Loader installer did not generate required library " + relative);
                    classpath.add(artifact);
                } else classpath.add(LauncherFiles.download(base + (base.endsWith("/") ? "" : "/") + relative, artifact,
                        string(library, "sha1", null), library.has("size") ? library.get("size").getAsLong() : -1, progress));
            }
            if (library.has("natives") && library.getAsJsonObject("natives").has(osName())) {
                String classifier = library.getAsJsonObject("natives").get(osName()).getAsString()
                        .replace("${arch}", System.getProperty("os.arch").contains("64") ? "64" : "32");
                if (!downloads.has("classifiers") || !downloads.getAsJsonObject("classifiers").has(classifier))
                    throw new IOException("Native library missing for " + osName() + ": " + library.get("name").getAsString());
                JsonObject nativeArtifact = downloads.getAsJsonObject("classifiers").getAsJsonObject(classifier);
                Path jar = download(nativeArtifact, LauncherFiles.child(cacheRoot.resolve("libraries"), nativeArtifact.get("path").getAsString()), progress);
                List<String> exclusions = new ArrayList<>(); exclusions.add("META-INF/");
                if (library.has("extract") && library.getAsJsonObject("extract").has("exclude"))
                    for (JsonElement exclude : library.getAsJsonObject("extract").getAsJsonArray("exclude")) exclusions.add(exclude.getAsString());
                extractNatives(jar, natives, exclusions);
            }
        }
        return classpath;
    }
    private String assets(JsonObject metadata, Path assets, Path gameDir, Consumer<String> progress) throws IOException {
        if (!metadata.has("assetIndex")) return string(metadata, "assets", "legacy");
        JsonObject descriptor = metadata.getAsJsonObject("assetIndex");
        String id = descriptor.get("id").getAsString();
        Path index = LauncherFiles.child(assets.resolve("indexes"), safeId(id) + ".json");
        download(descriptor, index, progress);
        JsonObject data = LauncherFiles.readJson(index);
        boolean virtual = data.has("virtual") && data.get("virtual").getAsBoolean();
        boolean resources = data.has("map_to_resources") && data.get("map_to_resources").getAsBoolean();
        int total = data.getAsJsonObject("objects").size();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(8, task -> {
            Thread thread = new Thread(task, "Minecraft asset download"); thread.setDaemon(true); return thread;
        });
        java.util.concurrent.CompletionService<Void> completion = new java.util.concurrent.ExecutorCompletionService<>(executor);
        List<java.util.concurrent.Future<Void>> downloads = new ArrayList<>();
        try {
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("objects").entrySet()) {
                downloads.add(completion.submit(() -> {
                    JsonObject object = entry.getValue().getAsJsonObject();
                    String hash = object.get("hash").getAsString();
                    if (!hash.matches("[0-9a-fA-F]{40}")) throw new IOException("Invalid asset hash.");
                    String relative = hash.substring(0, 2) + "/" + hash;
                    Path artifact = LauncherFiles.download("https://resources.download.minecraft.net/" + relative,
                            LauncherFiles.child(assets.resolve("objects"), relative), hash, object.get("size").getAsLong(), ignored -> { });
                    if (virtual) linkOrCopy(artifact, LauncherFiles.child(assets.resolve("virtual").resolve(safeId(id)), entry.getKey()));
                    if (resources) linkOrCopy(artifact, LauncherFiles.child(gameDir.resolve("resources"), entry.getKey()));
                    return null;
                }));
            }
            for (int done = 1; done <= total; done++) {
                completion.take().get();
                if (done % 100 == 0 || done == total) progress.accept("Minecraft assets: " + done + "/" + total);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException("Minecraft preparation cancelled.");
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw new IOException("Minecraft asset preparation failed.", e.getCause());
        } finally {
            for (java.util.concurrent.Future<Void> task : downloads) if (!task.isDone()) task.cancel(true);
            executor.shutdownNow();
        }
        return id;
    }
    static void linkOrCopy(Path source, Path target) throws IOException {
        if (Files.exists(target) && (Files.isSameFile(source, target) || (Files.size(target) == Files.size(source)
                && com.osiris.autoplug.client.utils.UtilsCrypto.fastSHA1(source.toFile()).equals(com.osiris.autoplug.client.utils.UtilsCrypto.fastSHA1(target.toFile()))))) return;
        Files.createDirectories(target.getParent());
        Files.deleteIfExists(target);
        try { Files.createLink(target, source); }
        catch (IOException | UnsupportedOperationException e) { Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    static void extractNatives(Path jar, Path target, List<String> exclusions) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || exclusions.stream().anyMatch(name::startsWith)) continue;
                Path output = LauncherFiles.child(target, name);
                if (sameNative(output, entry)) continue;
                Files.createDirectories(output.getParent());
                try (java.io.InputStream input = zip.getInputStream(entry)) { Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }
    private static boolean sameNative(Path output, ZipEntry entry) throws IOException {
        if (!Files.isRegularFile(output) || Files.size(output) != entry.getSize()) return false;
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        try (java.io.InputStream input = Files.newInputStream(output)) {
            byte[] buffer = new byte[65536]; int size;
            while ((size = input.read(buffer)) != -1) crc.update(buffer, 0, size);
        }
        return crc.getValue() == entry.getCrc();
    }
    static List<String> buildArguments(JsonObject metadata, LaunchRequest request, List<Path> classpath,
                                       Path natives, Path assets, String assetId, Path logging) throws Exception {
        Map<String, String> values = new HashMap<>();
        String id = metadata.get("id").getAsString();
        values.put("auth_player_name", request.account.username); values.put("version_name", id);
        values.put("game_directory", request.gameDir.toString()); values.put("assets_root", assets.toString());
        values.put("assets_index_name", assetId); values.put("auth_uuid", request.account.uuid);
        values.put("auth_access_token", request.account.accessToken); values.put("auth_session", request.account.accessToken);
        values.put("auth_xuid", request.account.xuid); values.put("clientid", request.account.clientId);
        values.put("user_type", request.account.offline ? "legacy" : "msa"); values.put("user_properties", "{}");
        values.put("profile_properties", "{}"); values.put("version_type", string(metadata, "type", "release"));
        values.put("natives_directory", natives.toString()); values.put("launcher_name", "AutoPlug");
        values.put("launcher_version", "10.2.0"); values.put("classpath_separator", File.pathSeparator);
        values.put("library_directory", assets.getParent().resolve("libraries").toString());
        values.put("game_assets", assets.resolve("virtual").resolve(assetId).toString());
        List<String> classpathStrings = new ArrayList<>(); for (Path path : classpath) classpathStrings.add(path.toString());
        values.put("classpath", String.join(File.pathSeparator, classpathStrings));
        boolean quickPlay = request.serverHost != null && metadata.has("arguments")
                && metadata.getAsJsonObject("arguments").toString().contains("${quickPlayMultiplayer}");
        Map<String, Boolean> features = new HashMap<>();
        features.put("is_quick_play_multiplayer", quickPlay);
        features.put("has_quick_plays_support", quickPlay);
        if (quickPlay) {
            String host = request.serverHost.contains(":") && !request.serverHost.startsWith("[") ? "[" + request.serverHost + "]" : request.serverHost;
            values.put("quickPlayMultiplayer", host + ":" + request.serverPort);
            values.put("quickPlayPath", request.gameDir.resolve("logs").resolve("quick-play.json").toString());
        }
        List<String> args = new ArrayList<>();
        args.add("-Xmx2G");
        if (metadata.has("arguments") && metadata.getAsJsonObject("arguments").has("jvm"))
            expand(metadata.getAsJsonObject("arguments").getAsJsonArray("jvm"), values, features, args);
        else {
            if (osName().equals("osx")) args.add("-XstartOnFirstThread");
            args.add("-Djava.library.path=" + natives); args.add("-cp"); args.add(values.get("classpath"));
        }
        if (logging != null) {
            String argument = metadata.getAsJsonObject("logging").getAsJsonObject("client").get("argument").getAsString();
            args.add(argument.replace("${path}", logging.toString()));
        }
        args.add(metadata.get("mainClass").getAsString());
        if (metadata.has("arguments") && metadata.getAsJsonObject("arguments").has("game"))
            expand(metadata.getAsJsonObject("arguments").getAsJsonArray("game"), values, features, args);
        else if (metadata.has("minecraftArguments")) {
            for (String argument : new UtilsString().splitBySpacesAndQuotes(metadata.get("minecraftArguments").getAsString()))
                args.add(substitute(argument, values));
        } else throw new IOException("Minecraft metadata has no game launch arguments.");
        if (request.serverHost != null && !quickPlay) {
            args.add("--server"); args.add(request.serverHost); args.add("--port"); args.add(Integer.toString(request.serverPort));
        }
        return args;
    }
    private static void expand(JsonArray array, Map<String, String> values, Map<String, Boolean> features, List<String> output) throws IOException {
        for (JsonElement item : array) {
            if (item.isJsonPrimitive()) output.add(substitute(item.getAsString(), values));
            else {
                JsonObject argument = item.getAsJsonObject();
                if (!allowed(argument, features)) continue;
                JsonElement value = argument.get("value");
                if (value.isJsonArray()) for (JsonElement element : value.getAsJsonArray()) output.add(substitute(element.getAsString(), values));
                else output.add(substitute(value.getAsString(), values));
            }
        }
    }
    static String substitute(String argument, Map<String, String> values) throws IOException {
        Matcher matcher = Pattern.compile("\\$\\{([^}]+)}").matcher(argument);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) throw new IOException("Unsupported Minecraft launch parameter: " + matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
    static boolean allowed(JsonObject object, Map<String, Boolean> features) {
        if (!object.has("rules")) return true;
        boolean allow = false;
        for (JsonElement value : object.getAsJsonArray("rules")) {
            JsonObject rule = value.getAsJsonObject();
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name") && !os.get("name").getAsString().equals(osName())) continue;
                if (os.has("arch") && !System.getProperty("os.arch").matches(os.get("arch").getAsString())) continue;
                if (os.has("version") && !Pattern.compile(os.get("version").getAsString()).matcher(System.getProperty("os.version")).find()) continue;
            }
            boolean matches = true;
            if (rule.has("features")) for (Map.Entry<String, JsonElement> feature : rule.getAsJsonObject("features").entrySet())
                if (features.getOrDefault(feature.getKey(), false) != feature.getValue().getAsBoolean()) { matches = false; break; }
            if (matches) allow = rule.get("action").getAsString().equals("allow");
        }
        return allow;
    }
    static JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject merged = parent.deepCopy();
        for (Map.Entry<String, JsonElement> field : child.entrySet()) {
            if (field.getKey().equals("libraries")) {
                Map<String, JsonElement> libraries = new LinkedHashMap<>();
                for (JsonElement value : parent.getAsJsonArray("libraries")) libraries.put(libraryKey(value), value);
                for (JsonElement value : field.getValue().getAsJsonArray()) libraries.put(libraryKey(value), value);
                JsonArray array = new JsonArray(); libraries.values().forEach(array::add); merged.add("libraries", array);
            } else if (field.getKey().equals("arguments")) {
                JsonObject args = parent.has("arguments") ? parent.getAsJsonObject("arguments").deepCopy() : new JsonObject();
                for (Map.Entry<String, JsonElement> arg : field.getValue().getAsJsonObject().entrySet()) {
                    JsonArray array = args.has(arg.getKey()) ? args.getAsJsonArray(arg.getKey()) : new JsonArray();
                    for (JsonElement value : arg.getValue().getAsJsonArray()) array.add(value);
                    args.add(arg.getKey(), array);
                }
                merged.add("arguments", args);
            } else merged.add(field.getKey(), field.getValue().deepCopy());
        }
        return merged;
    }
    private static String libraryKey(JsonElement value) {
        String[] parts = value.getAsJsonObject().get("name").getAsString().split(":");
        return parts[0] + ":" + parts[1] + (parts.length > 3 ? ":" + parts[3] : "");
    }
    static String mavenPath(String coordinate) throws IOException {
        String[] extension = coordinate.split("@", 2);
        String[] parts = extension[0].split(":");
        if (parts.length < 3 || parts.length > 4) throw new IOException("Invalid Maven artifact: " + coordinate);
        for (String part : parts) if (part.isEmpty() || part.contains("/") || part.contains("\\") || part.contains(".."))
            throw new IOException("Invalid Maven artifact: " + coordinate);
        String suffix = extension.length == 2 ? extension[1] : "jar";
        if (!suffix.matches("[A-Za-z0-9]+")) throw new IOException("Invalid artifact extension.");
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2]
                + (parts.length == 4 ? "-" + parts[3] : "") + "." + suffix;
    }
    static String osName() {
        String value = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return value.contains("win") ? "windows" : value.contains("mac") ? "osx" : "linux";
    }
    static String safeId(String id) { return LaunchRequest.safeVersion(id); }
    static String string(JsonObject object, String key, String fallback) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
}
