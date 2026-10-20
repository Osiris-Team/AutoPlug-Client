package com.osiris.autoplug.client.clientapp;

import com.osiris.autoplug.client.browser.ClientDataFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Public preferences only. OAuth access/refresh tokens are never serialized. */
public final class ClientSettings {
    public String offlineName = "Player", microsoftClientId = "", templateId = "";
    public boolean offlineMode = true;
    private volatile int worldPort;
    private volatile boolean upnpAllowed = true;
    public final Map<Integer, String> javaPaths = new LinkedHashMap<>();
    private final Path file;
    public ClientSettings(Path root) throws IOException {
        file = root.resolve("client-settings.properties");
        ClientDataFiles.rejectLinks(file);
        if (!Files.exists(file)) return;
        if (Files.size(file) > 32 * 1024) throw new IOException("Client settings are too large");
        Properties values = new Properties(); try (InputStream input = Files.newInputStream(file)) { values.load(input); }
        offlineName = values.getProperty("offlineName", "Player"); templateId = values.getProperty("templateId", "");
        microsoftClientId = values.getProperty("microsoftClientId", ""); offlineMode = Boolean.parseBoolean(values.getProperty("offlineMode", "true"));
        try { worldPort = Integer.parseInt(values.getProperty("worldPort", "0")); validatePort(worldPort); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid saved virtual world port", invalid); }
        upnpAllowed = Boolean.parseBoolean(values.getProperty("upnpAllowed", "true"));
        for (int major : new int[]{8, 17, 21, 25}) javaPaths.put(major, values.getProperty("java." + major, ""));
    }
    public synchronized void save() throws IOException {
        apply(offlineName, offlineMode, microsoftClientId, templateId, new LinkedHashMap<>(javaPaths));
    }
    /** Validate and persist a candidate before making it visible to a running launch worker. */
    public synchronized void apply(String offlineName, boolean offlineMode, String microsoftClientId, String templateId, Map<Integer, String> javaPaths) throws IOException {
        apply(offlineName, offlineMode, microsoftClientId, templateId, javaPaths, worldPort, upnpAllowed);
    }
    public synchronized void apply(String offlineName, boolean offlineMode, String microsoftClientId, String templateId, Map<Integer, String> javaPaths, int worldPort, boolean upnpAllowed) throws IOException {
        javaPaths = new LinkedHashMap<>(javaPaths);
        validatePort(worldPort);
        if (offlineName == null || !offlineName.matches("[a-zA-Z0-9_]{1,16}")) throw new IllegalArgumentException("Offline name must contain 1–16 letters, digits or underscores");
        if (microsoftClientId == null || (!microsoftClientId.isEmpty() && !microsoftClientId.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))) throw new IllegalArgumentException("Enter your registered public Microsoft application's client ID");
        if (templateId == null || (!templateId.isEmpty() && !templateId.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}"))) throw new IllegalArgumentException("Invalid template profile ID");
        Properties values = new Properties(); values.setProperty("offlineName", offlineName); values.setProperty("offlineMode", String.valueOf(offlineMode));
        values.setProperty("microsoftClientId", microsoftClientId); values.setProperty("templateId", templateId);
        values.setProperty("worldPort", Integer.toString(worldPort)); values.setProperty("upnpAllowed", Boolean.toString(upnpAllowed));
        for (int major : new int[]{8, 17, 21, 25}) values.setProperty("java." + major, javaPaths.getOrDefault(major, ""));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); values.store(bytes, "AutoPlug client preferences; no account tokens");
        ClientDataFiles.atomicWrite(file, bytes.toByteArray());
        this.offlineName = offlineName; this.offlineMode = offlineMode; this.microsoftClientId = microsoftClientId; this.templateId = templateId;
        this.javaPaths.clear(); this.javaPaths.putAll(javaPaths);
        this.worldPort = worldPort; this.upnpAllowed = upnpAllowed;
    }
    private static void validatePort(int port) { if (port != 0 && (port < 1024 || port > 65535)) throw new IllegalArgumentException("World port must be 0 (automatic) or 1024–65535"); }
    public int worldPort() { return worldPort; }
    public boolean upnpAllowed() { return upnpAllowed; }
    public synchronized Path javaOverride(int major) { String path = javaPaths.get(major); return path == null || path.trim().isEmpty() ? null : Paths.get(path); }
}
