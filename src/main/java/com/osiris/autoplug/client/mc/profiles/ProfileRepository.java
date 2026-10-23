package com.osiris.autoplug.client.mc.profiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Local profile storage shared by future UI and CLI callers. It does not launch a game,
 * download files, modify the server-wide updater configuration or migrate asset versions.
 * Copies deliberately retain the source version; migration needs a separate updater step.
 */
public class ProfileRepository {
    private final Path profilesDirectory;
    private final java.util.Set<String> inUse = new java.util.HashSet<>();

    /** Held by a launcher/world server from preparation until process exit or cancellation. */
    public final class UseLease implements AutoCloseable {
        private final String id;
        private boolean closed;
        private UseLease(String id) { this.id = id; }
        @Override public void close() {
            synchronized (ProfileRepository.this) {
                if (!closed) { inUse.remove(id); closed = true; }
            }
        }
    }

    public synchronized UseLease acquireUse(String id) throws IOException {
        require(id);
        requireNotInUse(id);
        inUse.add(id);
        return new UseLease(id);
    }

    public synchronized boolean isInUse(String id) { return inUse.contains(id); }

    public synchronized void requireNotInUse(String id) throws IOException {
        if (inUse.contains(id)) throw new IOException("Profile is currently in use: " + id);
    }


    public ProfileRepository(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path root = dataDirectory.toRealPath();
        profilesDirectory = root.resolve("profiles");
        if (Files.exists(profilesDirectory, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(profilesDirectory);
        } else {
            Files.createDirectory(profilesDirectory);
        }
    }

    public synchronized Profile create(String id, ProfileType type, String gameVersion,
                                       String loader, boolean template) throws IOException {
        Profile profile = new Profile(id, type, gameVersion, loader, directoryFor(id), template);
        requireAvailable(profile.getDirectory());
        Path staging = Files.createTempDirectory(profilesDirectory, ".create-");
        try {
            Files.createDirectory(staging.resolve(type.getAssetDirectoryName()));
            Files.createDirectory(staging.resolve("config"));
            writeMetadata(staging, profile);
            // No REPLACE_EXISTING: a competing creator must never replace an existing profile.
            Files.move(staging, profile.getDirectory());
            return profile;
        } finally {
            removeStaging(staging);
        }
    }

    public synchronized Optional<Profile> find(String id) throws IOException {
        Path directory = directoryFor(id);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(readMetadata(directory));
    }

    public synchronized List<Profile> list() throws IOException {
        List<Profile> profiles = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(profilesDirectory)) {
            for (Path entry : entries) {
                if (!entry.getFileName().toString().startsWith(".")) profiles.add(readMetadata(entry));
            }
        }
        profiles.sort(Comparator.comparing(Profile::getId));
        return profiles;
    }

    /** Independent same-version copy. A copy of a template is a normal profile. */
    public synchronized Profile cloneProfile(String sourceId, String targetId) throws IOException {
        requireNotInUse(sourceId);
        Profile source = find(sourceId).orElseThrow(() -> new IOException("Unknown profile: " + sourceId));
        Profile target = new Profile(targetId, source.getType(), source.getGameVersion(), source.getLoader(),
                directoryFor(targetId), false);
        requireAvailable(target.getDirectory());
        Path staging = Files.createTempDirectory(profilesDirectory, ".clone-");
        try {
            copyContents(source.getDirectory(), staging);
            writeMetadata(staging, target);
            Files.move(staging, target.getDirectory());
            return target;
        } finally {
            removeStaging(staging);
        }
    }

    /** Changes the template marker without changing the assets or the game version. */
    public synchronized Profile updateTemplate(String id, boolean template) throws IOException {
        requireNotInUse(id);
        Profile old = require(id);
        Profile updated = new Profile(id, old.getType(), old.getGameVersion(), old.getLoader(),
                old.getDirectory(), template);
        replaceMetadata(updated);
        return updated;
    }

