package com.osiris.autoplug.client.mc.launch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Reads existing JRE release metadata, including homes supplied by AutoPlug's JRE manager. */
public final class JavaRuntimeSelector {
    private final List<Path> homes;
    private final boolean windows;

    public JavaRuntimeSelector(List<Path> homes, boolean windows) {
        this.homes = new ArrayList<>(homes);
        this.windows = windows;
    }

    public Path select(int major) throws IOException {
        return select(major, System.getProperty("os.arch"));
    }

    public Path select(int major, String requiredArchitecture) throws IOException {
        String required = architecture(requiredArchitecture);
        for (Path home : candidates()) {
            Path release = home.resolve("release");
            Path executable = home.resolve("bin").resolve(windows ? "java.exe" : "java");
            if (!Files.isRegularFile(release) || !Files.isRegularFile(executable)) continue;
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(release)) { properties.load(input); }
            String version = properties.getProperty("JAVA_VERSION", "").replace("\"", "");
            String actualArchitecture = architecture(properties.getProperty("OS_ARCH", "").replace("\"", ""));
            if (actualArchitecture.isEmpty() || !actualArchitecture.equals(required)) continue;
            String[] parts = version.split("[._+-]");
            try {
                int actual = Integer.parseInt(parts[0].equals("1") && parts.length > 1 ? parts[1] : parts[0]);
                if (actual == major) return executable;
            } catch (NumberFormatException ignored) { /* Not an identifiable runtime. */ }
        }
        throw new IOException("Minecraft requires Java " + major + " for " + required + "; configure/install a matching JRE with AutoPlug before launching");
    }

    /** AutoPlug extracts vendor archives under its jre directory; macOS archives add Contents/Home. */
    private List<Path> candidates() throws IOException {
        java.util.LinkedHashSet<Path> result = new java.util.LinkedHashSet<>();
        for (Path configured : homes) {
            Path home = configured.toAbsolutePath().normalize();
            result.add(home); result.add(home.resolve("Contents/Home"));
            if (!Files.isDirectory(home, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
            List<Path> children = new ArrayList<>();
            try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(home)) {
                int count = 0;
                for (Path entry : entries) {
                    if (++count > 128) throw new IOException("Too many entries in a configured JRE directory; configure the exact Java home");
                    if (Files.isDirectory(entry, java.nio.file.LinkOption.NOFOLLOW_LINKS)) children.add(entry);
                }
            }
            children.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()));
            for (Path child : children) { result.add(child); result.add(child.resolve("Contents/Home")); }
        }
        return new ArrayList<>(result);
    }

    private String architecture(String value) {
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        if (normalized.matches("amd64|x86_64|x64")) return "x86_64";
        if (normalized.matches("aarch64|arm64")) return "aarch64";
        if (normalized.matches("x86|i[3-6]86")) return "x86";
        if (normalized.matches("arm|arm32|aarch32")) return "arm";
        return normalized;
    }
}
