package com.osiris.autoplug.client.worlds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Persistent metadata; never places a world save inside a reusable profile. */
public final class WorldStore {
    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public WorldStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public synchronized VirtualWorld create(String name, String serverProfileId, String clientProfileId) throws IOException {
        return create(name, serverProfileId, clientProfileId, false);
    }

    public synchronized VirtualWorld create(String name, String serverProfileId, String clientProfileId,
                                             boolean eulaAccepted) throws IOException {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("World name is required");
        if (serverProfileId == null || clientProfileId == null) throw new IllegalArgumentException("Both profiles are required");
        VirtualWorld world = new VirtualWorld();
        world.id = UUID.randomUUID().toString();
        world.name = name.trim();
        world.serverProfileId = serverProfileId;
        world.clientProfileId = clientProfileId;
        world.createdAt = System.currentTimeMillis();
        world.eulaAccepted = eulaAccepted;
        Files.createDirectories(getDirectory(world.id));
        save(world);
        return world;
    }

    public synchronized List<VirtualWorld> list() throws IOException {
        List<VirtualWorld> worlds = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).collect(Collectors.toList())) {
                if (Files.isRegularFile(path.resolve("world.json"), LinkOption.NOFOLLOW_LINKS))
                    worlds.add(get(path.getFileName().toString()));
            }
        }
        worlds.sort(Comparator.comparingLong(w -> w.createdAt));
        return worlds;
    }

    public synchronized VirtualWorld get(String id) throws IOException {
        Path file = metadataDirectory(id).resolve("world.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            VirtualWorld world = gson.fromJson(reader, VirtualWorld.class);
            if (world == null || !id.equals(world.id) || world.serverProfileId == null || world.clientProfileId == null)
                throw new IOException("Invalid world metadata: " + file);
            Path icon = getDirectory(id).resolve("world/icon.png");
            if (!Files.isRegularFile(icon, LinkOption.NOFOLLOW_LINKS)) icon = getDirectory(id).resolve("server-icon.png");
            world.thumbnail = Files.isRegularFile(icon, LinkOption.NOFOLLOW_LINKS) ? icon.toString() : null;
            return world;
        } catch (com.google.gson.JsonParseException e) {
            throw new IOException("Invalid world metadata: " + file, e);
        }
    }

    public synchronized void setEulaAccepted(String id, boolean accepted) throws IOException {
        VirtualWorld world = get(id);
        world.eulaAccepted = accepted;
        save(world);
    }

    public synchronized void save(VirtualWorld world) throws IOException {
        Path directory = metadataDirectory(world.id);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "world-", ".tmp");
        try {
            Files.write(temporary, gson.toJson(world).getBytes(StandardCharsets.UTF_8));
            try { Files.move(temporary, directory.resolve("world.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, directory.resolve("world.json"), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    /** Call only after closing any session using this world. */
    public synchronized void delete(String id) throws IOException {
        Path directory = metadataDirectory(id);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        Path trash = root.resolve(".trash");
        Files.createDirectories(trash);
        Files.move(directory, trash.resolve(id + "-" + System.currentTimeMillis()));
    }

    public Path getDirectory(String id) throws IOException {
        Path directory = metadataDirectory(id).resolve("server");
        if (Files.isSymbolicLink(directory)) throw new IOException("World directory must not be a symbolic link");
        return directory;
    }

    private Path metadataDirectory(String id) throws IOException {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid world id");
        Path directory = root.resolve(id).normalize();
        if (!directory.startsWith(root) || Files.isSymbolicLink(directory)) throw new IOException("Unsafe world directory");
        return directory;
    }
}
