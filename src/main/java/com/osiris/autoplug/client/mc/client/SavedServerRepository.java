package com.osiris.autoplug.client.mc.client;

import com.osiris.autoplug.client.mc.VanillaServerListReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Own saved-server list. Import never rewrites Minecraft's servers.dat. */
public final class SavedServerRepository {
    public static final class SavedServer {
        private final String id, name, address, loader;
        SavedServer(String id, String name, String address, String loader) {
            if (id == null || !id.matches("[a-z0-9-]{1,80}")) throw new IllegalArgumentException("Invalid server ID");
            if (name == null || name.trim().isEmpty() || name.length() > 200 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("A server name is required without control characters");
            if (loader == null || !loader.matches("[A-Za-z0-9_-]{1,40}")) throw new IllegalArgumentException("Select an explicit loader");
            this.id = id; this.name = name; this.address = new ServerAddress(address).toString(); this.loader = loader;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getAddress() { return address; }
        public String getLoader() { return loader; }
        @Override public String toString() { return name + " (" + address + ")"; }
    }

    private final Path file;
    private final ClientStorage storage = new ClientStorage();
    public SavedServerRepository(Path dataRoot) { file = dataRoot.resolve("saved-servers.properties"); }

    public synchronized List<SavedServer> list() throws IOException {
        Properties values = storage.read(file);
        List<SavedServer> result = new ArrayList<>();
        try {
            if (values.containsKey("formatVersion") && !"1".equals(values.getProperty("formatVersion"))) throw new IllegalArgumentException("Unsupported saved-server format");
            if (!values.isEmpty() && !values.containsKey("count")) throw new IllegalArgumentException("Missing saved-server count");
            int count = Integer.parseInt(values.getProperty("count", "0"));
            if (count < 0 || count > 10000) throw new IllegalArgumentException("Invalid saved-server count");
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < count; i++) {
                String prefix = "server." + i + ".";
                result.add(new SavedServer(values.getProperty(prefix + "id"), values.getProperty(prefix + "name"),
                        values.getProperty(prefix + "address"), values.getProperty(prefix + "loader")));
                if (!ids.add(result.get(result.size() - 1).id)) throw new IllegalArgumentException("Duplicate saved-server ID");
            }
        } catch (IllegalArgumentException error) { throw new IOException("Invalid AutoPlug saved-server list", error); }
        return Collections.unmodifiableList(result);
    }

    public synchronized SavedServer add(String name, String address, String loader) throws IOException {
        List<SavedServer> entries = new ArrayList<>(list());
        SavedServer entry = new SavedServer(UUID.randomUUID().toString(), name, address, loader);
        entries.add(entry); save(entries); return entry;
    }

    public synchronized void update(String id, String name, String address, String loader) throws IOException {
        List<SavedServer> entries = new ArrayList<>(list());
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) {
            entries.set(i, new SavedServer(id, name, address, loader)); save(entries); return;
        }
        throw new IOException("Unknown saved server: " + id);
    }

    public synchronized void delete(String id) throws IOException {
        List<SavedServer> entries = new ArrayList<>(list());
        if (!entries.removeIf(entry -> entry.id.equals(id))) throw new IOException("Unknown saved server: " + id);
        save(entries);
    }

    public synchronized int importVanilla(Path path, String loader) throws IOException {
        List<SavedServer> entries = new ArrayList<>(list());
        int added = 0;
        for (VanillaServerListReader.ServerEntry entry : new VanillaServerListReader().read(path)) {
            String address;
            try { address = new ServerAddress(entry.getAddress()).toString(); }
            catch (IllegalArgumentException error) { throw new IOException("Invalid imported Minecraft server address", error); }
            if (entries.stream().anyMatch(existing -> existing.address.equals(address))) continue;
            try { entries.add(new SavedServer(UUID.randomUUID().toString(), entry.getName(), address, loader)); }
            catch (IllegalArgumentException error) { throw new IOException("Invalid imported Minecraft server metadata", error); }
            added++;
        }
        if (added > 0) save(entries);
        return added;
    }

    private void save(List<SavedServer> entries) throws IOException {
        if (entries.size() > 10000) throw new IOException("Saved-server limit reached");
        Properties values = new Properties(); values.setProperty("formatVersion", "1"); values.setProperty("count", Integer.toString(entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            SavedServer entry = entries.get(i); String prefix = "server." + i + ".";
            values.setProperty(prefix + "id", entry.id); values.setProperty(prefix + "name", entry.name);
            values.setProperty(prefix + "address", entry.address); values.setProperty(prefix + "loader", entry.loader);
        }
        storage.write(file, values);
    }
}
