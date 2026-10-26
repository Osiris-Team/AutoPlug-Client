package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Clean-room launcher based on official Mojang and loader JSON protocols, without launcher code imports. */
public final class NativeLauncher {
    public static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private final LauncherDownloads downloads;
    private final LauncherPlatform platform;
    private final JavaResolver javaResolver;
    public NativeLauncher(Path cacheRoot) throws IOException {
        this(cacheRoot, new LauncherDownloads.HttpsTransport(), LauncherPlatform.current(), null);
    }
    public NativeLauncher(Path cacheRoot, LauncherDownloads.Transport transport, LauncherPlatform platform, JavaResolver resolver) throws IOException {
        this.downloads = new LauncherDownloads(cacheRoot, transport); this.platform = platform;
        this.javaResolver = resolver == null ? new ManagedJavaRuntime(cacheRoot.resolve("runtime")) : resolver;
    }
    public Path resolveJava(LaunchPlan plan) throws Exception { return javaResolver.resolve(plan.getJavaMajor()); }
    public LaunchPlan prepare(String gameVersion, String loader, Path gameDirectory, LaunchAccount account) throws Exception {
        return prepare(gameVersion, loader, null, gameDirectory, account);
    }
    public LaunchPlan prepare(String gameVersion, String loader, String loaderVersion, Path gameDirectory, LaunchAccount account) throws Exception {
        validateVersion(gameVersion);
        JsonObject manifest = downloads.json(VERSION_MANIFEST); JsonObject entry = null;
        for (JsonElement e : manifest.getAsJsonArray("versions")) if (gameVersion.equals(e.getAsJsonObject().get("id").getAsString())) { entry = e.getAsJsonObject(); break; }
        if (entry == null) throw new IOException("Minecraft version is absent from Mojang's official manifest");
        Path metadataFile = downloads.download(entry.get("url").getAsString(), "versions/" + gameVersion + "/" + gameVersion + ".json",
                string(entry, "sha1", null), "SHA-1", -1);
        JsonObject base = readJson(metadataFile);
        String type = loader == null ? "VANILLA" : loader.toUpperCase(Locale.ROOT);
        JsonObject combined = base;
        if ("FABRIC".equals(type) || "QUILT".equals(type)) {
            String endpoint = "FABRIC".equals(type) ? "https://meta.fabricmc.net/v2" : "https://meta.quiltmc.org/v3";
            if (loaderVersion == null) {
                JsonArray versions = JsonParser.parseString(downloads.text(endpoint + "/versions/loader/" + gameVersion, 8 * 1024 * 1024)).getAsJsonArray();
                if (versions.size() == 0) throw new IOException("Loader does not support this Minecraft version");
                JsonObject selected = versions.get(0).getAsJsonObject();
                for (JsonElement e : versions) if (e.getAsJsonObject().getAsJsonObject("loader").has("stable")
                        && e.getAsJsonObject().getAsJsonObject("loader").get("stable").getAsBoolean()) { selected = e.getAsJsonObject(); break; }
                loaderVersion = selected.getAsJsonObject("loader").get("version").getAsString();
            }
            validateVersion(loaderVersion);
            JsonObject extra = downloads.json(endpoint + "/versions/loader/" + gameVersion + "/" + loaderVersion + "/profile/json");
            combined = merge(base, extra);
        } else if ("FORGE".equals(type) || "NEOFORGE".equals(type)) {
            // Installer processors need only the official client JAR, not a game account or running game.
            Path client = client(base);
            LoaderInstaller installer = new LoaderInstaller(downloads, platform, javaResolver);
            combined = merge(base, installer.install(gameVersion, type, loaderVersion, client,
                    base.has("javaVersion") ? base.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8));
        } else if (!"VANILLA".equals(type) && !"NONE".equals(type)) throw new IOException("Not a supported client loader: " + type);
        return prepareMetadata(combined, gameVersion, gameDirectory, account);
    }
    /** Injected metadata/transport is useful for protocol fixtures; normal callers use prepare(). */
    public LaunchPlan prepareMetadata(JsonObject metadata, String gameVersion, Path gameDirectory, LaunchAccount account) throws Exception {
        validateVersion(gameVersion); validateVersion(metadata.get("id").getAsString());
        Path directory = gameDirectory.toAbsolutePath().normalize(); LauncherDownloads.rejectLinks(directory); Files.createDirectories(directory);
        Path client = client(metadata);
        List<Path> classpath = new ArrayList<>();
        Path natives = directory.resolve("autoplug/natives/" + metadata.get("id").getAsString() + "-" + platform.os + "-" + platform.arch.replaceAll("[^A-Za-z0-9_-]", ""));
        LauncherDownloads.rejectLinks(natives); Files.createDirectories(natives);
        if (metadata.has("libraries")) for (JsonElement e : metadata.getAsJsonArray("libraries")) {
            JsonObject library = e.getAsJsonObject(); if (!platform.allows(library)) continue;
            Path artifact = library(library); if (artifact != null) {
                classpath.add(artifact);
                if (string(library, "name", "").contains(":natives-")) LauncherDownloads.extractZip(artifact, natives, Collections.emptyList());
            }
            if (library.has("natives") && library.getAsJsonObject("natives").has(platform.os)) {
                String classifier = library.getAsJsonObject("natives").get(platform.os).getAsString().replace("${arch}", platform.bits());
                JsonObject classifiers = library.getAsJsonObject("downloads").getAsJsonObject("classifiers");
                if (!classifiers.has(classifier)) throw new IOException("Required native classifier is missing: " + classifier);
                Path nativeJar = artifact(classifiers.getAsJsonObject(classifier));
                List<String> excludes = new ArrayList<>();
                if (library.has("extract") && library.getAsJsonObject("extract").has("exclude"))
                    for (JsonElement excluded : library.getAsJsonObject("extract").getAsJsonArray("exclude")) excludes.add(excluded.getAsString());
                LauncherDownloads.extractZip(nativeJar, natives, excludes);
            }
        }
        classpath.add(client);
        Path assets = downloads.resolve("assets/objects").getParent(); Files.createDirectories(assets);
        if (metadata.has("assetIndex")) downloadAssets(metadata.getAsJsonObject("assetIndex"), assets, directory);
        Path logging = null;
        if (metadata.has("logging") && metadata.getAsJsonObject("logging").has("client")) {
            JsonObject file = metadata.getAsJsonObject("logging").getAsJsonObject("client").getAsJsonObject("file");
            logging = downloads.download(file.get("url").getAsString(), "assets/log_configs/" + file.get("id").getAsString(),
                    file.get("sha1").getAsString(), "SHA-1", file.get("size").getAsLong());
        }
        return new LaunchPlan(metadata, gameVersion, directory, natives, assets, downloads.resolve("libraries/.marker").getParent(), client, classpath, account, platform, logging);
    }
    Path client(JsonObject metadata) throws IOException {
        if (!metadata.has("downloads") || !metadata.getAsJsonObject("downloads").has("client")) throw new IOException("Missing Minecraft client download metadata");
        JsonObject client = metadata.getAsJsonObject("downloads").getAsJsonObject("client");
        String id = metadata.has("jar") ? metadata.get("jar").getAsString() : metadata.get("id").getAsString(); validateVersion(id);
        Path original = downloads.download(client.get("url").getAsString(), "versions/" + id + "/" + id + ".jar", client.get("sha1").getAsString(), "SHA-1", client.get("size").getAsLong());
        String launchedVersion = metadata.get("id").getAsString(); validateVersion(launchedVersion);
        if (launchedVersion.equals(id)) return original;
        // Forge/NeoForge's module ignore list uses ${version_name}.jar. Keep the inherited client under that name.
        Path alias = downloads.resolve("versions/" + launchedVersion + "/" + launchedVersion + ".jar");
        linkOrCopy(original, alias); return alias;
    }
    Path artifact(JsonObject artifact) throws IOException {
        return downloads.download(artifact.get("url").getAsString(), "libraries/" + artifact.get("path").getAsString(),
                string(artifact, "sha1", null), "SHA-1", artifact.has("size") ? artifact.get("size").getAsLong() : -1);
    }
    Path library(JsonObject library) throws IOException {
        if (library.has("downloads")) {
            JsonObject information = library.getAsJsonObject("downloads");
            return information.has("artifact") ? artifact(information.getAsJsonObject("artifact")) : null;
        }
        String path = mavenPath(library.get("name").getAsString());
        String repository = string(library, "url", "https://libraries.minecraft.net/");
        if (repository.startsWith("http://")) repository = "https://" + repository.substring(7);
        String url = repository + (repository.endsWith("/") ? "" : "/") + path;
        String hash = string(library, "sha1", null);
        if (hash == null) hash = checksum(downloads.text(url + ".sha1", 4096));
        return downloads.download(url, "libraries/" + path, hash, "SHA-1", library.has("size") ? library.get("size").getAsLong() : -1);
    }
    void downloadAssets(JsonObject information, Path assets, Path gameDirectory) throws Exception {
        String id = information.get("id").getAsString(); validateVersion(id);
        Path indexFile = downloads.download(information.get("url").getAsString(), "assets/indexes/" + id + ".json", information.get("sha1").getAsString(), "SHA-1", information.get("size").getAsLong());
        JsonObject index = readJson(indexFile); boolean virtual = index.has("virtual") && index.get("virtual").getAsBoolean();
        boolean resources = index.has("map_to_resources") && index.get("map_to_resources").getAsBoolean();
        ExecutorService pool = Executors.newFixedThreadPool(4); List<Future<?>> futures = new ArrayList<>();
        try {
            for (Map.Entry<String, JsonElement> entry : index.getAsJsonObject("objects").entrySet()) {
                final String name = entry.getKey(); final JsonObject object = entry.getValue().getAsJsonObject();
                futures.add(pool.submit(() -> {
                    String hash = object.get("hash").getAsString();
                    if (!hash.matches("[a-f0-9]{40}")) throw new IOException("Invalid asset hash");
                    Path source = downloads.download("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash,
                            "assets/objects/" + hash.substring(0, 2) + "/" + hash, hash, "SHA-1", object.get("size").getAsLong());
                    if (virtual) linkOrCopy(source, LauncherDownloads.safeResolve(assets.resolve("virtual/" + id), name));
                    if (resources) linkOrCopy(source, LauncherDownloads.safeResolve(gameDirectory.resolve("resources"), name));
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
        } catch (ExecutionException e) { throw new IOException("Asset preparation failed", e.getCause()); }
        finally { pool.shutdownNow(); }
    }
    static void linkOrCopy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent()); LauncherDownloads.rejectLinks(target);
        if (Files.isRegularFile(target) && Files.size(target) == Files.size(source)
                && LauncherDownloads.hash(target, "SHA-1").equals(LauncherDownloads.hash(source, "SHA-1"))) return;
        Files.deleteIfExists(target);
        try { Files.createLink(target, source); } catch (IOException | UnsupportedOperationException e) { Files.copy(source, target); }
    }
    public static String mavenPath(String coordinate) throws IOException {
        String[] extension = coordinate.split("@", -1); if (extension.length > 2) throw new IOException("Invalid Maven coordinate");
        String[] pieces = extension[0].split(":", -1);
        if (pieces.length < 3 || pieces.length > 4) throw new IOException("Invalid Maven coordinate");
        for (String piece : pieces) if (!piece.matches("[A-Za-z0-9_+.-]+") || piece.equals(".") || piece.equals("..")) throw new IOException("Unsafe Maven coordinate");
        String type = extension.length == 2 ? extension[1] : "jar";
        if (!type.matches("[A-Za-z0-9]+")) throw new IOException("Unsafe Maven extension");
        return pieces[0].replace('.', '/') + "/" + pieces[1] + "/" + pieces[2] + "/" + pieces[1] + "-" + pieces[2]
                + (pieces.length == 4 ? "-" + pieces[3] : "") + "." + type;
    }
    static String checksum(String text) throws IOException {
        String value = text.trim().split("\\s+")[0]; if (!value.matches("[a-fA-F0-9]{40}")) throw new IOException("Invalid repository SHA-1 checksum"); return value;
    }
    static void validateVersion(String value) throws IOException { if (value == null || !value.matches("[A-Za-z0-9_+.-]{1,160}") || value.equals(".") || value.equals("..")) throw new IOException("Unsafe version identifier"); }
    static JsonObject readJson(Path path) throws IOException { try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); } }
    static String string(JsonObject object, String name, String fallback) { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : fallback; }
    public static JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject merged = parent.deepCopy();
        for (Map.Entry<String, JsonElement> entry : child.entrySet()) if (!entry.getKey().equals("libraries") && !entry.getKey().equals("arguments") && !entry.getKey().equals("inheritsFrom")) merged.add(entry.getKey(), entry.getValue().deepCopy());
        if (!child.has("jar")) merged.addProperty("jar", string(parent, "jar", parent.get("id").getAsString()));
        LinkedHashMap<String, JsonElement> libraries = new LinkedHashMap<>();
        for (JsonObject source : Arrays.asList(parent, child)) if (source.has("libraries")) for (JsonElement library : source.getAsJsonArray("libraries")) {
            String name = library.getAsJsonObject().get("name").getAsString(); String[] parts = name.split(":");
            String key = parts[0] + ":" + parts[1] + (parts.length > 3 ? ":" + parts[3] : ""); libraries.put(key, library.deepCopy());
        }
        JsonArray libraryArray = new JsonArray(); for (JsonElement library : libraries.values()) libraryArray.add(library); merged.add("libraries", libraryArray);
        if (parent.has("arguments") || child.has("arguments")) {
            JsonObject arguments = new JsonObject();
            for (String kind : Arrays.asList("jvm", "game")) {
                JsonArray values = new JsonArray();
                for (JsonObject source : Arrays.asList(parent, child)) if (source.has("arguments") && source.getAsJsonObject("arguments").has(kind))
                    for (JsonElement argument : source.getAsJsonObject("arguments").getAsJsonArray(kind)) values.add(argument.deepCopy());
                arguments.add(kind, values);
            }
            merged.add("arguments", arguments);
        }
        return merged;
    }
}
