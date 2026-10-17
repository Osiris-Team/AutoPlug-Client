package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/** Mojang library/argument rules, evaluated using an explicit platform for fixture coverage. */
public final class LaunchPlatform {
    private final String name;
    private final String architecture;
    private final String version;
    private final Map<String, Boolean> features;

    public LaunchPlatform(String name, String architecture, String version, Map<String, Boolean> features) {
        this.name = name;
        this.architecture = architecture;
        this.version = version;
        this.features = Collections.unmodifiableMap(new java.util.HashMap<>(features));
    }

    public String getName() { return name; }
    public String getArchitecture() { return architecture; }
    public String getNativeBits() { return architecture.contains("64") ? "64" : "32"; }

    public boolean allows(JsonObject object) throws IOException {
        if (!object.has("rules")) return true;
        boolean allowed = false;
        try {
            for (JsonElement entry : object.getAsJsonArray("rules")) {
                JsonObject rule = entry.getAsJsonObject();
                boolean matches = true;
                if (rule.has("os")) {
                    JsonObject os = rule.getAsJsonObject("os");
                    if (os.has("name")) matches &= name.equals(os.get("name").getAsString());
                    if (os.has("arch")) matches &= architecture.matches(os.get("arch").getAsString());
                    if (os.has("version")) matches &= version.matches(os.get("version").getAsString());
                }
                if (rule.has("features")) {
                    for (Map.Entry<String, JsonElement> feature : rule.getAsJsonObject("features").entrySet()) {
                        matches &= features.getOrDefault(feature.getKey(), false) == feature.getValue().getAsBoolean();
                    }
                }
                if (matches) allowed = "allow".equals(rule.get("action").getAsString());
            }
            return allowed;
        } catch (RuntimeException error) {
            throw new IOException("Invalid Minecraft platform rule", error);
        }
    }
}
