package com.osiris.autoplug.client.mc.worlds;

import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileRepository;
import com.osiris.autoplug.client.mc.profiles.ProfileType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/** Metadata and isolated per-world profile snapshots. Never reads or changes vanilla saves. */
public final class WorldRepository {
    private final Path directory;
    private final ProfileRepository profiles;

    public WorldRepository(Path dataRoot, ProfileRepository profiles) throws IOException {
        Files.createDirectories(dataRoot);
        this.directory = dataRoot.toRealPath().resolve("worlds");
        this.profiles = profiles;
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory);
        requireDirectory(directory);
    }

    public synchronized World create(String id, String profileId) throws IOException {
        Path target = path(id);
        Profile profile = requireServerProfile(profileId);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(id);
        Path staging = Files.createTempDirectory(directory, ".create-");
        try {
            Properties metadata = new Properties();
            metadata.setProperty("formatVersion", "1");
            metadata.setProperty("id", id);
            metadata.setProperty("profileId", profile.getId());
            store(staging.resolve("world.properties"), metadata);
            Properties eula = new Properties();
            eula.setProperty("eula", "false");
            store(staging.resolve("eula.txt"), eula);
            Files.move(staging, target);
            return new World(id, profileId, target);
        } finally {
            deleteTree(staging);
        }
    }

    public synchronized Optional<World> find(String id) throws IOException {
        Path target = path(id);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        requireDirectory(target);
        Properties values = load(target.resolve("world.properties"));
        if (!"1".equals(values.getProperty("formatVersion")) || !id.equals(values.getProperty("id"))) {
            throw new IOException("Invalid world metadata: " + id);
        }
        String profileId = values.getProperty("profileId");
        requireServerProfile(profileId);
        return Optional.of(new World(id, profileId, target));
    }

    public synchronized List<World> list() throws IOException {
        requireDirectory(directory);
        List<World> worlds = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String id = entry.getFileName().toString();
                if (!id.startsWith(".")) worlds.add(find(id).orElseThrow(() -> new IOException("World disappeared")));
            }
        }
        worlds.sort(Comparator.comparing(World::getId));
        return worlds;
    }

    /**
     * Refreshes this world's managed mods/plugins snapshot; world data is untouched.
     * Existing per-world config files are preserved. The owner must accept Minecraft's
     * EULA themselves before this operation; AutoPlug never writes eula=true.
     */
    public synchronized void prepare(World world) throws IOException {
        requireOwned(world);
        requireEulaAccepted(world);
        Profile profile = requireServerProfile(world.getProfileId());
        String assets = profile.getAssetsDirectory().getFileName().toString();
        Path staging = Files.createTempDirectory(world.getDirectory(), ".profile-");
        Path target = world.getDirectory().resolve(assets), config = world.getDirectory().resolve("config");
        Path preparedAssets = staging.resolve("assets"), preparedConfig = staging.resolve("config");
        Path oldAssets = staging.resolve("old-assets"), oldConfig = staging.resolve("old-config");
        boolean assetsBackedUp = false, configBackedUp = false, assetsInstalled = false, configInstalled = false;
        boolean preserveRecovery = false;
        try {
            // Validate and stage both trees before changing either live directory.
            copyTree(profile.getAssetsDirectory(), preparedAssets, false);
            if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)) copyTree(config, preparedConfig, false);
            copyTree(profile.getConfigDirectory(), preparedConfig, true);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                requireDirectory(target);
                Files.move(target, oldAssets); assetsBackedUp = true;
            }
            try {
                if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(config, oldConfig); configBackedUp = true;
                }
                Files.move(preparedAssets, target); assetsInstalled = true;
                Files.move(preparedConfig, config); configInstalled = true;
            } catch (IOException error) {
                try {
                    if (configInstalled) deleteTree(config);
                    if (assetsInstalled) deleteTree(target);
                    if (configBackedUp) Files.move(oldConfig, config);
                    if (assetsBackedUp) Files.move(oldAssets, target);
                } catch (IOException rollback) {
                    preserveRecovery = true;
                    error.addSuppressed(new IOException("World preparation rollback failed; recovery data preserved at " + staging, rollback));
                }
                throw error;
            }
        } finally {
            if (!preserveRecovery) deleteTree(staging);
        }
    }

    public static void requireEulaAccepted(World world) throws IOException {
        Properties values;
        try {
            values = load(world.getDirectory().resolve("eula.txt"));
        } catch (IOException error) {
            throw new IOException("Read the Minecraft EULA and explicitly accept it in this world's eula.txt before playing. AutoPlug does not accept it for you.", error);
        }
        if (!"true".equals(values.getProperty("eula"))) {
            throw new IOException("Read the Minecraft EULA and explicitly accept it in this world's eula.txt before playing. AutoPlug does not accept it for you.");
        }
    }

    public void requireOwned(World world) throws IOException {
        Path expected = path(world.getId());
        if (!expected.equals(world.getDirectory())) throw new IOException("World is outside this repository");
        requireDirectory(expected);
        Properties metadata = load(expected.resolve("world.properties"));
        if (!"1".equals(metadata.getProperty("formatVersion")) || !world.getId().equals(metadata.getProperty("id"))
                || !world.getProfileId().equals(metadata.getProperty("profileId"))) {
            throw new IOException("World metadata changed");
        }
    }

    private Profile requireServerProfile(String id) throws IOException {
        Profile profile = profiles.find(id).orElseThrow(() -> new IOException("Unknown world profile: " + id));
        if (profile.getType() == ProfileType.MODS) throw new IOException("Worlds require a PLUGINS or MODS_SERVER profile");
        return profile;
    }

    private Path path(String id) throws IOException {
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,79}")) throw new IOException("Invalid world ID");
        requireDirectory(directory);
        return directory.resolve(id);
    }

    private static void requireDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Expected a direct world/profile directory: " + path);
    }

    static Properties load(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Expected a regular configuration file: " + path);
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) { values.load(input); }
        catch (IllegalArgumentException error) { throw new IOException("Malformed configuration", error); }
        return values;
    }

    static void store(Path path, Properties values) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Refusing linked configuration file");
        try (OutputStream output = Files.newOutputStream(path)) { values.store(output, "AutoPlug isolated world"); }
    }

    private static void copyTree(Path source, Path target, boolean preserveExisting) throws IOException {
        requireDirectory(source);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(target);
        requireDirectory(target);
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path destination = target.resolve(source.relativize(dir));
                if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(destination);
                requireDirectory(destination);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) throw new IOException("Refusing linked or special profile content");
                Path destination = target.resolve(source.relativize(file));
                if (preserveExisting && Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Refusing linked world config");
                } else Files.copy(file, destination);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }
}
