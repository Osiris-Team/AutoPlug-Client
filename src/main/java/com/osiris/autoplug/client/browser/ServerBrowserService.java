package com.osiris.autoplug.client.browser;

import com.google.gson.*;
import com.osiris.autoplug.client.utils.MineStat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local favorites plus an adapter to AutoPlug's existing status ping. */
public final class ServerBrowserService {
    private final Path storage, vanilla;
    public ServerBrowserService(Path storage, Path vanilla) { this.storage = storage; this.vanilla = vanilla; }
    public static ServerBrowserService defaults() {
        Path home = Paths.get(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path minecraft;
        if (os.contains("win") && System.getenv("APPDATA") != null) minecraft = Paths.get(System.getenv("APPDATA"), ".minecraft");
        else if (os.contains("mac")) minecraft = home.resolve("Library/Application Support/minecraft");
        else minecraft = home.resolve(".minecraft");
        return new ServerBrowserService(home.resolve(".autoplug/servers.json"), minecraft.resolve("servers.dat"));
    }
    public Path vanillaFile() { return vanilla; }
    public synchronized List<SavedServer> list() throws IOException {
        List<SavedServer> result = new ArrayList<>();
        if (!Files.exists(storage)) return result;
        if (Files.size(storage) > 4 * 1024 * 1024) throw new IOException("The server favorites file is too large.");
        try (Reader reader = Files.newBufferedReader(storage, StandardCharsets.UTF_8)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement value : array) {
                JsonObject entry = value.getAsJsonObject();
                result.add(new SavedServer(entry.get("name").getAsString(), entry.get("address").getAsString(),
                        entry.has("lastJoinedAt") && !entry.get("lastJoinedAt").isJsonNull() ? entry.get("lastJoinedAt").getAsLong() : 0));
            }
        } catch (RuntimeException e) { throw new IOException("Could not read server favorites. The original file was preserved.", e); }
        return result;
    }
    public synchronized void add(String name, String address) throws IOException {
        SavedServer added = new SavedServer(name, address);
        List<SavedServer> servers = list();
        long lastJoined = 0;
        for (SavedServer server : servers) if (server.address.equalsIgnoreCase(added.address)) lastJoined = Math.max(lastJoined, server.lastJoinedAt);
        servers.removeIf(server -> server.address.equalsIgnoreCase(added.address));
        servers.add(new SavedServer(added.name, added.address, lastJoined)); save(servers);
    }
    /** Record only after a client starts; a ping or selection must not reorder recent play. */
    public synchronized void recordJoined(String address) throws IOException {
        recordJoined(address, System.currentTimeMillis());
    }
    synchronized void recordJoined(String address, long at) throws IOException {
        String canonical = ServerAddress.parse(address).toString();
        List<SavedServer> servers = list(); boolean found = false;
        for (int i = 0; i < servers.size(); i++) {
            SavedServer server = servers.get(i);
            if (server.address.equalsIgnoreCase(canonical)) {
                servers.set(i, new SavedServer(server.name, server.address, Math.max(server.lastJoinedAt, at))); found = true;
            }
        }
        if (found) save(servers); // A removed favorite must not be resurrected by a late launch callback.
    }
    public synchronized void remove(String address) throws IOException {
        List<SavedServer> servers = list(); servers.removeIf(s -> s.address.equalsIgnoreCase(address)); save(servers);
    }
    public synchronized int importVanilla() throws IOException {
        List<SavedServer> servers = list();
        Set<String> existing = new HashSet<>();
        for (SavedServer server : servers) existing.add(server.address.toLowerCase(Locale.ROOT));
        int added = 0;
        for (SavedServer server : new VanillaServersReader().read(vanilla)) {
            if (existing.add(server.address.toLowerCase(Locale.ROOT))) { servers.add(server); added++; }
        }
        if (added > 0) save(servers);
        return added;
    }
    private void save(List<SavedServer> servers) throws IOException {
        Path parent = storage.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "servers-", ".json.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { new GsonBuilder().setPrettyPrinting().create().toJson(servers, writer); }
            try { Files.move(temp, storage, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, storage, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public ServerStatus ping(SavedServer server) {
        ServerAddress address = ServerAddress.parse(server.address);
        MineStat ping = new MineStat(address.host, address.port, 3, MineStat.Request.JSON);
        boolean online = ping.pingResult == MineStat.Retval.SUCCESS && ping.isServerUp();
        return new ServerStatus(online, ping.getStrippedMotd(), ping.getVersion(), ping.getCurrentPlayers(), ping.getMaximumPlayers(),
                ping.getProtocol(), ping.getLatency(), ping.pingResult.name(), online ? ServerIcon.decode(ping.getFavicon()) : null);
    }
}
