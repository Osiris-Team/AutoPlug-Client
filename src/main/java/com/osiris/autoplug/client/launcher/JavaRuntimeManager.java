package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.java.AdoptV3API;
import com.osiris.autoplug.client.utils.UtilsCrypto;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Version-scoped extension of AutoPlug's Adoptium JRE manager, without changing server settings. */
public class JavaRuntimeManager {
    private final Path runtimeRoot;
    private final Map<Integer, Path> overrides = new HashMap<>();

    public JavaRuntimeManager(Path runtimeRoot) { this.runtimeRoot = runtimeRoot.toAbsolutePath().normalize(); }
    public Path getRuntimeRoot() { return runtimeRoot; }

    /** Configure an existing Java executable. The version is checked when resolved. */
    public synchronized void setRuntime(int major, Path executable) {
        if (executable == null) overrides.remove(major);
        else overrides.put(major, executable.toAbsolutePath().normalize());
    }

    public synchronized Path resolve(int requiredMajor, Consumer<String> progress) throws Exception {
        if (requiredMajor < 8) throw new IOException("Unsupported Java runtime version: " + requiredMajor);
        Consumer<String> log = progress == null ? value -> { } : progress;
        Path custom = overrides.get(requiredMajor);
        if (custom != null) {
            if (!Files.isRegularFile(custom) || majorVersion(custom) != requiredMajor)
                throw new IOException("Configured runtime is not Java " + requiredMajor + ": " + custom);
            return custom;
        }
        Path current = Paths.get(System.getProperty("java.home"), "bin", executableName());
        if (Files.isRegularFile(current) && majorVersion(current) == requiredMajor) return current;
        Path destination = runtimeRoot.resolve("java-" + requiredMajor);
        Path cached = findJava(destination, requiredMajor);
        if (cached != null) return cached;
        // Read the existing server runtime, but never replace it or modify its global configuration.
        Path legacy = findJava(Paths.get(System.getProperty("user.dir"), "autoplug", "system", "jre"), requiredMajor);
        if (legacy != null) return legacy;
        log.accept("Installing Java " + requiredMajor + " using AutoPlug's Adoptium provider");
        AdoptV3API api = new AdoptV3API();
        AdoptV3API.OperatingSystemArchitectureType arch = architecture();
        AdoptV3API.OperatingSystemType os = operatingSystem();
        AtomicReference<JsonObject> release = new AtomicReference<>();
        boolean lts = true;
        for (boolean onlyLts : new boolean[]{true, false}) {
            api.getReleases(arch, false, AdoptV3API.ImageType.JDK, true, onlyLts, os, 50,
                    AdoptV3API.VendorProjectType.JDK, AdoptV3API.ReleaseType.GENERAL_AVAILABILITY, page -> {
                        for (JsonElement element : page.getAsJsonArray("versions")) {
                            JsonObject candidate = element.getAsJsonObject();
                            if (candidate.get("major").getAsInt() == requiredMajor) { release.set(candidate); return false; }
                        }
                        return true;
                    });
            if (release.get() != null) { lts = onlyLts; break; }
        }
        if (release.get() == null) throw new IOException("Adoptium has no supported Java " + requiredMajor + " build for this computer.");
        JsonArray details = api.getVersionInformation(release.get().get("semver").getAsString(), arch, false,
                AdoptV3API.ImageType.JDK, true, lts, os, 20, AdoptV3API.VendorProjectType.JDK,
                AdoptV3API.ReleaseType.GENERAL_AVAILABILITY);
        if (details.size() == 0) throw new IOException("No Java " + requiredMajor + " package was returned.");
        JsonObject binary = details.get(0).getAsJsonObject().getAsJsonArray("binaries").get(0).getAsJsonObject();
        JsonObject pack = binary.getAsJsonObject("package");
        String checksum = pack.get("checksum").getAsString();
        String name = pack.get("name").getAsString();
        Path archive = LauncherFiles.child(runtimeRoot.resolve("downloads"), name);
        if (Files.exists(archive) && !checksum.equalsIgnoreCase(UtilsCrypto.fastSHA256(archive.toFile()))) Files.delete(archive);
        LauncherFiles.download(pack.get("link").getAsString(), archive, null, pack.get("size").getAsLong(), log);
        if (!checksum.equalsIgnoreCase(UtilsCrypto.fastSHA256(archive.toFile()))) {
            Files.deleteIfExists(archive);
            throw new IOException("Java runtime checksum mismatch.");
        }
        Files.createDirectories(runtimeRoot);
        Path staging = Files.createTempDirectory(runtimeRoot, ".java-" + requiredMajor + "-");
        try {
            extract(archive, staging);
            Path executable = findJava(staging, requiredMajor);
            if (executable == null) throw new IOException("Downloaded archive does not contain Java " + requiredMajor + ".");
            Path relative = staging.relativize(executable);
            if (Files.exists(destination)) deleteTree(destination);
            LauncherFiles.move(staging, destination);
            return destination.resolve(relative);
        } finally { if (Files.exists(staging)) deleteTree(staging); }
    }

