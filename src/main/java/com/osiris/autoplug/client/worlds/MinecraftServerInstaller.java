package com.osiris.autoplug.client.worlds;

import com.google.gson.*;
import com.osiris.autoplug.client.profiles.ArtifactCache;
import com.osiris.autoplug.client.profiles.Profile;
import com.osiris.autoplug.client.profiles.ProfileLease;
import io.github.projectunified.mcserverupdater.UpdateBuilder;
import io.github.projectunified.mcserverupdater.UpdateStatus;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/** Reuses the bundled server updaters, with isolated install work and shared immutable JARs. */
public final class MinecraftServerInstaller implements ServerInstaller {
    @FunctionalInterface public interface JavaRuntime { Path resolve(String gameVersion) throws Exception; }
    @FunctionalInterface public interface ArtifactProvider { Path acquire(Profile profile, Path workspace, Path javaExecutable) throws Exception; }
    private final ArtifactCache cache;
    private final JavaRuntime runtimes;
    private final Consumer<String> progress;
    private final ServerInstaller moddedInstaller;
    private final ArtifactProvider provider;

    public MinecraftServerInstaller(ArtifactCache cache, JavaRuntime runtimes) {
        this(cache, runtimes, ignored -> { }, null);
    }
    public MinecraftServerInstaller(ArtifactCache cache, JavaRuntime runtimes, Consumer<String> progress, ServerInstaller moddedInstaller) {
        this.cache = Objects.requireNonNull(cache); this.runtimes = Objects.requireNonNull(runtimes);
        this.progress = Objects.requireNonNull(progress); this.moddedInstaller = moddedInstaller;
        this.provider = this::acquire;
    }
    public MinecraftServerInstaller(ArtifactCache cache, JavaRuntime runtimes, ArtifactProvider provider) {
        this.cache = Objects.requireNonNull(cache); this.runtimes = Objects.requireNonNull(runtimes);
        this.provider = Objects.requireNonNull(provider); this.progress = ignored -> { }; this.moddedInstaller = null;
    }

    @Override public ServerLaunch prepare(Profile profile, Path worldDirectory) throws Exception {
        Files.createDirectories(worldDirectory);
        try (ProfileLease ignored = new ProfileLease(profile.getDirectory())) { copyProfile(profile, worldDirectory); }
        String software = profile.getLoader().toLowerCase(Locale.ROOT);
        if (Arrays.asList("forge", "neoforge", "quilt").contains(software)) {
            if (moddedInstaller == null) throw new IOException("No native server installer configured for " + software);
            return moddedInstaller.prepare(profile, worldDirectory);
        }
        if (Arrays.asList("velocity", "bungee", "bungeecord").contains(software))
            throw new IOException("Proxy profiles cannot host a Minecraft world; select Paper, Spigot or a modded server");
        Path java = runtimes.resolve(profile.getGameVersion());
        String key = safe(software) + "-" + safe(profile.getGameVersion()) + "-" + safe(profile.getLoaderVersion() == null ? "default" : profile.getLoaderVersion());
        Path work = cache.getRoot().resolve("server-installers").resolve(key);
        Files.createDirectories(work);
        try (ProfileLease ignored = new ProfileLease(work)) {
            Path jar = work.resolve("server.jar");
            if (!Files.isRegularFile(jar) || Files.size(jar) == 0) {
                Path acquired = provider.acquire(profile, work, java);
                if (acquired == null || !Files.isRegularFile(acquired)) throw new IOException("Server installer produced no JAR");
                try (JarFile ignoredJar = new JarFile(acquired.toFile())) { /* Reject error pages and incomplete downloads. */ }
                cache.link(cache.store(acquired), jar);
            }
            cache.link(cache.store(jar), worldDirectory.resolve("server.jar"));
        }
        return new ServerLaunch(Arrays.asList(java.toString(), "-Xms512M", "-Xmx2G", "-jar", "server.jar", "nogui"), worldDirectory);
    }

