package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/** Mojang's ordered allow/disallow rules. An absent rule set allows; a present empty set denies. */
public final class LauncherPlatform {
    public final String os, arch, version;
    public LauncherPlatform(String os, String arch, String version) {
        this.os = os; this.arch = arch; this.version = version;
    }
    public static LauncherPlatform current() {
        String name = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        return new LauncherPlatform(name.contains("win") ? "windows" : name.contains("mac") ? "osx" : "linux",
                System.getProperty("os.arch"), System.getProperty("os.version"));
    }
    public String bits() { return arch.contains("64") ? "64" : "32"; }
    public boolean allows(JsonObject node) { return allows(node, Collections.emptyMap()); }
    public boolean allows(JsonObject node, Map<String, Boolean> features) {
        if (!node.has("rules")) return true;
        boolean allow = false;
        for (JsonElement element : node.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            boolean matches = true;
            if (rule.has("os")) {
                JsonObject condition = rule.getAsJsonObject("os");
                if (condition.has("name")) matches &= os.equals(condition.get("name").getAsString());
                if (condition.has("arch")) matches &= Pattern.matches(condition.get("arch").getAsString(), arch);
                if (condition.has("version")) matches &= Pattern.matches(condition.get("version").getAsString(), version);
            }
            if (rule.has("features")) for (Map.Entry<String, JsonElement> e : rule.getAsJsonObject("features").entrySet())
                matches &= features.getOrDefault(e.getKey(), false) == e.getValue().getAsBoolean();
            if (matches) allow = "allow".equals(rule.get("action").getAsString());
        }
        return allow;
    }
}
