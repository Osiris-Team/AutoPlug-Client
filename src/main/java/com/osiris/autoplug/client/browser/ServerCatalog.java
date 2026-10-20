package com.osiris.autoplug.client.browser;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class ServerCatalog {
    private final Path file;
    public ServerCatalog(Path autoplugRoot) { file = autoplugRoot.resolve("servers.json").toAbsolutePath().normalize(); }
    public synchronized List<SavedServer> list() throws IOException {
        ClientDataFiles.rejectLinks(file);
        if (!Files.exists(file)) return Collections.emptyList();
        if (Files.size(file) > 1024 * 1024) throw new IOException("Saved server list is too large");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray values = JsonParser.parseReader(reader).getAsJsonArray();
            if (values.size() > 1000) throw new IOException("Too many saved servers");
            List<SavedServer> result = new ArrayList<>();
            for (JsonElement value : values) {
                JsonObject entry = value.getAsJsonObject();
                result.add(new SavedServer(entry.get("name").getAsString(), ServerAddress.parse(entry.get("address").getAsString()),
                        entry.has("loader") ? entry.get("loader").getAsString() : "VANILLA"));
            }
            return Collections.unmodifiableList(result);
        } catch (RuntimeException exception) { throw new IOException("Invalid saved server list", exception); }
    }
    public synchronized void save(List<SavedServer> servers) throws IOException {
        if (servers.size() > 1000) throw new IOException("Too many saved servers");
        JsonArray values = new JsonArray(); Set<ServerAddress> seen = new HashSet<>();
        for (SavedServer server : servers) {
            if (!seen.add(server.address)) throw new IOException("Duplicate server address");
            JsonObject entry = new JsonObject(); entry.addProperty("name", server.name); entry.addProperty("address", server.address.toString()); entry.addProperty("loader", server.loaderHint); values.add(entry);
        }
        ClientDataFiles.atomicWrite(file, new GsonBuilder().setPrettyPrinting().create().toJson(values).getBytes(StandardCharsets.UTF_8));
    }
    /** Address identifies a bookmark; stale panels cannot replace newer edits or drop imported entries. */
    public synchronized List<SavedServer> edit(SavedServer previous, SavedServer replacement) throws IOException {
        List<SavedServer> current = new ArrayList<>(list());
        int position = previous == null ? -1 : find(current, previous.address);
        if (previous != null && (position < 0 || !same(current.get(position), previous))) throw new IOException("Server changed since selection; refresh the list before editing");
        int duplicate = find(current, replacement.address);
        if (duplicate >= 0 && duplicate != position) throw new IOException("This address is already saved");
        if (position >= 0) current.set(position, replacement); else current.add(replacement);
        save(current); return list();
    }
    public synchronized List<SavedServer> remove(SavedServer previous) throws IOException {
        List<SavedServer> current = new ArrayList<>(list()); int position = find(current, previous.address);
        if (position < 0 || !same(current.get(position), previous)) throw new IOException("Server changed since selection; refresh the list before removing");
        current.remove(position); save(current); return list();
    }
    private static int find(List<SavedServer> servers, ServerAddress address) { for (int i = 0; i < servers.size(); i++) if (servers.get(i).address.equals(address)) return i; return -1; }
    private static boolean same(SavedServer first, SavedServer second) { return first.address.equals(second.address) && first.name.equals(second.name) && first.loaderHint.equals(second.loaderHint); }
    public synchronized int importVanilla(Path path) throws IOException {
        Map<ServerAddress, SavedServer> result = new LinkedHashMap<>();
        for (SavedServer server : list()) result.put(server.address, server);
        int initial = result.size();
        for (SavedServer server : MinecraftServersDat.read(path).servers()) result.putIfAbsent(server.address, server);
        save(new ArrayList<>(result.values())); return result.size() - initial;
    }
    /** User-requested export replaces the vanilla list, preserves retained icons/tags and creates a unique backup. */
    public synchronized Path exportVanilla(Path path) throws IOException {
        path = path.toAbsolutePath().normalize(); ClientDataFiles.rejectLinks(path);
        boolean exists = Files.exists(path);
        if (exists && (!Files.isRegularFile(path) || Files.size(path) > 32 * 1024 * 1024)) throw new IOException("Invalid or oversized servers.dat");
        byte[] before = exists ? digest(path) : null;
        MinecraftServersDat.Document document = exists ? MinecraftServersDat.read(path) : MinecraftServersDat.empty();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MinecraftServersDat.write(document.withServers(list()), output);
        if (exists != Files.exists(path) || (exists && !MessageDigest.isEqual(before, digest(path)))) throw new IOException("servers.dat changed; export cancelled");
        Path backup = null;
        if (exists) {
            backup = path.resolveSibling(path.getFileName() + ".autoplug-backup-" + UUID.randomUUID());
            Files.copy(path, backup);
        }
        ClientDataFiles.atomicWrite(path, output.toByteArray()); return backup;
    }
    private static byte[] digest(Path path) throws IOException {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) { byte[] buffer = new byte[8192]; int size; while ((size = input.read(buffer)) != -1) hash.update(buffer, 0, size); }
            return hash.digest();
        } catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }
    public static Path vanillaPath() {
        String appData = System.getenv("APPDATA");
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") && appData != null) return Paths.get(appData, ".minecraft", "servers.dat");
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft", "servers.dat");
        return Paths.get(System.getProperty("user.home"), ".minecraft", "servers.dat");
    }
}