    private void copyProfile(Profile profile, Path worldDirectory) throws IOException {
        Set<String> roots = new HashSet<>(Arrays.asList("mods", "plugins", "config", "defaultconfigs", "server.properties",
                "bukkit.yml", "spigot.yml", "paper.yml", "permissions.yml", "whitelist.json", "ops.json", "banned-players.json",
                "banned-ips.json", "server-icon.png"));
        Set<String> jars = new TreeSet<>();
        Path source = profile.getDirectory();
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(directory);
                if (!relative.toString().isEmpty() && !roots.contains(relative.getName(0).toString())) return FileVisitResult.SKIP_SUBTREE;
                Path target = worldDirectory.resolve(relative);
                if (Files.isSymbolicLink(target)) throw new IOException("Linked world configuration directory: " + relative);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                if (!roots.contains(relative.getName(0).toString())) return FileVisitResult.CONTINUE;
                Path target = worldDirectory.resolve(relative);
                if (file.getFileName().toString().endsWith(".jar")) {
                    cache.link(cache.store(file), target);
                    jars.add(relative.toString().replace('\\', '/'));
                } else {
                    if (Files.isSymbolicLink(file) || Files.isSymbolicLink(target)) throw new IOException("Linked profile configuration: " + relative);
                    // World-specific settings survive profile refreshes.
                    if (!Files.exists(target)) Files.copy(file, target);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Path manifest = worldDirectory.resolve(".autoplug-profile-jars");
        if (Files.isSymbolicLink(manifest)) throw new IOException("Invalid world artifact manifest");
        if (Files.exists(manifest)) {
            for (String old : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                Path path = worldDirectory.resolve(old).normalize();
                if (!jars.contains(old) && path.startsWith(worldDirectory) && old.endsWith(".jar")
                        && (old.startsWith("mods/") || old.startsWith("plugins/"))) Files.deleteIfExists(path);
            }
        }
        Files.write(manifest, jars, StandardCharsets.UTF_8);
    }

    private Path acquire(Profile profile, Path work, Path java) throws Exception {
        String software = profile.getLoader().toLowerCase(Locale.ROOT);
        if (software.equals("bukkit")) software = "spigot";
        Path output = work.resolve("download.jar");
        if (software.equals("vanilla")) {
            JsonObject manifest = json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").getAsJsonObject();
            String metadata = null;
            for (JsonElement item : manifest.getAsJsonArray("versions")) {
                JsonObject version = item.getAsJsonObject();
                if (profile.getGameVersion().equals(version.get("id").getAsString())) metadata = version.get("url").getAsString();
            }
            if (metadata == null) throw new IOException("Unknown Minecraft version " + profile.getGameVersion());
            JsonObject details = json(metadata).getAsJsonObject();
            if (!details.getAsJsonObject("downloads").has("server")) throw new IOException("This version has no dedicated server download");
            JsonObject server = details.getAsJsonObject("downloads").getAsJsonObject("server");
            download(server.get("url").getAsString(), output);
            String expected = server.get("sha1").getAsString();
            if (!expected.equalsIgnoreCase(sha1(output))) { Files.deleteIfExists(output); throw new IOException("Server download checksum mismatch"); }
        } else if (software.equals("fabric") && profile.getLoaderVersion() != null && !profile.getLoaderVersion().isEmpty()) {
            JsonArray installers = json("https://meta.fabricmc.net/v2/versions/installer").getAsJsonArray();
            String installer = null;
            for (JsonElement entry : installers) if (entry.getAsJsonObject().get("stable").getAsBoolean()) {
                installer = entry.getAsJsonObject().get("version").getAsString(); break;
            }
            if (installer == null) throw new IOException("No stable Fabric installer available");
            download("https://meta.fabricmc.net/v2/versions/loader/" + safe(profile.getGameVersion()) + "/"
                    + safe(profile.getLoaderVersion()) + "/" + safe(installer) + "/server/jar", output);
        } else {
            if (!UpdateBuilder.getUpdaterNames().contains(software)) throw new IOException("Unsupported world server software: " + software);
            if (software.equals("spigot")) {
                // The existing Spigot updater accepts the Java path via a system property. Isolate that
                // property in a worker JVM instead of changing it for concurrent AutoPlug operations.
                String currentJava = Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
                String classpath = Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
                        .map(p -> Paths.get(p).toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator));
                Process worker = new ProcessBuilder(currentJava, "-DMCServerUpdater.javaExecutable=" + java,
                        "-cp", classpath, ServerInstallerWorker.class.getName(), software, profile.getGameVersion(),
                        work.toString(), output.toString()).directory(work.toFile()).redirectErrorStream(true)
                        .redirectOutput(work.resolve("build.log").toFile()).start();
                try {
                    if (!worker.waitFor(15, TimeUnit.MINUTES)) throw new IOException("Spigot build timed out; see " + work.resolve("build.log"));
                    if (worker.exitValue() != 0) throw new IOException("Spigot build failed; see " + work.resolve("build.log"));
                } finally { if (worker.isAlive()) { worker.descendants().forEach(ProcessHandle::destroyForcibly); worker.destroyForcibly(); } }
            } else {
                UpdateStatus status = UpdateBuilder.updateProject(software).version(profile.getGameVersion())
                        .workingDirectory(work.toFile()).outputFile(output.toFile()).debugConsumer(progress::accept).execute();
                if (!status.isSuccessStatus()) throw new IOException("Server installation failed: " + status.getMessage(), status.getThrowable());
            }
        }
        return output;
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.+-]{1,100}")) throw new IllegalArgumentException("Invalid server version or loader");
        return value;
    }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }
    private static JsonElement json(String url) throws IOException {
        try (InputStream input = connection(url).getInputStream(); Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }
    private static HttpURLConnection connection(String url) throws IOException {
        URL parsed = new URL(url);
        if (!"https".equals(parsed.getProtocol())) throw new IOException("Server download must use HTTPS");
        HttpURLConnection connection = (HttpURLConnection) parsed.openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "AutoPlug-Client");
        return connection;
    }
    private static void download(String url, Path output) throws IOException {
        Path temporary = Files.createTempFile(output.getParent(), "server-", ".part");
        try {
            try (InputStream input = connection(url).getInputStream()) { Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING); }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String sha1(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) { byte[] bytes = new byte[65536]; int n; while ((n = in.read(bytes)) != -1) digest.update(bytes, 0, n); }
        StringBuilder result = new StringBuilder(); for (byte b : digest.digest()) result.append(String.format("%02x", b & 255));
        return result.toString();
    }
}
