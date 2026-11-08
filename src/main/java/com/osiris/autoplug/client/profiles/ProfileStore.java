package com.osiris.autoplug.client.profiles;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Independent profile metadata/configuration, with shared immutable artifact storage. */
public class ProfileStore {
    private final Path root;
    private final JsonFiles json = new JsonFiles();
    private final ArtifactCache cache;
    public ProfileStore(Path root) throws IOException {
        this(root, new ArtifactCache(root.toAbsolutePath().normalize().getParent().resolve("cache")));
    }
    public ProfileStore(Path root, ArtifactCache cache) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.cache = cache; Files.createDirectories(this.root);
    }
    public Path getRoot() { return root; }
    public ArtifactCache getCache() { return cache; }
    public List<Profile> list() throws IOException {
        List<Profile> result = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
            for (Path dir : dirs) if (Files.isRegularFile(dir.resolve("profile.json")) && !Files.isSymbolicLink(dir))
                result.add(get(dir.getFileName().toString()));
        }
        result.sort(Comparator.comparing(p -> p.name.toLowerCase(Locale.ROOT)));
        return result;
    }
    public Profile get(String id) throws IOException {
        Path dir = directory(id);
        Profile profile = json.read(dir.resolve("profile.json"), Profile.class);
        if (!id.equals(profile.id)) throw new IOException("Profile identity does not match its directory");
        validate(profile); profile.setDirectory(dir); return profile;
    }
    public Profile create(String name, String version, String loader, ProfileType type) throws IOException {
        Profile p = new Profile(); p.id = UUID.randomUUID().toString(); p.name = name;
        p.gameVersion = version; p.loader = loader.toUpperCase(Locale.ROOT); p.type = type;
        validate(p); p.setDirectory(directory(p.id));
        Files.createDirectory(p.getDirectory());
        Files.createDirectories(p.getDirectory().resolve(type.collectionDirectory()));
        save(p); return p;
    }
    /** Defaults are version-specific; never retarget or overwrite an existing user profile. */
    public Profile ensureDefault(String version, ProfileType type) throws IOException {
        if (type != ProfileType.MODS && type != ProfileType.MODS_SERVER) throw new IOException("Invalid default profile type");
        String name = type.isClient() ? "Default (VANILLA)" : "Default";
        try (ProfileLease ignored = new ProfileLease(root)) {
            for (Profile profile : list()) if (name.equals(profile.name) && version.equals(profile.gameVersion)
                    && "VANILLA".equals(profile.loader) && profile.type == type && !profile.template && !profile.migrationPending)
                return profile;
            return create(name, version, "VANILLA", type);
        }
    }
    public void save(Profile p) throws IOException {
        validate(p); p.setDirectory(directory(p.id)); json.write(p.getDirectory().resolve("profile.json"), p);
    }
    /** Creates metadata offline. The complete compatible utility pack is prepared on first launch. */
    public Profile ensureFabricDefault(String version) throws IOException {
        try (ProfileLease ignored = new ProfileLease(root)) {
            for (Profile profile : list()) if (FabricDefaultProfile.PRESET.equals(profile.builtinPreset)
                    && version.equals(profile.gameVersion) && "FABRIC".equals(profile.loader)
                    && profile.type == ProfileType.MODS && !profile.template && !profile.migrationPending) return profile;
            Profile profile = create("Default (FABRIC)", version, "FABRIC", ProfileType.MODS);
            profile.builtinPreset = FabricDefaultProfile.PRESET;
            profile.migrationSummary = "Client utility mods install on first launch: Fabric API, Sodium, Entity Culling, ImmediatelyFast and Mod Menu, with required libraries. Exact-version availability is checked before launch.";
            save(profile); return profile;
        }
    }
    public Profile cloneProfile(String sourceId, String targetVersion) throws IOException {
        Profile source = get(sourceId);
        return cloneProfile(sourceId, source.name + " " + targetVersion, targetVersion, source.loader);
    }
    public Profile cloneProfile(String sourceId, String name, String version, String loader) throws IOException {
        Profile source = get(sourceId);
        try (ProfileLease ignored = new ProfileLease(source.getDirectory())) {
            Profile target = create(name, version, loader, source.type);
            target.loaderVersion = Objects.equals(source.loader, target.loader) && Objects.equals(source.gameVersion, version) ? source.loaderVersion : null;
            if ("FABRIC".equals(target.loader)) { target.builtinPreset = source.builtinPreset; target.builtinPresetInstalled = source.builtinPresetInstalled; }
            target.migrationPending = !Objects.equals(source.gameVersion, version) || !Objects.equals(source.loader, target.loader);
            Set<String> skip = new HashSet<>(Arrays.asList("profile.json", ".profile.lock", ".updates", ".autoplug", "logs", "crash-reports", "saves", "world", "versions", "libraries", "assets", "natives", "launcher_profiles.json", "accounts.json", "usercache.json"));
            Files.walkFileTree(source.getDirectory(), new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.getDirectory().relativize(dir);
                    if (!relative.toString().isEmpty() && skip.contains(relative.getName(0).toString())) return FileVisitResult.SKIP_SUBTREE;
                    Files.createDirectories(target.getDirectory().resolve(relative)); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.getDirectory().relativize(file);
                    if (skip.contains(relative.getName(0).toString())) return FileVisitResult.CONTINUE;
                    Path dest = target.getDirectory().resolve(relative);
                    if (file.getFileName().toString().endsWith(".jar")) cache.link(cache.store(file), dest);
                    else {
                        if (Files.isSymbolicLink(file)) throw new IOException("Refusing to clone linked configuration: " + relative);
                        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            save(target); return target;
        }
    }
    /** Move to a recoverable trash directory instead of recursively deleting user configuration. */
    public void delete(String id) throws IOException {
        Profile p = get(id);
        try (ProfileLease ignored = new ProfileLease(p.getDirectory())) {
            Path trash = root.getParent().resolve("trash"); Files.createDirectories(trash);
            Files.move(p.getDirectory(), trash.resolve(id + "-" + System.currentTimeMillis()));
        }
    }
    private Path directory(String id) throws IOException {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,100}")) throw new IOException("Invalid profile id");
        Path path = root.resolve(id).normalize();
        if (!path.getParent().equals(root) || Files.isSymbolicLink(path)) throw new IOException("Invalid profile directory");
        return path;
    }
    private void validate(Profile p) throws IOException {
        if (p.name == null || p.name.trim().isEmpty() || p.name.length() > 120 || p.type == null)
            throw new IOException("Profile requires a name and type");
        if (p.gameVersion == null || !p.gameVersion.matches("[A-Za-z0-9_.-]{1,80}")) throw new IOException("Invalid game version");
        if (p.loader == null || !Arrays.asList("VANILLA", "FABRIC", "QUILT", "FORGE", "NEOFORGE", "PAPER", "SPIGOT", "PURPUR", "BUKKIT", "VELOCITY", "BUNGEE").contains(p.loader))
            throw new IOException("Unsupported loader: " + p.loader);
        if (p.type == ProfileType.MODS && Arrays.asList("PAPER", "SPIGOT", "PURPUR", "BUKKIT", "VELOCITY", "BUNGEE").contains(p.loader))
            throw new IOException("Server plugin loaders require a plugins profile");
    }
}