    /** Renames a profile atomically; the contents and configuration stay independent. */
    public synchronized Profile rename(String id, String newId) throws IOException {
        requireNotInUse(id);
        Profile old = require(id);
        if (id.equals(newId)) return old;
        Profile renamed = new Profile(newId, old.getType(), old.getGameVersion(), old.getLoader(),
                directoryFor(newId), old.isTemplate());
        requireAvailable(renamed.getDirectory());
        Files.move(old.getDirectory(), renamed.getDirectory());
        try {
            replaceMetadata(renamed);
        } catch (IOException failure) {
            Files.move(renamed.getDirectory(), old.getDirectory());
            throw failure;
        }
        return renamed;
    }

    /** Deletes only this repository's direct profile tree, never a symlink target. */
    public synchronized void delete(String id) throws IOException {
        requireNotInUse(id);
        Profile profile = require(id);
        Path removed = profilesDirectory.resolve(".delete-" + java.util.UUID.randomUUID());
        Files.move(profile.getDirectory(), removed);
        removeStaging(removed);
    }

    public synchronized Profile require(String id) throws IOException {
        return find(id).orElseThrow(() -> new IOException("Unknown profile: " + id));
    }

    /** Called after a successfully applied, explicitly approved asset migration. */
    public synchronized Profile recordMigratedVersion(String id, String version) throws IOException {
        requireNotInUse(id);
        Profile old = require(id);
        Profile updated = new Profile(id, old.getType(), version, old.getLoader(), old.getDirectory(), old.isTemplate());
        replaceMetadata(updated);
        return updated;
    }

    public Path getProfilesDirectory() { return profilesDirectory; }

    private void replaceMetadata(Profile profile) throws IOException {
        Path temporary = Files.createTempDirectory(profilesDirectory, ".metadata-");
        try {
            writeMetadata(temporary, profile);
            try {
                Files.move(temporary.resolve("profile.properties"), profile.getDirectory().resolve("profile.properties"),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.resolve("profile.properties"), profile.getDirectory().resolve("profile.properties"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            removeStaging(temporary);
        }
    }

    private Path directoryFor(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,79}")) {
            throw new IllegalArgumentException("Invalid profile ID: " + id);
        }
        return profilesDirectory.resolve(id);
    }

    private void requireAvailable(Path directory) throws IOException {
        requireDirectory(profilesDirectory);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(directory.toString());
        }
    }

    private void requireDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a direct profile directory: " + path);
        }
    }

    private Profile readMetadata(Path directory) throws IOException {
        requireDirectory(profilesDirectory);
        requireDirectory(directory);
        Path metadata = directory.resolve("profile.properties");
        if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing profile metadata: " + metadata);
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(metadata)) {
            values.load(input);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid profile metadata: " + metadata, error);
        }
        try {
            if (!"1".equals(values.getProperty("formatVersion"))) {
                throw new IllegalArgumentException("Unsupported profile format");
            }
            if (!directory.getFileName().toString().equals(values.getProperty("id"))) {
                throw new IllegalArgumentException("Profile ID does not match its directory");
            }
            String template = values.getProperty("isTemplate");
            if (!"true".equals(template) && !"false".equals(template)) {
                throw new IllegalArgumentException("Invalid template flag");
            }
            Profile profile = new Profile(values.getProperty("id"), ProfileType.valueOf(values.getProperty("type")),
                    values.getProperty("gameVersion"), values.getProperty("loader"), directory,
                    Boolean.parseBoolean(template));
            requireDirectory(profile.getAssetsDirectory());
            requireDirectory(profile.getConfigDirectory());
            return profile;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("Invalid profile metadata: " + metadata, error);
        }
    }

    private void writeMetadata(Path directory, Profile profile) throws IOException {
        Properties values = new Properties();
        values.setProperty("formatVersion", "1");
        values.setProperty("id", profile.getId());
        values.setProperty("type", profile.getType().name());
        values.setProperty("gameVersion", profile.getGameVersion());
        values.setProperty("loader", profile.getLoader());
        values.setProperty("isTemplate", Boolean.toString(profile.isTemplate()));
        try (OutputStream output = Files.newOutputStream(directory.resolve("profile.properties"))) {
            values.store(output, "AutoPlug native profile");
        }
    }

    private void copyContents(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (!directory.equals(source)) Files.createDirectory(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) throw new IOException("Cannot clone a linked or special file: " + file);
                Path relative = source.relativize(file);
                if (!relative.toString().equals("profile.properties")) Files.copy(file, target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void removeStaging(Path staging) throws IOException {
        if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(staging, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
