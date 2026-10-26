/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.worlds;

import com.google.gson.*;
import com.osiris.autoplug.client.launcher.*;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.jar.*;

/** Uses AutoPlug's existing Paper/Purpur/Fabric update protocols without changing global server configuration. */
public final class WorldServerPreparer implements VirtualWorldService.ServerPreparer {
    public static final String PAPER = "https://fill.papermc.io/v3/projects/paper/versions/";
    public static final String PURPUR = "https://api.purpurmc.org/v2/purpur/";
    public static final String FABRIC = "https://meta.fabricmc.net/v2/versions/";
    public static final class Artifact {
        public final String url, cacheKey, hash, algorithm;
        public final long size;
        Artifact(String url, String key, String hash, String algorithm, long size) throws IOException {
            URI uri; try { uri = URI.create(url); } catch (RuntimeException invalid) { throw new IOException("Invalid server download URL", invalid); }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) throw new IOException("Server downloads require HTTPS");
            this.url = url; this.cacheKey = key; this.hash = hash; this.algorithm = algorithm; this.size = size;
        }
    }
    private final LauncherDownloads downloads;
    private final JavaResolver javaResolver;
    private final BooleanSupplier offlineMode;
    public WorldServerPreparer(Path cacheRoot, JavaResolver javaResolver, BooleanSupplier offlineMode) throws IOException { this(cacheRoot, productionTransport(), javaResolver, offlineMode); }
    public WorldServerPreparer(Path cacheRoot, LauncherDownloads.Transport transport, JavaResolver javaResolver, BooleanSupplier offlineMode) throws IOException {
        downloads = new LauncherDownloads(cacheRoot, transport); this.javaResolver = Objects.requireNonNull(javaResolver); this.offlineMode = Objects.requireNonNull(offlineMode);
    }
    @Override public ProcessBuilder prepare(WorldDefinition world, int port, boolean sharing) throws Exception {
        if (!world.acceptedEula) throw new IOException("Accept the Minecraft EULA explicitly for this world before launching: https://aka.ms/MinecraftEULA");
        if (sharing && offlineMode.getAsBoolean()) throw new IOException("Sign in with Microsoft before sharing a world; shared servers require online authentication");
        WorldStore.rejectLinks(world.directoryPath); Files.createDirectories(world.directoryPath);
        JsonObject game = gameMetadata(world.gameVersion);
        int javaMajor = game.has("javaVersion") ? game.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
        Path java = javaResolver.resolve(javaMajor);
        Path cached;
        if (world.serverExecutable != null) { validateSuppliedJar(world.serverExecutable, world.gameVersion); cached = world.serverExecutable; }
        else {
            Artifact artifact = resolveArtifact(world.loader, world.gameVersion);
            cached = downloads.download(artifact.url, artifact.cacheKey, artifact.hash, artifact.algorithm, artifact.size);
            validateSuppliedJar(cached, world.gameVersion);
        }
        Path executable = world.directoryPath.resolve("autoplug-server.jar"); WorldStore.rejectLinks(executable);
        Path staging = Files.createTempFile(world.directoryPath, "server-", ".part");
        try { Files.copy(cached, staging, StandardCopyOption.REPLACE_EXISTING); WorldStore.move(staging, executable); } finally { Files.deleteIfExists(staging); }
        Properties properties = new Properties(); Path propertyFile = world.directoryPath.resolve("server.properties"); WorldStore.rejectLinks(propertyFile);
        if (Files.exists(propertyFile)) { if (Files.size(propertyFile) > 1024 * 1024) throw new IOException("Server properties exceed size limit"); try (InputStream input = Files.newInputStream(propertyFile)) { properties.load(input); } }
        properties.setProperty("server-ip", sharing ? "" : "127.0.0.1"); properties.setProperty("server-port", Integer.toString(port));
        properties.setProperty("level-name", "world"); properties.setProperty("enable-status", "true"); properties.setProperty("enable-query", "false"); properties.setProperty("enable-rcon", "false");
        properties.setProperty("online-mode", Boolean.toString(sharing || !offlineMode.getAsBoolean()));
        if (!properties.containsKey("motd")) properties.setProperty("motd", world.name);
        try (OutputStream output = Files.newOutputStream(propertyFile)) { properties.store(output, "AutoPlug managed virtual world"); }
        Path eula = world.directoryPath.resolve("eula.txt"); WorldStore.rejectLinks(eula);
        Files.write(eula, Arrays.asList("# Explicit acceptance is recorded in this world's metadata", "eula=true"), StandardCharsets.UTF_8);
        return new ProcessBuilder(java.toString(), "-Xms256M", "-Xmx2G", "-jar", executable.toString(), "nogui").directory(world.directoryPath.toFile()).redirectErrorStream(true);
    }
    public Artifact resolveArtifact(String loader, String version) throws IOException {
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9_.+-]{0,79}")) throw new IOException("Invalid Minecraft version");
        try {
            if ("PAPER".equals(loader)) return paper(JsonParser.parseString(downloads.text(PAPER + version + "/builds", 16 * 1024 * 1024)).getAsJsonArray(), version);
            if ("PURPUR".equals(loader)) return purpur(downloads.json(PURPUR + version + "/latest"), version);
            if ("FABRIC".equals(loader)) {
                JsonArray loaders = JsonParser.parseString(downloads.text(FABRIC + "loader/" + version, 8 * 1024 * 1024)).getAsJsonArray();
                JsonArray installers = JsonParser.parseString(downloads.text(FABRIC + "installer", 1024 * 1024)).getAsJsonArray();
                return fabric(loaders, installers, version);
            }
            throw new IOException("Automatic virtual-world server installation supports PAPER, PURPUR and FABRIC. For " + loader + ", supply a matching runnable server JAR; Spigot BuildTools is an installer, not a server");
        } catch (RuntimeException invalid) { throw new IOException("Invalid official server metadata for " + loader + " " + version, invalid); }
    }
    private JsonObject gameMetadata(String version) throws IOException {
        JsonObject manifest = downloads.json(NativeLauncher.VERSION_MANIFEST);
        for (JsonElement entry : manifest.getAsJsonArray("versions")) {
            JsonObject item = entry.getAsJsonObject(); if (!version.equals(item.get("id").getAsString())) continue;
            Path file = downloads.download(item.get("url").getAsString(), "metadata/" + version + ".json", item.has("sha1") ? item.get("sha1").getAsString() : null, "SHA-1", -1);
            try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject game = JsonParser.parseReader(input).getAsJsonObject(); if (!version.equals(game.get("id").getAsString())) throw new IOException("Minecraft metadata version does not match the selected world"); return game;
            }
        }
        throw new IOException("Minecraft version is absent from Mojang's official manifest");
    }
    static Artifact paper(JsonArray builds, String version) throws IOException {
        JsonObject selected = null;
        for (JsonElement entry : builds) { JsonObject build = entry.getAsJsonObject(); if (!"STABLE".equals(build.get("channel").getAsString())) continue; if (selected == null || build.get("id").getAsInt() > selected.get("id").getAsInt()) selected = build; }
        if (selected == null) throw new IOException("No stable Paper build is available for Minecraft " + version);
        JsonObject artifact = selected.getAsJsonObject("downloads").getAsJsonObject("server:default");
        String hash = artifact.getAsJsonObject("checksums").get("sha256").getAsString(); if (!hash.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid Paper checksum");
        return new Artifact(artifact.get("url").getAsString(), "paper/" + version + "/" + selected.get("id").getAsInt() + ".jar", hash, "SHA-256", artifact.get("size").getAsLong());
    }
    static Artifact purpur(JsonObject metadata, String version) throws IOException {
        if (!version.equals(metadata.get("version").getAsString()) || !"SUCCESS".equals(metadata.get("result").getAsString())) throw new IOException("Purpur build is unavailable or does not match the world");
        String build = metadata.get("build").getAsString(), hash = metadata.get("md5").getAsString();
        if (!build.matches("[0-9]{1,10}") || !hash.matches("[a-fA-F0-9]{32}")) throw new IOException("Invalid Purpur build/checksum");
        return new Artifact(PURPUR + version + "/" + build + "/download", "purpur/" + version + "/" + build + ".jar", hash, "MD5", -1);
    }
    static Artifact fabric(JsonArray loaders, JsonArray installers, String version) throws IOException {
        String loader = null, installer = null;
        for (JsonElement entry : loaders) { JsonObject item = entry.getAsJsonObject(), candidate = item.getAsJsonObject("loader"); if (version.equals(item.getAsJsonObject("intermediary").get("version").getAsString()) && candidate.get("stable").getAsBoolean()) { loader = candidate.get("version").getAsString(); break; } }
        for (JsonElement entry : installers) { JsonObject candidate = entry.getAsJsonObject(); if (candidate.get("stable").getAsBoolean()) { installer = candidate.get("version").getAsString(); break; } }
        if (loader == null || installer == null) throw new IOException("No stable Fabric server loader/installer supports Minecraft " + version);
        if (!loader.matches("[A-Za-z0-9_.+-]{1,80}") || !installer.matches("[A-Za-z0-9_.+-]{1,80}")) throw new IOException("Invalid Fabric version metadata");
        // Fabric's generated server launcher API supplies no checksum; authenticity relies on its official HTTPS endpoint.
        return new Artifact(FABRIC + "loader/" + version + "/" + loader + "/" + installer + "/server/jar", "fabric/" + version + "/" + loader + "-" + installer + ".jar", null, "SHA-256", -1);
    }
    public static void validateSuppliedJar(Path executable, String gameVersion) throws IOException {
        WorldStore.rejectLinks(executable);
        if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS) || !executable.getFileName().toString().endsWith(".jar")) throw new IOException("Supply a runnable server JAR");
        try (JarFile jar = new JarFile(executable.toFile())) {
            Manifest manifest = jar.getManifest(); String main = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (main == null || main.trim().isEmpty()) throw new IOException("Server JAR has no executable Main-Class");
            if (main.equals("org.spigotmc.builder.Builder") || main.toLowerCase(Locale.ROOT).contains("buildtools")) throw new IOException("Spigot BuildTools is an installer, not a runnable Minecraft server");
        }
        String detected = new UtilsMinecraft().getInstalledVersion(executable.toFile());
        if (detected != null && !gameVersion.equals(detected)) throw new IOException("Server JAR targets Minecraft " + detected + "; the world requires " + gameVersion);
    }
    private static LauncherDownloads.Transport productionTransport() {
        return uri -> {
            URI current = uri;
            for (int redirects = 0; redirects <= 5; redirects++) {
                if (!"https".equalsIgnoreCase(current.getScheme()) || current.getUserInfo() != null) throw new IOException("World downloads require HTTPS without credentials");
                HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection(); connection.setConnectTimeout(15000); connection.setReadTimeout(60000); connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "AutoPlug-NativeWorlds/10.2.0 (https://github.com/Osiris-Team/AutoPlug-Client)");
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) { String destination = connection.getHeaderField("Location"); connection.disconnect(); if (destination == null) throw new IOException("Missing download redirect"); current = current.resolve(destination); continue; }
                if (status != 200) { connection.disconnect(); throw new IOException("World metadata/download returned HTTP " + status); }
                return new FilterInputStream(connection.getInputStream()) { @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } } };
            }
            throw new IOException("Too many world download redirects");
        };
    }
}
