package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Reads official Forge/NeoForge installer metadata and runs its declared client-side processors. */
final class LoaderInstaller {
    private final LauncherDownloads downloads;
    private final LauncherPlatform platform;
    private final JavaResolver javaResolver;
    LoaderInstaller(LauncherDownloads downloads, LauncherPlatform platform, JavaResolver javaResolver) {
        this.downloads = downloads; this.platform = platform; this.javaResolver = javaResolver;
    }
    JsonObject install(String minecraft, String loader, String requestedVersion, Path client, int javaMajor) throws Exception {
        String version = requestedVersion, repository, group, artifact;
        if ("FORGE".equals(loader)) {
            repository = "https://maven.minecraftforge.net/"; group = "net.minecraftforge"; artifact = "forge";
            if (version == null) {
                JsonObject promos = downloads.json("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json").getAsJsonObject("promos");
                String key = promos.has(minecraft + "-recommended") ? minecraft + "-recommended" : minecraft + "-latest";
                if (!promos.has(key)) throw new IOException("Forge has no release for Minecraft " + minecraft);
                version = promos.get(key).getAsString();
            }
            if (!version.startsWith(minecraft + "-")) version = minecraft + "-" + version;
        } else {
            repository = "https://maven.neoforged.net/releases/";
            // NeoForge's original 1.20.1 releases used the forge artifact; later releases use neoforge.
            group = "net.neoforged"; artifact = "1.20.1".equals(minecraft) ? "forge" : "neoforge";
            if (version == null) version = selectNeoForgeVersion(downloads.text(repository + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml", 8 * 1024 * 1024), minecraft);
            if (artifact.equals("forge") && !version.startsWith(minecraft + "-")) version = minecraft + "-" + version;
        }
        NativeLauncher.validateVersion(version);
        String coordinate = group + ":" + artifact + ":" + version + ":installer";
        String installerPath = NativeLauncher.mavenPath(coordinate);
        String installerUrl = repository + installerPath;
        String sha1 = NativeLauncher.checksum(downloads.text(installerUrl + ".sha1", 4096));
        Path installer = downloads.download(installerUrl, "loaders/" + loader.toLowerCase(Locale.ROOT) + "/" + version + "/installer.jar", sha1, "SHA-1", -1);
        Path root = downloads.resolve("loaders/" + loader.toLowerCase(Locale.ROOT) + "/" + version + "/work/.marker").getParent();
        Files.createDirectories(root);
        JsonObject profile = zipJson(installer, "install_profile.json");
        JsonObject versionMetadata = profile.has("versionInfo") ? profile.getAsJsonObject("versionInfo") : zipJson(installer,
                NativeLauncher.string(profile, "json", "/version.json").replaceFirst("^/", ""));
        String inherited = NativeLauncher.string(versionMetadata, "inheritsFrom", NativeLauncher.string(profile, "minecraft", minecraft));
        if (!minecraft.equals(inherited)) throw new IOException("Loader installer targets a different Minecraft version");
        if (profile.has("libraries")) downloadLibraries(installer, profile.getAsJsonArray("libraries"), true);
        if (profile.has("install")) {
            // Old Forge installers embed the universal library instead of declaring processors.
            JsonObject installation = profile.getAsJsonObject("install");
            String maven = installation.get("path").getAsString();
            Path target = downloads.resolve("libraries/" + NativeLauncher.mavenPath(maven));
            extractEntry(installer, installation.get("filePath").getAsString(), target);
        }
        Path completed = root.resolve("client-installation.json");
        Map<String, String> data = Collections.emptyMap();
        if (profile.has("processors")) {
            data = installerData(profile, installer, root, client, minecraft);
            if (!completedInstallationValid(completed, sha1)) {
                Path java = javaResolver.resolve(javaMajor);
                for (JsonElement element : profile.getAsJsonArray("processors")) {
                    JsonObject processor = element.getAsJsonObject();
                    if (!runsOnClient(processor)) continue;
                    runProcessor(processor, data, java, root);
                }
            }
        }
        if (versionMetadata.has("libraries")) downloadLibraries(installer, versionMetadata.getAsJsonArray("libraries"), false);
        saveCompletedInstallation(completed, sha1, data, versionMetadata);
        return versionMetadata;
    }
    void downloadLibraries(Path installer, JsonArray libraries, boolean deferGenerated) throws IOException {
        for (JsonElement element : libraries) {
            JsonObject library = element.getAsJsonObject(); if (!platform.allows(library)) continue;
            JsonObject artifact = library.has("downloads") ? library.getAsJsonObject("downloads").getAsJsonObject("artifact") : null;
            if (artifact == null) {
                String path = NativeLauncher.mavenPath(library.get("name").getAsString());
                String repo = NativeLauncher.string(library, "url", "https://libraries.minecraft.net/");
                if (repo.startsWith("http://")) repo = "https://" + repo.substring(7);
                String url = repo + (repo.endsWith("/") ? "" : "/") + path;
                downloads.download(url, "libraries/" + path, NativeLauncher.checksum(downloads.text(url + ".sha1", 4096)), "SHA-1", -1);
                continue;
            }
            String path = artifact.get("path").getAsString(); Path target = downloads.resolve("libraries/" + path);
            String hash = NativeLauncher.string(artifact, "sha1", null); long size = artifact.has("size") ? artifact.get("size").getAsLong() : -1;
            if (LauncherDownloads.valid(target, hash, "SHA-1", size)) continue;
            if (hasEntry(installer, "maven/" + path)) {
                extractEntry(installer, "maven/" + path, target);
                if (!LauncherDownloads.valid(target, hash, "SHA-1", size)) { Files.deleteIfExists(target); throw new IOException("Embedded loader library failed integrity verification"); }
            } else {
                String url = NativeLauncher.string(artifact, "url", "");
                if (url.isEmpty()) {
                    // This is a processor output; it is checked after the processor completes.
                    if (!deferGenerated) throw new IOException("Loader processor did not produce a required client library");
                    continue;
                }
                downloads.download(url, "libraries/" + path, hash, "SHA-1", size);
            }
        }
    }
    boolean completedInstallationValid(Path receipt, String installerSha1) throws IOException {
        if (!Files.isRegularFile(receipt)) return false;
        try {
            JsonObject completed = NativeLauncher.readJson(receipt);
            if (!installerSha1.equals(NativeLauncher.string(completed, "installerSha1", ""))) return false;
            JsonObject files = completed.getAsJsonObject("files"); if (files == null || files.size() == 0) return false;
            for (Map.Entry<String, JsonElement> entry : files.entrySet())
                if (!LauncherDownloads.valid(downloads.resolve(entry.getKey()), entry.getValue().getAsString(), "SHA-256", -1)) return false;
            return true;
        } catch (RuntimeException e) { return false; }
    }
    void saveCompletedInstallation(Path receipt, String installerSha1, Map<String,String> data, JsonObject version) throws IOException {
        if (data.containsKey("PATCHED")) {
            Path patched = Paths.get(data.get("PATCHED")).toAbsolutePath().normalize();
            if (!patched.startsWith(downloads.getRoot()) || !Files.isRegularFile(patched) || Files.size(patched) == 0)
                throw new IOException("Loader processor did not produce the patched client");
        }
        JsonObject files = new JsonObject();
        for (String value : data.values()) {
            try {
                Path path = Paths.get(value).toAbsolutePath().normalize();
                if (path.startsWith(downloads.getRoot()) && Files.isRegularFile(path))
                    files.addProperty(downloads.getRoot().relativize(path).toString().replace(File.separatorChar, '/'), LauncherDownloads.hash(path, "SHA-256"));
            } catch (InvalidPathException ignored) { /* A quoted installer scalar is not a file. */ }
        }
        if (version.has("libraries")) for (JsonElement e : version.getAsJsonArray("libraries")) {
            JsonObject library = e.getAsJsonObject();
            if (!platform.allows(library) || !library.has("downloads") || !library.getAsJsonObject("downloads").has("artifact")) continue;
            JsonObject artifact = library.getAsJsonObject("downloads").getAsJsonObject("artifact");
            if (!NativeLauncher.string(artifact, "url", "").isEmpty()) continue;
            String path = "libraries/" + artifact.get("path").getAsString();
            Path file = downloads.resolve(path); if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new IOException("Required generated loader library is missing or empty");
            files.addProperty(path, LauncherDownloads.hash(file, "SHA-256"));
        }
        JsonObject completed = new JsonObject(); completed.addProperty("installerSha1", installerSha1); completed.add("files", files);
        Path temporary = receipt.resolveSibling(receipt.getFileName() + ".part-" + UUID.randomUUID());
        try {
            Files.write(temporary, completed.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            Files.move(temporary, receipt, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    Map<String, String> installerData(JsonObject profile, Path installer, Path root, Path client, String minecraft) throws IOException {
        Map<String, String> data = new HashMap<>();
        // ROOT is the installation root, not the processor working directory. Current NeoForge
        // extracts embedded Minecraft libraries into {ROOT}/libraries, which must match LIBRARY_DIR.
        data.put("SIDE", "client"); data.put("ROOT", downloads.getRoot().toString()); data.put("LIBRARY_DIR", downloads.resolve("libraries/.marker").getParent().toString());
        data.put("INSTALLER", installer.toString()); data.put("MINECRAFT_JAR", client.toString()); data.put("MINECRAFT_VERSION", minecraft);
        if (profile.has("data")) for (Map.Entry<String, JsonElement> entry : profile.getAsJsonObject("data").entrySet()) {
            JsonObject sides = entry.getValue().getAsJsonObject(); if (!sides.has("client")) continue;
            String value = sides.get("client").getAsString();
            if (value.startsWith("/") && !value.startsWith("//")) {
                Path target = LauncherDownloads.safeResolve(root.resolve("installer-data"), value.substring(1));
                extractEntry(installer, value.substring(1), target); value = target.toString();
            } else value = expression(value, data);
            data.put(entry.getKey(), value);
        }
        return data;
    }
    String expression(String value, Map<String, String> data) throws IOException {
        if (value.startsWith("'") && value.endsWith("'")) value = value.substring(1, value.length() - 1);
        Matcher placeholders = Pattern.compile("\\{([^}]+)}").matcher(value); StringBuffer output = new StringBuffer();
        while (placeholders.find()) {
            String replacement = data.get(placeholders.group(1));
            if (replacement == null) throw new IOException("Unknown installer variable: " + placeholders.group(1));
            placeholders.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        placeholders.appendTail(output); value = output.toString();
        Matcher maven = Pattern.compile("\\[([^]\\[]+)]").matcher(value); output = new StringBuffer();
        while (maven.find()) maven.appendReplacement(output, Matcher.quoteReplacement(downloads.resolve("libraries/" + NativeLauncher.mavenPath(maven.group(1))).toString()));
        maven.appendTail(output); return output.toString();
    }
    void runProcessor(JsonObject processor, Map<String, String> data, Path java, Path root) throws Exception {
        if (outputsValid(processor, data)) return;
        Path jar = downloads.resolve("libraries/" + NativeLauncher.mavenPath(processor.get("jar").getAsString()));
        if (!Files.isRegularFile(jar)) throw new IOException("Installer processor library is missing");
        List<String> classpath = new ArrayList<>(); classpath.add(jar.toString());
        if (processor.has("classpath")) for (JsonElement dependency : processor.getAsJsonArray("classpath")) {
            Path file = downloads.resolve("libraries/" + NativeLauncher.mavenPath(dependency.getAsString()));
            if (!Files.isRegularFile(file)) throw new IOException("Installer processor dependency is missing"); classpath.add(file.toString());
        }
        String main;
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            if (jarFile.getManifest() == null) throw new IOException("Installer processor has no manifest");
            main = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        }
        if (main == null || !main.matches("[A-Za-z0-9_.$]+")) throw new IOException("Invalid installer processor main class");
        List<String> command = new ArrayList<>(Arrays.asList(java.toString(), "-cp", String.join(File.pathSeparator, classpath), main));
        if (processor.has("args")) for (JsonElement argument : processor.getAsJsonArray("args")) command.add(expression(argument.getAsString(), data));
        Path log = root.resolve("processor-" + UUID.randomUUID() + ".log");
        LauncherDownloads.checkInterrupted();
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) throw new IOException("Loader processor timed out");
            if (process.exitValue() != 0) throw new IOException("Loader processor failed (exit " + process.exitValue() + "); see " + log.getFileName());
            if (processor.has("outputs") && !outputsValid(processor, data)) throw new IOException("Loader processor output failed integrity verification");
        } catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw cancelled; }
        finally { if (process.isAlive()) { process.descendants().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); }); process.destroyForcibly(); } }
    }
    boolean outputsValid(JsonObject processor, Map<String, String> data) throws IOException {
        if (!processor.has("outputs") || processor.getAsJsonObject("outputs").size() == 0) return false;
        boolean valid = true;
        for (Map.Entry<String, JsonElement> output : processor.getAsJsonObject("outputs").entrySet()) {
            Path file = Paths.get(expression(output.getKey(), data)).toAbsolutePath().normalize();
            if (!file.startsWith(downloads.getRoot())) throw new IOException("Installer output escapes the launcher cache");
            LauncherDownloads.rejectLinks(file);
            String expected = expression(output.getValue().getAsString(), data);
            if (!expected.matches("[a-fA-F0-9]{40}")) throw new IOException("Invalid installer output checksum");
            if (!LauncherDownloads.valid(file, expected, "SHA-1", -1)) valid = false;
        }
        return valid;
    }
    static boolean runsOnClient(JsonObject processor) {
        if (!processor.has("sides")) return true;
        for (JsonElement side : processor.getAsJsonArray("sides")) if ("client".equals(side.getAsString())) return true;
        return false;
    }
    static JsonObject zipJson(Path archive, String name) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(name); if (entry == null) throw new IOException("Loader installer metadata is missing: " + name);
            try (InputStream input = zip.getInputStream(entry)) { return JsonParser.parseString(new String(LauncherDownloads.readBounded(input, 32 * 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject(); }
        }
    }
    static boolean hasEntry(Path archive, String name) throws IOException { try (ZipFile zip = new ZipFile(archive.toFile())) { return zip.getEntry(name) != null; } }
    static void extractEntry(Path archive, String name, Path destination) throws IOException {
        LauncherDownloads.rejectLinks(destination); Files.createDirectories(destination.getParent());
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(name); if (entry == null || entry.isDirectory()) throw new IOException("Loader installer entry is missing");
            try (InputStream in = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[65536]; long count = 0; int n;
                while ((n = in.read(buffer)) != -1) { count += n; if (count > 256L * 1024 * 1024) throw new IOException("Embedded loader library exceeds safety limit"); out.write(buffer, 0, n); }
            }
        }
    }
    static String selectNeoForgeVersion(String xml, String minecraft) throws Exception {
        String[] parts = minecraft.split("\\.");
        if (parts.length < 2 || !parts[0].matches("[0-9]+") || !parts[1].matches("[0-9]+")) throw new IOException("Unsupported NeoForge game version");
        String prefix = "1.20.1".equals(minecraft) ? "1.20.1-" : "1".equals(parts[0])
                ? parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + "."
                : parts[0] + "." + parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + ".";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList versions = document.getElementsByTagName("version"); String selected = null, preview = null;
        for (int i = 0; i < versions.getLength(); i++) {
            String candidate = versions.item(i).getTextContent().trim();
            if (!candidate.startsWith(prefix)) continue;
            if (candidate.matches(".*-(alpha|beta|rc).*")) { if (preview == null || compareVersions(candidate, preview) > 0) preview = candidate; }
            else if (selected == null || compareVersions(candidate, selected) > 0) selected = candidate;
        }
        if (selected == null) selected = preview;
        if (selected == null) throw new IOException("NeoForge has no release for Minecraft " + minecraft); return selected;
    }
    static int compareVersions(String a, String b) {
        String[] left = a.split("[.-]"), right = b.split("[.-]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            String x = i < left.length ? left[i] : "", y = i < right.length ? right[i] : ""; int result;
            try { result = Integer.compare(Integer.parseInt(x), Integer.parseInt(y)); } catch (NumberFormatException e) { result = x.compareTo(y); }
            if (result != 0) return result;
        }
        return 0;
    }
}
