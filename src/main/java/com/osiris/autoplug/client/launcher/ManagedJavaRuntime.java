package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import com.osiris.autoplug.client.tasks.updater.java.AdoptV3API;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

/** Reuses AutoPlug's Adoptium protocol in separate per-major installations, without changing server updater settings. */
public final class ManagedJavaRuntime implements JavaResolver {
    private final Path root;
    private final LauncherDownloads downloads;
    private final Map<Integer, Path> configured = new HashMap<>();
    public ManagedJavaRuntime(Path root) throws IOException { this(root, new LauncherDownloads.HttpsTransport()); }
    public ManagedJavaRuntime(Path root, LauncherDownloads.Transport transport) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.downloads = new LauncherDownloads(this.root, transport);
    }
    public synchronized void configure(int major, Path executable) { configured.put(major, executable.toAbsolutePath().normalize()); }
    @Override public synchronized Path resolve(int major) throws Exception {
        if (major < 8 || major > 99) throw new IOException("Unsupported Java major version");
        Path setting = configured.get(major);
        if (setting != null) { verifyMajor(setting, major); return setting; }
        String executable = LauncherPlatform.current().os.equals("windows") ? "java.exe" : "java";
        Path current = Paths.get(System.getProperty("java.home"), "bin", executable);
        if (javaMajorFromRelease(Paths.get(System.getProperty("java.home"), "release")) == major && Files.isRegularFile(current)) return current;
        Path installation = root.resolve(Integer.toString(major));
        Path installed = findExecutable(installation, major, executable);
        if (installed != null) { verifyMajor(installed, major); return installed; }

        AdoptV3API api = new AdoptV3API();
        LauncherPlatform platform = LauncherPlatform.current();
        AdoptV3API.OperatingSystemType os = platform.os.equals("windows") ? AdoptV3API.OperatingSystemType.WINDOWS
                : platform.os.equals("osx") ? AdoptV3API.OperatingSystemType.MAC : AdoptV3API.OperatingSystemType.LINUX;
        AdoptV3API.OperatingSystemArchitectureType arch = architecture(platform.arch);
        JsonObject release = null;
        // Same release discovery API as TaskJavaUpdater, bounded so an unsupported major cannot poll indefinitely.
        for (int page = 0; page < 20 && release == null; page++) {
            JsonObject response;
            try { response = downloads.json(api.getReleasesUrl(page, arch, false, AdoptV3API.ImageType.JDK,
                    true, major == 8 || major == 11 || major == 17 || major == 21 || major == 25,
                    os, 50, AdoptV3API.VendorProjectType.JDK, AdoptV3API.ReleaseType.GENERAL_AVAILABILITY)); }
            catch (IOException e) { if (page == 0) throw e; break; }
            JsonArray versions = response.getAsJsonArray("versions");
            if (versions == null || versions.size() == 0) break;
            for (JsonElement item : versions) if (item.getAsJsonObject().get("major").getAsInt() == major) { release = item.getAsJsonObject(); break; }
        }
        if (release == null) throw new IOException("No Java " + major + " release available; configure an installed Java path in Settings");
        String url = api.getVersionInformationUrl(release.get("semver").getAsString(), arch, false, AdoptV3API.ImageType.JDK,
                true, major == 8 || major == 11 || major == 17 || major == 21 || major == 25,
                os, 20, AdoptV3API.VendorProjectType.JDK, AdoptV3API.ReleaseType.GENERAL_AVAILABILITY);
        JsonArray details = JsonParser.parseString(downloads.text(url, 16 * 1024 * 1024)).getAsJsonArray();
        JsonObject binaryPackage = null;
        for (JsonElement item : details) {
            JsonObject detail = item.getAsJsonObject();
            if (detail.has("version_data") && detail.getAsJsonObject("version_data").get("major").getAsInt() != major) continue;
            JsonArray binaries = detail.getAsJsonArray("binaries");
            if (binaries != null && binaries.size() > 0) { binaryPackage = binaries.get(0).getAsJsonObject().getAsJsonObject("package"); break; }
        }
        if (binaryPackage == null) throw new IOException("No matching Java binary found");
        String filename = binaryPackage.get("name").getAsString();
        String hash = binaryPackage.get("checksum").getAsString();
        if (!hash.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid Java SHA-256 metadata");
        Path archive = downloads.download(binaryPackage.get("link").getAsString(), "archives/" + filename, hash, "SHA-256", binaryPackage.get("size").getAsLong());
        Path staging = downloads.resolve(major + "/.staging-" + UUID.randomUUID());
        Files.createDirectories(staging);
        if (filename.endsWith(".zip")) LauncherDownloads.extractZip(archive, staging, Collections.emptyList());
        else if (filename.endsWith(".tar.gz")) extractTarGz(archive, staging);
        else throw new IOException("Unsupported Java archive format");
        installed = findExecutable(staging, major, executable);
        if (installed == null) throw new IOException("Java archive contains no matching runtime");
        if (!platform.os.equals("windows")) installed.toFile().setExecutable(true, false);
        verifyMajor(installed, major);
        Path relativeExecutable = staging.relativize(installed);
        Path finalDirectory = downloads.resolve(major + "/" + hash);
        if (Files.exists(finalDirectory)) {
            Path previous = finalDirectory.resolve(relativeExecutable); verifyMajor(previous, major); return previous;
        }
        try { Files.move(staging, finalDirectory, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(staging, finalDirectory); }
        return finalDirectory.resolve(relativeExecutable);
    }
    static AdoptV3API.OperatingSystemArchitectureType architecture(String value) throws IOException {
        for (AdoptV3API.OperatingSystemArchitectureType arch : AdoptV3API.OperatingSystemArchitectureType.values())
            if (arch.name().equalsIgnoreCase(value)) return arch;
        throw new IOException("Unsupported Java runtime architecture: " + value);
    }
    static Path findExecutable(Path folder, int major, String executable) throws IOException {
        if (!Files.isDirectory(folder)) return null;
        try (java.util.stream.Stream<Path> files = Files.walk(folder, 5)) {
            Iterator<Path> iterator = files.filter(path -> !folder.relativize(path).toString().contains(".staging-")
                    && path.getFileName().toString().equals("release") && Files.isRegularFile(path)).iterator();
            while (iterator.hasNext()) { Path release = iterator.next(); Path candidate = release.getParent().resolve("bin/" + executable);
                if (javaMajorFromRelease(release) == major && Files.isRegularFile(candidate)) return candidate; }
        }
        return null;
    }
    static int javaMajorFromRelease(Path release) throws IOException {
        if (!Files.isRegularFile(release)) return -1;
        Properties properties = new Properties(); try (Reader in = Files.newBufferedReader(release, StandardCharsets.UTF_8)) { properties.load(in); }
        String version = properties.getProperty("JAVA_VERSION", "").replace("\"", "");
        Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)").matcher(version);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
    public static void verifyMajor(Path executable, int expected) throws IOException, InterruptedException {
        if (!Files.isRegularFile(executable)) throw new IOException("Configured Java executable does not exist");
        Process process = new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("Java version probe timed out"); }
        String text; try (InputStream in = process.getInputStream()) { text = new String(LauncherDownloads.readBounded(in, 16384), StandardCharsets.UTF_8); }
        Matcher matcher = Pattern.compile("version \\\"(?:1\\.)?(\\d+)").matcher(text);
        if (process.exitValue() != 0 || !matcher.find() || Integer.parseInt(matcher.group(1)) != expected)
            throw new IOException("Selected runtime is not Java " + expected);
    }
    static void extractTarGz(Path archive, Path directory) throws IOException {
        List<Path[]> pendingLinks = new ArrayList<>(); List<Boolean> symbolic = new ArrayList<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
            TarArchiveEntry entry; long total = 0; int count = 0;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (++count > 200000) throw new IOException("Runtime archive has too many entries");
                Path target = LauncherDownloads.safeResolve(directory, entry.getName());
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                if (entry.isLink() || entry.isSymbolicLink()) {
                    String name = entry.getLinkName();
                    if (name.contains(":") || name.contains("\\") || Paths.get(name).isAbsolute()) throw new IOException("Unsafe runtime archive link");
                    Path linkTarget = (entry.isSymbolicLink() ? target.getParent() : directory).resolve(name).toAbsolutePath().normalize();
                    if (!linkTarget.startsWith(directory.toAbsolutePath().normalize())) throw new IOException("Runtime link escapes installation");
                    pendingLinks.add(new Path[]{target, linkTarget}); symbolic.add(entry.isSymbolicLink()); continue;
                }
                if (!entry.isFile()) throw new IOException("Unsupported runtime archive entry");
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = tar.read(buffer)) != -1) { total += n; if (total > 1024L * 1024 * 1024) throw new IOException("Runtime archive exceeds safety limit"); out.write(buffer, 0, n); }
                }
                if ((entry.getMode() & 0111) != 0) target.toFile().setExecutable(true, false);
            }
        }
        // Create links after ordinary files so archive entries cannot write through them.
        for (int i = 0; i < pendingLinks.size(); i++) {
            Path target = pendingLinks.get(i)[0], linkTarget = pendingLinks.get(i)[1];
            LauncherDownloads.rejectLinks(target); LauncherDownloads.rejectLinks(linkTarget);
            if (!Files.exists(linkTarget)) throw new IOException("Runtime link target is missing");
            Files.createDirectories(target.getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Duplicate runtime archive link");
            if (symbolic.get(i)) {
                try { Files.createSymbolicLink(target, target.getParent().relativize(linkTarget)); }
                catch (IOException | UnsupportedOperationException e) {
                    if (!Files.isRegularFile(linkTarget)) throw new IOException("Cannot create runtime directory link", e);
                    Files.copy(linkTarget, target);
                }
            } else Files.createLink(target, linkTarget);
        }
    }
}
