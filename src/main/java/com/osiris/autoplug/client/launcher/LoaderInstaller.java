package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Adapters for official loader metadata and standalone installers, with no linked launcher code. */
final class LoaderInstaller {
    private final Path cache;
    LoaderInstaller(Path cache) { this.cache = cache; }

    JsonObject clientProfile(LaunchRequest request, JsonObject vanilla, Path client, Path java, Consumer<String> log) throws Exception {
        if (request.loader.equals("VANILLA")) return null;
        if (request.loader.equals("FABRIC") || request.loader.equals("QUILT")) {
            String base = metaBase(request.loader);
            String version = request.loaderVersion == null ? latestMetaLoader(base, request.version) : request.loaderVersion;
            Path file = cache.resolve("loader-metadata").resolve(request.loader.toLowerCase() + "-" + MinecraftLauncher.safeId(request.version) + "-" + MinecraftLauncher.safeId(version) + ".json");
            JsonObject profile = cachedJson(base + "/loader/" + encode(request.version) + "/" + encode(version) + "/profile/json", file);
            if (!request.version.equals(MinecraftLauncher.string(profile, "inheritsFrom", "")))
                throw new IOException("Loader metadata is for a different Minecraft version.");
            return profile;
        }
        Installer installer = forgeInstaller(request.version, request.loader, request.loaderVersion, log);
        Path home = cache.resolve("loader-installations").resolve(installer.id);
        Path marker = home.resolve("autoplug-installed.json");
        if (Files.isRegularFile(marker)) { importLibraries(home.resolve("libraries")); return LauncherFiles.readJson(marker); }
        Files.createDirectories(home);
        Path baseVersion = home.resolve("versions").resolve(request.version);
        MinecraftLauncher.linkOrCopy(client, baseVersion.resolve(request.version + ".jar"));
        LauncherFiles.writeJson(baseVersion.resolve(request.version + ".json"), vanilla);
        Path launcherProfiles = home.resolve("launcher_profiles.json");
        if (!Files.exists(launcherProfiles)) {
            JsonObject profiles = new JsonObject(); profiles.add("profiles", new JsonObject());
            LauncherFiles.writeJson(launcherProfiles, profiles);
        }
        log.accept("Installing " + request.loader + " " + installer.version + " and its client processors");
        run(java, Arrays.asList("-jar", installer.jar.toString(), "--installClient", home.toString()), home, log);
        JsonObject profile = installedClientProfile(home, request.version);
        importLibraries(home.resolve("libraries"));
        // Some old installers publish a version jar alongside their version JSON.
        Path versionDir = home.resolve("versions").resolve(profile.get("id").getAsString());
        if (Files.isDirectory(versionDir)) {
            try (Stream<Path> paths = Files.list(versionDir)) {
                for (Path path : paths.filter(Files::isRegularFile).collect(Collectors.toList()))
                    MinecraftLauncher.linkOrCopy(path, cache.resolve("versions").resolve(versionDir.getFileName()).resolve(path.getFileName()));
            }
        }
        LauncherFiles.writeJson(marker, profile);
        return profile;
    }