    static int majorVersion(Path java) throws IOException {
        Path home = java.getParent().getParent();
        Path release = home.resolve("release");
        if (Files.isRegularFile(release)) {
            Properties values = new Properties();
            try (InputStream in = Files.newInputStream(release)) { values.load(in); }
            return parseMajor(values.getProperty("JAVA_VERSION", ""));
        }
        // Some Java 8 distributions place the executable in jre/bin and release in the JDK root.
        if (home.getFileName().toString().equals("jre") && Files.isRegularFile(home.getParent().resolve("release")))
            return majorVersion(home.getParent().resolve("bin").resolve(executableName()));
        return -1;
    }
    static int parseMajor(String version) {
        String value = version.replace("\"", "");
        if (value.startsWith("1.")) value = value.substring(2);
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("^(\\d+)").matcher(value);
        return match.find() ? Integer.parseInt(match.group(1)) : -1;
    }
    private static Path findJava(Path root, int major) throws IOException {
        if (!Files.isDirectory(root)) return null;
        try (Stream<Path> paths = Files.walk(root, 6)) {
            for (Path candidate : (Iterable<Path>) paths.filter(p -> p.getFileName().toString().equals(executableName()))::iterator)
                if (Files.isRegularFile(candidate) && majorVersion(candidate) == major) return candidate;
        }
        return null;
    }
    static String executableName() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java"; }
    private static AdoptV3API.OperatingSystemType operatingSystem() throws IOException {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return AdoptV3API.OperatingSystemType.WINDOWS;
        if (name.contains("mac")) return AdoptV3API.OperatingSystemType.MAC;
        if (name.contains("linux")) return AdoptV3API.OperatingSystemType.LINUX;
        throw new IOException("Automatic Java installation is unavailable on " + name + "; configure an existing runtime.");
    }
    private static AdoptV3API.OperatingSystemArchitectureType architecture() throws IOException {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64")) return AdoptV3API.OperatingSystemArchitectureType.X64;
        if (arch.equals("aarch64") || arch.equals("arm64")) return AdoptV3API.OperatingSystemArchitectureType.AARCH64;
        if (arch.equals("x86") || arch.equals("i386")) return AdoptV3API.OperatingSystemArchitectureType.X86;
        for (AdoptV3API.OperatingSystemArchitectureType value : AdoptV3API.OperatingSystemArchitectureType.values())
            if (value.name().equalsIgnoreCase(arch)) return value;
        throw new IOException("Unsupported Java architecture " + arch + "; configure an existing runtime.");
    }
    static void extract(Path archive, Path target) throws IOException {
        Files.createDirectories(target);
        if (archive.getFileName().toString().endsWith(".zip")) {
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path destination = LauncherFiles.child(target, entry.getName());
                    if (entry.isDirectory()) Files.createDirectories(destination);
                    else { Files.createDirectories(destination.getParent()); Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        } else {
            List<Path[]> links = new ArrayList<>();
            try (TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextTarEntry()) != null) {
                    Path destination = LauncherFiles.child(target, entry.getName());
                    if (entry.isDirectory()) Files.createDirectories(destination);
                    else if (entry.isSymbolicLink() || entry.isLink()) {
                        Path linkTarget = entry.isLink() ? LauncherFiles.child(target, entry.getLinkName())
                                : destination.getParent().resolve(entry.getLinkName()).normalize();
                        if (!linkTarget.startsWith(target.toAbsolutePath().normalize())) throw new IOException("Unsafe runtime archive link.");
                        links.add(new Path[]{destination, linkTarget});
                    } else if (entry.isFile()) {
                        Files.createDirectories(destination.getParent());
                        Files.copy(tar, destination, StandardCopyOption.REPLACE_EXISTING);
                        if ((entry.getMode() & 0111) != 0) destination.toFile().setExecutable(true, false);
                    }
                }
            }
            for (Path[] link : links) {
                Files.createDirectories(link[0].getParent());
                Files.createSymbolicLink(link[0], link[0].getParent().relativize(link[1]));
            }
        }
    }
    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            Path[] paths = stream.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path path : paths) Files.deleteIfExists(path);
        }
    }
}
