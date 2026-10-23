package com.osiris.autoplug.client.mc.client;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Non-secret configuration only. Access/refresh tokens never enter this file. */
public final class ClientSettings {
    private static final java.util.Set<String> KEYS = java.util.Set.of("clientId", "javaHomes", "defaultTemplate", "username", "offlineFixtureMode", "cacheDirectory");
    private final Path file;
    private final ClientStorage storage = new ClientStorage();
    private final Properties values;

    public ClientSettings(Path dataRoot) throws IOException {
        file = dataRoot.resolve("client-settings.properties"); values = storage.read(file);
        try { for (String key : values.stringPropertyNames()) validate(key, values.getProperty(key)); }
        catch (IllegalArgumentException error) { throw new IOException("Invalid non-secret client settings", error); }
    }
    public synchronized String get(String key, String fallback) { return values.getProperty(key, fallback); }
    public synchronized void set(String key, String value) {
        validate(key, value);
        if (value == null) values.remove(key); else values.setProperty(key, value);
    }
    public synchronized void save() throws IOException { storage.write(file, values); }
    public synchronized List<Path> javaHomes(Path autoplugWorkingDirectory) {
        List<Path> paths = new ArrayList<>();
        paths.add(Paths.get(System.getProperty("java.home")));
        paths.add(autoplugWorkingDirectory.resolve("autoplug/system/jre"));
        for (String path : get("javaHomes", "").split("\\r?\\n")) if (!path.trim().isEmpty()) paths.add(Paths.get(path.trim()));
        return paths;
    }

    private void validate(String key, String value) {
        if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown non-secret setting");
        if (value == null) return;
        if (value.length() > 16384 || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Setting value exceeds limit or contains a NUL");
        if (key.equals("offlineFixtureMode") && !value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Offline fixture mode must be true or false");
        if (key.equals("username") && !value.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid Minecraft username");
        if (key.equals("defaultTemplate") && !value.isEmpty() && !value.matches("[a-z0-9][a-z0-9._-]{0,79}")) throw new IllegalArgumentException("Invalid default template ID");
        if (key.equals("javaHomes")) {
            if (value.split("\\r?\\n", -1).length > 32) throw new IllegalArgumentException("Too many Java home paths");
            for (String path : value.split("\\r?\\n")) if (!path.trim().isEmpty()) Paths.get(path.trim());
        }
        if (key.equals("cacheDirectory") && !value.trim().isEmpty()) Paths.get(value.trim());
    }
}
