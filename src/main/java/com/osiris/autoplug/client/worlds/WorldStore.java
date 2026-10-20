/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** World metadata lives under worlds/definitions; saves and independent configs under worlds/instances. */
public final class WorldStore {
    private final Path root, definitions, instances, locks;
    private final ProfileStore profiles;
    public WorldStore(Path root, ProfileStore profiles) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.profiles = Objects.requireNonNull(profiles);
        definitions = this.root.resolve("worlds/definitions"); instances = this.root.resolve("worlds/instances"); locks = this.root.resolve("worlds/locks");
        rejectLinks(definitions); rejectLinks(instances); rejectLinks(locks); Files.createDirectories(definitions); Files.createDirectories(instances); Files.createDirectories(locks);
    }
    public static WorldStore defaultStore() throws IOException { ProfileStore profiles = ProfileStore.defaultStore(); return new WorldStore(profiles.getRoot(), profiles); }
    public Path getRoot() { return root; }
    public ProfileStore getProfiles() { return profiles; }
    Path lockPath(String id) throws IOException { WorldDefinition.validateId(id); return contained(locks, locks.resolve(id + ".lock")); }
    public synchronized WorldDefinition create(String name, String profileId) throws IOException {
        if (name == null || name.trim().isEmpty()) throw new IOException("Enter a world name");
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "world"; if (base.length() > 65) base = base.substring(0, 65);
        String id = base; int number = 2;
        while (Files.exists(definitions.resolve(id + ".properties")) || Files.exists(instances.resolve(id))) id = base + "-" + number++;
        return create(id, name, profileId);
    }
    public synchronized WorldDefinition create(String id, String name, String profileId) throws IOException {
        Profile profile = profiles.get(profileId); requireServer(profile);
        Path directory = instances.resolve(id);
        WorldDefinition world = new WorldDefinition(id, name, profile.id, null, profile.gameVersion, profile.loader, profile.type, directory, null, false);
        contained(instances, directory);
        if (Files.exists(directory) || Files.exists(definitions.resolve(id + ".properties"))) throw new IOException("World already exists: " + id);
        Files.createDirectory(directory);
        try {
            copyProfile(profile, directory);
            save(world); return world;
        } catch (IOException | RuntimeException failure) { try { deleteTree(instances, directory); } catch (IOException cleanup) { failure.addSuppressed(cleanup); } throw failure; }
    }
    public synchronized WorldDefinition get(String id) throws IOException {
        WorldDefinition.validateId(id); Path metadata = contained(definitions, definitions.resolve(id + ".properties"));
        Properties p = new Properties(); if (Files.size(metadata) > 16384) throw new IOException("World metadata exceeds size limit");
        try (InputStream input = Files.newInputStream(metadata)) { p.load(input); }
        catch (IllegalArgumentException malformed) { throw new IOException("Malformed world metadata", malformed); }
        try {
            if (!id.equals(p.getProperty("id")) || !p.getProperty("acceptedEula", "").matches("true|false")) throw new IllegalArgumentException("Inconsistent world metadata");
            Path directory = contained(instances, instances.resolve(id));
            WorldDefinition world = new WorldDefinition(id, p.getProperty("name"), p.getProperty("profileId"), p.getProperty("clientProfileId"),
                    p.getProperty("gameVersion"), p.getProperty("loader"), Profile.Type.valueOf(p.getProperty("profileType")), directory,
                    p.containsKey("serverExecutable") ? Paths.get(p.getProperty("serverExecutable")) : null, Boolean.parseBoolean(p.getProperty("acceptedEula")));
            return world;
        } catch (RuntimeException malformed) { throw new IOException("Invalid world metadata: " + id, malformed); }
    }
    public synchronized List<WorldDefinition> list() throws IOException {
        List<WorldDefinition> result = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(definitions, "*.properties")) {
            for (Path file : files) { String name = file.getFileName().toString(); result.add(get(name.substring(0, name.length() - 11))); }
        }
        result.sort(Comparator.comparing(world -> world.id)); return Collections.unmodifiableList(result);
    }
    public synchronized WorldDefinition setClientProfile(String id, String clientId) throws IOException {
        WorldDefinition old = get(id);
        if (clientId != null) {
            Profile client = profiles.get(clientId);
            if (client.type != Profile.Type.MODS || !old.gameVersion.equals(client.gameVersion)) throw new IOException("Choose a client MODS profile for Minecraft " + old.gameVersion);
            if (old.profileType == Profile.Type.MODS_SERVER && !old.loader.equals(client.loader)) throw new IOException("The client and server mod loaders must match");
        }
        return save(new WorldDefinition(old.id, old.name, old.profileId, clientId, old.gameVersion, old.loader, old.profileType, old.directoryPath, old.serverExecutable, old.acceptedEula));
    }
    public synchronized WorldDefinition setServerExecutable(String id, Path executable) throws IOException {
        WorldDefinition old = get(id); if (executable != null) WorldServerPreparer.validateSuppliedJar(executable, old.gameVersion);
        return save(new WorldDefinition(old.id, old.name, old.profileId, old.clientProfileId, old.gameVersion, old.loader, old.profileType, old.directoryPath, executable, old.acceptedEula));
    }
    /** Only call after an explicit user action accepting https://aka.ms/MinecraftEULA for this world. */
    public synchronized WorldDefinition acceptEula(String id, boolean accepted) throws IOException {
        WorldDefinition old = get(id);
        return save(new WorldDefinition(old.id, old.name, old.profileId, old.clientProfileId, old.gameVersion, old.loader, old.profileType, old.directoryPath, old.serverExecutable, accepted));
    }
    /** Service consumers must stop the world before deleting it; an OS-held process lock also prevents deletion. */
    public synchronized void delete(String id) throws IOException {
        WorldDefinition world = get(id);
        // This stable lock is outside save data so Windows deletion can retain ownership until it finishes.
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(lockPath(id), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            java.nio.channels.FileLock lock;
            try { lock = channel.tryLock(); } catch (java.nio.channels.OverlappingFileLockException running) { throw new IOException("Stop the world before deleting it", running); }
            if (lock == null) throw new IOException("Stop the world before deleting it");
            try { deleteTree(instances, world.directoryPath); Files.delete(contained(definitions, definitions.resolve(id + ".properties"))); }
            finally { lock.release(); }
        }
    }
    private WorldDefinition save(WorldDefinition world) throws IOException {
        Properties p = new Properties(); p.setProperty("id", world.id); p.setProperty("name", world.name); p.setProperty("profileId", world.profileId);
        p.setProperty("gameVersion", world.gameVersion); p.setProperty("loader", world.loader); p.setProperty("profileType", world.profileType.name());
        p.setProperty("acceptedEula", Boolean.toString(world.acceptedEula));
        if (world.clientProfileId != null) p.setProperty("clientProfileId", world.clientProfileId);
        if (world.serverExecutable != null) p.setProperty("serverExecutable", world.serverExecutable.toString());
        Path temporary = Files.createTempFile(definitions, world.id + "-", ".part");
        try { try (OutputStream output = Files.newOutputStream(temporary)) { p.store(output, "AutoPlug virtual world"); } move(temporary, contained(definitions, definitions.resolve(world.id + ".properties"))); }
        finally { Files.deleteIfExists(temporary); }
        return world;
    }
    private void copyProfile(Profile profile, Path directory) throws IOException {
        rejectLinks(profile.directoryPath);
        for (String name : Arrays.asList(profile.type == Profile.Type.PLUGINS ? "plugins" : "mods", "config", "defaultconfigs")) {
            Path source = profile.directoryPath.resolve(name); if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path from, BasicFileAttributes attributes) throws IOException { rejectLinks(from); Files.createDirectories(directory.resolve(profile.directoryPath.relativize(from))); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult visitFile(Path from, BasicFileAttributes attributes) throws IOException {
                    if (!attributes.isRegularFile() || Files.isSymbolicLink(from)) throw new IOException("Profile contains linked/special files");
                    Path to = directory.resolve(profile.directoryPath.relativize(from));
                    if (from.getFileName().toString().endsWith(".jar") || from.getFileName().toString().endsWith(".jar.disabled")) profiles.getCache().link(profiles.getCache().store(from), to);
                    else Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES); return FileVisitResult.CONTINUE;
                }
            });
        }
    }
    static void requireServer(Profile profile) throws IOException {
        if (profile.type == Profile.Type.MODS) throw new IOException("Virtual worlds require a PLUGINS or MODS_SERVER profile");
        if (profile.type == Profile.Type.PLUGINS && profile.loader.matches("FABRIC|QUILT|FORGE|NEOFORGE"))
            throw new IOException(profile.loader + " loads server mods, not plugin profiles; choose MODS_SERVER or explicitly name your custom hybrid server loader");
        if (profile.type == Profile.Type.MODS_SERVER && profile.loader.matches("PAPER|PURPUR|SPIGOT|BUKKIT"))
            throw new IOException(profile.loader + " loads plugins, not server mod profiles; choose PLUGINS or explicitly name your custom hybrid server loader");
    }
    static Path contained(Path root, Path candidate) throws IOException {
        Path base = root.toAbsolutePath().normalize(), target = candidate.toAbsolutePath().normalize();
        if (!target.startsWith(base) || target.equals(base)) throw new IOException("World path escapes its managed directory"); rejectLinks(target); return target;
    }
    static void rejectLinks(Path path) throws IOException { for (Path cursor = path.toAbsolutePath().normalize(); cursor != null; cursor = cursor.getParent()) if (Files.isSymbolicLink(cursor)) throw new IOException("World paths cannot contain symbolic links"); }
    static void move(Path from, Path to) throws IOException { try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException unavailable) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); } }
    static void deleteTree(Path root, Path path) throws IOException {
        contained(root, path);
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException { if (failure != null) throw failure; Files.delete(directory); return FileVisitResult.CONTINUE; }
        });
    }
}