    PreparedLaunch server(String gameVersion, String loader, String loaderVersion, Path directory,
                          Path java, int major, Consumer<String> log) throws Exception {
        Files.createDirectories(directory);
        if (loader.equals("QUILT")) {
            String version = loaderVersion == null || loaderVersion.isEmpty() ? latestMetaLoader(metaBase(loader), gameVersion) : LaunchRequest.safeVersion(loaderVersion);
            Path installerMetadata = cache.resolve("loader-metadata").resolve("quilt-installer.json");
            JsonObject info;
            if (Files.isRegularFile(installerMetadata)) info = LauncherFiles.readJson(installerMetadata);
            else {
                JsonArray installers = JsonParser.parseString(LauncherFiles.text(metaBase(loader) + "/installer")).getAsJsonArray();
                if (installers.size() == 0) throw new IOException("Quilt did not return an installer.");
                info = installers.get(0).getAsJsonObject(); LauncherFiles.writeJson(installerMetadata, info);
            }
            Path jar = cache.resolve("installers").resolve("quilt-" + LaunchRequest.safeVersion(info.get("version").getAsString()) + ".jar");
            String sha = info.has("hashes") ? MinecraftLauncher.string(info.getAsJsonObject("hashes"), "sha1", null) : null;
            LauncherFiles.download(info.get("url").getAsString(), jar, sha, info.has("file_size") ? info.get("file_size").getAsLong() : -1, log);
            Path marker = directory.resolve(".autoplug-quilt-version");
            String key = gameVersion + ":" + version;
            if (!Files.isRegularFile(marker) || !new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).equals(key)) {
                run(java, Arrays.asList("-jar", jar.toString(), "install", "server", gameVersion, version,
                        "--install-dir=" + directory, "--download-server"), directory, log);
                Files.write(marker, key.getBytes(StandardCharsets.UTF_8));
            }
            Path launchJar = directory.resolve("quilt-server-launch.jar");
            if (!Files.isRegularFile(launchJar)) throw new IOException("Quilt installer did not produce quilt-server-launch.jar.");
            return new PreparedLaunch(java, directory, Arrays.asList("-Xmx2G", "-jar", launchJar.toString(), "nogui"), gameVersion + "-quilt-" + version, major);
        }
        if (!loader.equals("FORGE") && !loader.equals("NEOFORGE")) throw new IOException("Unsupported dedicated loader: " + loader);
        Installer installer = forgeInstaller(gameVersion, loader, loaderVersion, log);
        Path marker = directory.resolve(".autoplug-forge-version");
        if (!Files.isRegularFile(marker) || !new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).equals(installer.id)) {
            log.accept("Installing " + loader + " dedicated server");
            run(java, Arrays.asList("-jar", installer.jar.toString(), "--installServer", directory.toString()), directory, log);
            Files.write(marker, installer.id.getBytes(StandardCharsets.UTF_8));
        }
        Path libraries = directory.resolve("libraries");
        String argsName = MinecraftLauncher.osName().equals("windows") ? "win_args.txt" : "unix_args.txt";
        List<Path> argsFiles = new ArrayList<>();
        if (Files.isDirectory(libraries)) try (Stream<Path> paths = Files.walk(libraries)) {
            argsFiles = paths.filter(p -> p.getFileName().toString().equals(argsName))
                    .filter(p -> p.toString().contains(installer.version)).collect(Collectors.toList());
        }
        if (argsFiles.size() == 1) {
            List<String> arguments = new ArrayList<>(); arguments.add("-Xmx2G");
            Path custom = directory.resolve("user_jvm_args.txt");
            if (Files.isRegularFile(custom)) arguments.add("@" + custom);
            arguments.add("@" + argsFiles.get(0)); arguments.add("nogui");
            return new PreparedLaunch(java, directory, arguments, installer.id, major);
        }
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path candidate : paths.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().collect(Collectors.toList())) {
                String name = candidate.getFileName().toString();
                if ((!name.startsWith("forge-") && !name.startsWith("neoforge-")) || name.contains("installer")) continue;
                try (JarFile jar = new JarFile(candidate.toFile())) {
                    if (jar.getManifest() != null && jar.getManifest().getMainAttributes().getValue("Main-Class") != null)
                        return new PreparedLaunch(java, directory, Arrays.asList("-Xmx2G", "-jar", candidate.toString(), "nogui"), installer.id, major);
                }
            }
        }
        throw new IOException("Official " + loader + " installer did not produce an unambiguous server launch command. See autoplug-loader-install.log.");
    }

    private Installer forgeInstaller(String game, String loader, String desired, Consumer<String> log) throws Exception {
        String version = desired == null || desired.trim().isEmpty() ? null : LaunchRequest.safeVersion(desired);
        String base, coordinate;
        if (loader.equals("FORGE")) {
            if (version == null) {
                JsonObject promotions = freshJson("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json", cache.resolve("forge-promotions.json")).getAsJsonObject("promos");
                version = MinecraftLauncher.string(promotions, game + "-recommended", MinecraftLauncher.string(promotions, game + "-latest", null));
                if (version == null) throw new IOException("Forge does not publish a loader for Minecraft " + game + ".");
            }
            if (!version.startsWith(game + "-")) version = game + "-" + version;
            base = "https://maven.minecraftforge.net/";
            coordinate = "net.minecraftforge:forge:" + version + ":installer";
        } else {
            base = "https://maven.neoforged.net/releases/";
            boolean legacy = game.equals("1.20.1");
            String artifact = legacy ? "forge" : "neoforge";
            if (version == null) {
                String prefix = legacy ? game + "-" : neoPrefix(game);
                version = latestMavenVersion(base + "net/neoforged/" + artifact + "/maven-metadata.xml", prefix);
                if (version == null) throw new IOException("NeoForge does not publish a loader for Minecraft " + game + ".");
            }
            if (legacy && !version.startsWith(game + "-")) version = game + "-" + version;
            coordinate = "net.neoforged:" + artifact + ":" + version + ":installer";
        }
        String relative = MinecraftLauncher.mavenPath(coordinate);
        Path jar = LauncherFiles.child(cache.resolve("installers"), relative);
        String hash = null;
        Path checksumFile = jar.resolveSibling(jar.getFileName() + ".sha1");
        if (Files.isRegularFile(checksumFile)) hash = new String(Files.readAllBytes(checksumFile), StandardCharsets.US_ASCII).trim();
        else {
            hash = LauncherFiles.text(base + relative + ".sha1").trim().split("\\s+")[0];
            if (!hash.matches("[a-fA-F0-9]{40}")) throw new IOException("Invalid official installer checksum.");
            Files.createDirectories(checksumFile.getParent()); Files.write(checksumFile, hash.getBytes(StandardCharsets.US_ASCII));
        }
        LauncherFiles.download(base + relative, jar, hash, -1, log);
        validateInstallerGame(jar, game);
        return new Installer(loader.toLowerCase() + "-" + version, version, jar);
    }
    static String neoPrefix(String game) throws IOException {
        String[] parts = game.split("\\.");
        if (parts.length >= 2 && parts[0].equals("1")) return parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + ".";
        if (parts.length >= 2 && parts[0].matches("\\d+") && parts[1].matches("\\d+")) return parts[0] + "." + parts[1] + ".";
        throw new IOException("Choose an explicit NeoForge loader version for " + game + ".");
    }
    private String latestMavenVersion(String url, String prefix) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Path cached = cache.resolve("neoforge-" + prefix.replace('.', '-') + "versions.xml");
        String xml;
        try { xml = LauncherFiles.text(url); Files.createDirectories(cache); Files.write(cached, xml.getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { if (!Files.exists(cached)) throw e; xml = new String(Files.readAllBytes(cached), StandardCharsets.UTF_8); }
        org.w3c.dom.NodeList nodes = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getElementsByTagName("version");
        String latest = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = nodes.item(i).getTextContent();
            if (value.startsWith(prefix) && (latest == null || compareVersions(value, latest) > 0)) latest = value;
        }
        return latest;
    }
    static int compareVersions(String first, String second) {
        String[] a = first.split("[.\\-+]"); String[] b = second.split("[.\\-+]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String left = i < a.length ? a[i] : ""; String right = i < b.length ? b[i] : "";
            int result;
            if (left.matches("\\d+") && right.matches("\\d+")) result = new java.math.BigInteger(left).compareTo(new java.math.BigInteger(right));
            else if (left.isEmpty() || right.isEmpty()) result = left.isEmpty() ? 1 : -1;
            else result = left.compareTo(right);
            if (result != 0) return result;
        }
        return 0;
    }
    private static void validateInstallerGame(Path jar, String game) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("install_profile.json");
            if (entry == null) throw new IOException("Official installer has no install profile.");
            JsonObject profile;
            try (InputStream in = zip.getInputStream(entry)) { profile = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject(); }
            String actual = MinecraftLauncher.string(profile, "minecraft", "");
            if (actual.isEmpty() && profile.has("versionInfo")) actual = MinecraftLauncher.string(profile.getAsJsonObject("versionInfo"), "inheritsFrom", "");
            if (!actual.isEmpty() && !actual.equals(game)) throw new IOException("Requested loader is for Minecraft " + actual + ", not " + game + ".");
        }
    }
    private static JsonObject installedClientProfile(Path home, String game) throws IOException {
        List<JsonObject> candidates = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(home.resolve("versions"), 2)) {
            for (Path file : paths.filter(p -> p.getFileName().toString().endsWith(".json")).collect(Collectors.toList())) {
                JsonObject metadata = LauncherFiles.readJson(file);
                if (game.equals(MinecraftLauncher.string(metadata, "inheritsFrom", "")) && !game.equals(MinecraftLauncher.string(metadata, "id", ""))) candidates.add(metadata);
            }
        }
        if (candidates.size() != 1) throw new IOException("Official installer did not produce a unique client profile. See autoplug-loader-install.log.");
        return candidates.get(0);
    }
    private void importLibraries(Path source) throws IOException {
        if (!Files.isDirectory(source)) return;
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).collect(Collectors.toList()))
                MinecraftLauncher.linkOrCopy(file, LauncherFiles.child(cache.resolve("libraries"), source.relativize(file).toString()));
        }
    }
    private String latestMetaLoader(String base, String game) throws IOException {
        Path file = cache.resolve("loader-metadata").resolve((base.contains("quilt") ? "quilt" : "fabric") + "-latest-" + MinecraftLauncher.safeId(game) + ".json");
        JsonArray versions;
        try {
            versions = JsonParser.parseString(LauncherFiles.text(base + "/loader/" + encode(game))).getAsJsonArray();
            JsonObject saved = new JsonObject(); saved.add("versions", versions); LauncherFiles.writeJson(file, saved);
        } catch (IOException e) {
            if (!Files.exists(file)) throw e;
            versions = LauncherFiles.readJson(file).getAsJsonArray("versions");
        }
        for (JsonElement element : versions) {
            JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");
            if (!loader.has("stable") || loader.get("stable").getAsBoolean()) return loader.get("version").getAsString();
        }
        throw new IOException("No stable loader is available for Minecraft " + game + ". Choose a specific loader version.");
    }
    private JsonObject cachedJson(String url, Path file) throws IOException {
        if (Files.isRegularFile(file)) return LauncherFiles.readJson(file);
        JsonObject json = LauncherFiles.json(url); LauncherFiles.writeJson(file, json); return json;
    }
    private JsonObject freshJson(String url, Path file) throws IOException {
        try { JsonObject json = LauncherFiles.json(url); LauncherFiles.writeJson(file, json); return json; }
        catch (IOException e) { if (Files.exists(file)) return LauncherFiles.readJson(file); throw e; }
    }
    static void run(Path java, List<String> arguments, Path directory, Consumer<String> log) throws IOException, InterruptedException {
        Files.createDirectories(directory);
        List<String> command = new ArrayList<>(); command.add(java.toString()); command.add("-Djava.awt.headless=true"); command.addAll(arguments);
        Path output = directory.resolve("autoplug-loader-install.log");
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        java.util.concurrent.atomic.AtomicReference<IOException> outputFailure = new java.util.concurrent.atomic.AtomicReference<>();
        // Drain while the installer runs: its downloads and substeps must not disappear into a file until exit.
        Thread reader = new Thread(() -> {
            try (java.io.BufferedReader lines = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 java.io.BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                String line;
                while ((line = lines.readLine()) != null) {
                    String safe = DownloadProgress.installerOutput(line);
                    writer.write(safe); writer.newLine(); writer.flush();
                    if (log != null) log.accept(safe);
                }
            } catch (IOException e) { outputFailure.set(e); }
        }, "autoplug-loader-output");
        reader.setDaemon(true);
        reader.start();
        try {
            if (!process.waitFor(20, TimeUnit.MINUTES)) throw new IOException("Loader installation timed out. See " + output);
            reader.join(5000);
            if (reader.isAlive()) throw new IOException("Loader output did not finish. See " + output);
            if (outputFailure.get() != null) throw new IOException("Unable to record loader progress. See " + output, outputFailure.get());
            if (process.exitValue() != 0) throw new IOException("Loader installer exited with " + process.exitValue() + ". See " + output);
            if (log != null) log.accept("Official loader installer completed");
        } finally {
            if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly(); }
        }
    }
    private static String metaBase(String loader) { return loader.equals("QUILT") ? "https://meta.quiltmc.org/v3/versions" : "https://meta.fabricmc.net/v2/versions"; }
    private static String encode(String value) throws IOException { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
    private static final class Installer {
        final String id, version; final Path jar;
        Installer(String id, String version, Path jar) { this.id = id; this.version = version; this.jar = jar; }
    }
}
