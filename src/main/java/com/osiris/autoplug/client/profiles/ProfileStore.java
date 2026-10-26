/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/** Managed profiles live below root/instances; no process-wide user.dir or config mutation. */
public final class ProfileStore {
    /** UI and CLI may hold separate stores for the same root; defaults are one shared transaction. */
    private static final ConcurrentMap<Path, Object> ACTIVE_LOCKS = new ConcurrentHashMap<>();
    private final Path root, metadata, instances;
    private final SharedArtifactCache cache;
    private final Object activeLock;
    public ProfileStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        activeLock = ACTIVE_LOCKS.computeIfAbsent(this.root, path -> new Object());
        metadata = this.root.resolve("profiles"); instances = this.root.resolve("instances");
        Files.createDirectories(metadata); Files.createDirectories(instances);
        if (Files.isSymbolicLink(this.root) || Files.isSymbolicLink(metadata) || Files.isSymbolicLink(instances)) throw new IOException("Managed profile roots cannot be links");
        cache = new SharedArtifactCache(this.root.resolve("cache"));
    }
    public static ProfileStore defaultStore() throws IOException { return new ProfileStore(Paths.get(System.getProperty("user.home"), ".autoplug")); }
    public Path getRoot() { return root; }
    public SharedArtifactCache getCache() { return cache; }
    public synchronized Profile create(String id, Profile.Type type, String version, String loader, boolean template) throws IOException {
        Profile profile = new Profile(id, type, version, loader, instances.resolve(id), template);
        if (Files.exists(metadata.resolve(id + ".properties")) || Files.exists(profile.directoryPath)) throw new IOException("Profile already exists: " + id);
        ProfileFiles.contained(instances, profile.directoryPath);
        Files.createDirectory(profile.directoryPath);
        Files.createDirectories(profile.assetsDirectory());
        try { save(profile); } catch (IOException exception) { ProfileFiles.deleteTree(instances, profile.directoryPath); throw exception; }
        return profile;
    }
    public synchronized Profile get(String id) throws IOException {
        validateId(id);
        Path file = ProfileFiles.contained(metadata, metadata.resolve(id + ".properties"));
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) { values.load(input); }
        catch (IllegalArgumentException malformed) { throw new IOException("Invalid profile metadata encoding: " + id, malformed); }
        try {
            for (String key : Arrays.asList("id", "type", "gameVersion", "loader", "isTemplate")) if (values.getProperty(key) == null) throw new IllegalArgumentException("Missing " + key);
            if (!id.equals(values.getProperty("id")) || !values.getProperty("isTemplate").matches("true|false")) throw new IllegalArgumentException("Inconsistent profile id or template value");
            Profile profile = new Profile(id, Profile.Type.valueOf(values.getProperty("type")), values.getProperty("gameVersion"), values.getProperty("loader"), instances.resolve(id), Boolean.parseBoolean(values.getProperty("isTemplate")));
            ProfileFiles.contained(instances, profile.directoryPath);
            return profile;
        } catch (RuntimeException exception) { throw new IOException("Invalid profile metadata: " + id, exception); }
    }
    public synchronized List<Profile> list() throws IOException {
        List<Profile> profiles = new ArrayList<>();
        try (Stream<Path> files = Files.list(metadata)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".properties"))::iterator) {
                String name = file.getFileName().toString(); profiles.add(get(name.substring(0, name.length() - 11)));
            }
        }
        profiles.sort(Comparator.comparing(p -> p.id)); return Collections.unmodifiableList(profiles);
    }
    public synchronized Profile clone(String sourceId, String targetVersion) throws IOException {
        String base = sourceId + "-" + targetVersion.replaceAll("[^a-zA-Z0-9_-]", "-");
        if (base.length() > 70) base = base.substring(0, 70);
        String id = base; int count = 2;
        while (Files.exists(metadata.resolve(id + ".properties")) || Files.exists(instances.resolve(id))) id = base + "-" + count++;
        return clone(sourceId, id, targetVersion);
    }
    public synchronized Profile clone(String sourceId, String newId, String targetVersion) throws IOException {
        Profile source = get(sourceId);
        Profile target = create(newId, source.type, targetVersion, source.loader, false);
        try {
            Files.walkFileTree(source.directoryPath, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(directory)) throw new IOException("Cannot clone linked directory");
                    Path relative = source.directoryPath.relativize(directory);
                    if (relative.toString().equals(".autoplug-transactions")) return FileVisitResult.SKIP_SUBTREE;
                    Files.createDirectories(target.directoryPath.resolve(relative)); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile() || Files.isSymbolicLink(file)) throw new IOException("Cannot clone linked/special file: " + file);
                    String filename = file.getFileName().toString();
                    if (filename.equals("credentials.json") || filename.equals("launcher_accounts.json") || filename.endsWith(".lock")) return FileVisitResult.CONTINUE;
                    Path destination = target.directoryPath.resolve(source.directoryPath.relativize(file));
                    if (filename.endsWith(".jar") || filename.endsWith(".jar.disabled")) cache.link(cache.store(file), destination);
                    else Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
            return target;
        } catch (IOException failure) { delete(newId); throw failure; }
    }
    public synchronized void delete(String id) throws IOException {
        synchronized (activeLock) {
            Profile profile = get(id);
            Properties active = readActive();
            if (id.equals(active.getProperty(profile.type.name()))) { active.remove(profile.type.name()); writeActive(active); }
            ProfileFiles.deleteTree(instances, profile.directoryPath);
            Files.delete(metadata.resolve(id + ".properties"));
        }
    }
    public synchronized Profile setTemplate(String id, boolean template) throws IOException {
        Profile old = get(id);
        Profile updated = new Profile(old.id, old.type, old.gameVersion, old.loader, old.directoryPath, template);
        save(updated); return updated;
    }
    public synchronized Profile setActive(String id) throws IOException {
        synchronized (activeLock) {
            Profile profile = get(id); Properties active = readActive(); active.setProperty(profile.type.name(), id); writeActive(active); return profile;
        }
    }
    public synchronized Profile getActive(Profile.Type type) throws IOException {
        synchronized (activeLock) {
            String id = readActive().getProperty(type.name());
            if (id == null) return null;
            try { Profile profile = get(id); if (profile.type != type) throw new IOException("Active profile type does not match"); return profile; }
            catch (RuntimeException malformed) { throw new IOException("Invalid active profile id", malformed); }
        }
    }
    private Properties readActive() throws IOException {
        Path file = ProfileFiles.contained(root, root.resolve("active-profiles.properties")); Properties values = new Properties();
        if (Files.exists(file)) { if (Files.size(file) > 8192) throw new IOException("Invalid active profile settings"); try (InputStream input = Files.newInputStream(file)) { values.load(input); } catch (IllegalArgumentException malformed) { throw new IOException("Invalid active profile settings", malformed); } }
        return values;
    }
    private void writeActive(Properties values) throws IOException {
        Path file = ProfileFiles.contained(root, root.resolve("active-profiles.properties")); Path temporary = Files.createTempFile(root, "active-", ".part");
        try { try (OutputStream output = Files.newOutputStream(temporary)) { values.store(output, "Default MODS and PLUGINS update profiles"); } ProfileFiles.move(temporary, file); }
        finally { Files.deleteIfExists(temporary); }
    }
    private void save(Profile profile) throws IOException {
        Properties values = new Properties();
        values.setProperty("id", profile.id); values.setProperty("type", profile.type.name()); values.setProperty("gameVersion", profile.gameVersion);
        values.setProperty("loader", profile.loader); values.setProperty("isTemplate", String.valueOf(profile.isTemplate));
        Path temporary = Files.createTempFile(metadata, profile.id + "-", ".part");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) { values.store(output, "AutoPlug unified profile"); }
            ProfileFiles.move(temporary, metadata.resolve(profile.id + ".properties"));
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void validateId(String id) { if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}")) throw new IllegalArgumentException("Invalid profile id"); }
}
