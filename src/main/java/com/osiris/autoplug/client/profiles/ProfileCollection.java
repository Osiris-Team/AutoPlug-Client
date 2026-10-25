package com.osiris.autoplug.client.profiles;

import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ProfileCollection {
    public List<CollectionEntry> entries = new ArrayList<>();
    public ProfileCollection scan(Profile profile) throws IOException {
        Path dir = profile.getDirectory().resolve(profile.type.collectionDirectory());
        Files.createDirectories(dir);
        Path metadata = profile.getDirectory().resolve("collection.json");
        ProfileCollection result = Files.exists(metadata) ? new JsonFiles().read(metadata, ProfileCollection.class) : new ProfileCollection();
        Map<String, CollectionEntry> found = new LinkedHashMap<>();
        if (profile.type == ProfileType.PLUGINS) {
            for (MinecraftPlugin p : new UtilsMinecraft().getPlugins(dir.toFile())) {
                CollectionEntry entry = new CollectionEntry(); entry.file = Paths.get(p.installationPath).getFileName().toString();
                entry.name = p.getName(); entry.version = p.getVersion(); entry.author = p.getAuthor();
                entry.modrinthId = p.modrinthId; entry.spigotId = p.spigotId; entry.bukkitId = p.bukkitId; found.put(entry.file, entry);
            }
        } else for (MinecraftMod m : new UtilsMinecraft().getMods(dir.toFile())) {
            CollectionEntry entry = new CollectionEntry(); entry.file = Paths.get(m.installationPath).getFileName().toString();
            entry.name = m.getName(); entry.version = m.getVersion(); entry.author = m.getAuthor();
            entry.modrinthId = m.modrinthId; entry.curseforgeId = m.curseforgeId; found.put(entry.file, entry);
        }
        // Include unreadable/unknown jars so migrations never silently leave them active.
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jar : jars) if (!found.containsKey(jar.getFileName().toString())) {
                CollectionEntry entry = new CollectionEntry(); entry.file = jar.getFileName().toString(); entry.name = entry.file; found.put(entry.file, entry);
            }
        }
        for (CollectionEntry configured : result.entries) {
            resolve(profile, configured.file); // Reject persisted paths outside the selected collection.
            if (found.containsKey(configured.file)) found.put(configured.file, configured);
        }
        result.entries = new ArrayList<>(found.values()); return result;
    }
    public Path resolve(Profile profile, String file) throws IOException {
        if (file == null || file.contains("/") || file.contains("\\") || !file.endsWith(".jar") || file.equals(".jar")) throw new IOException("Invalid collection artifact filename");
        Path directory = profile.getDirectory().resolve(profile.type.collectionDirectory());
        if (Files.isSymbolicLink(directory)) throw new IOException("Collection directory must not be a link");
        return directory.resolve(file);
    }
    public void save(Profile profile) throws IOException { new JsonFiles().write(profile.getDirectory().resolve("collection.json"), this); }
}
