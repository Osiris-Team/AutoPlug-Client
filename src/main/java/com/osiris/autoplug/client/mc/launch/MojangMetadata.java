package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Official Mojang version manifests plus native Fabric/Quilt and installed Forge/NeoForge metadata. */
public final class MojangMetadata {
    private final LaunchTransport transport;
    private final ArtifactCache cache;

    public MojangMetadata(LaunchTransport transport, ArtifactCache cache) {
        this.transport = transport;
        this.cache = cache;
    }

    public JsonObject version(String id, Cancellation cancellation) throws IOException {
        return version(id, cancellation, new HashSet<>());
    }

    private JsonObject version(String id, Cancellation cancellation, Set<String> visiting) throws IOException {
        validateId(id);
        cancellation.check();
        if (visiting.size() >= 8 || !visiting.add(id)) throw new IOException("Cyclic or excessive Minecraft version inheritance");
        try {
            Path file = cache.resolve("versions/" + id + "/" + id + ".json");
            JsonObject child;
            // Local loader profiles have no entry in Mojang's version manifest.
            if (Files.isRegularFile(file) && Files.isRegularFile(file.resolveSibling("autoplug-loader-profile"))) {
                child = read(file);
            } else {
                JsonObject manifest = transport.json("GET", URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"),
                        Collections.emptyMap(), null, cancellation);
                JsonObject selected = null;
                for (JsonElement entry : manifest.getAsJsonArray("versions")) {
                    JsonObject candidate = entry.getAsJsonObject();
                    if (id.equals(candidate.get("id").getAsString())) { selected = candidate; break; }
                }
                if (selected == null) throw new IOException("Unknown official Minecraft version: " + id);
                file = cache.obtain("versions/" + id + "/" + id + ".json", URI.create(selected.get("url").getAsString()),
                        selected.get("sha1").getAsString(), -1, cancellation);
                child = read(file);
            }
            if (!id.equals(child.get("id").getAsString())) throw new IOException("Minecraft version ID mismatch");
            if (!child.has("inheritsFrom")) return child;
            JsonObject base = version(child.get("inheritsFrom").getAsString(), cancellation, visiting);
            return merge(base, child);
        } catch (RuntimeException error) {
            throw new IOException("Invalid Minecraft version metadata", error);
        } finally {
            visiting.remove(id);
        }
    }

    /** Fetches an official loader profile. This does not authenticate or launch Minecraft. */
    public String installFabric(String gameVersion, String loaderVersion, Cancellation cancellation) throws IOException {
        return installRemoteProfile("https://meta.fabricmc.net/v2/versions/loader/", gameVersion, loaderVersion, cancellation);
    }

    public String installQuilt(String gameVersion, String loaderVersion, Cancellation cancellation) throws IOException {
        return installRemoteProfile("https://meta.quiltmc.org/v3/versions/loader/", gameVersion, loaderVersion, cancellation);
    }

    private String installRemoteProfile(String prefix, String gameVersion, String loaderVersion, Cancellation cancellation) throws IOException {
        validateId(gameVersion);
        validateId(loaderVersion);
        JsonObject profile = transport.json("GET", URI.create(prefix + gameVersion + "/" + loaderVersion + "/profile/json"),
                Collections.emptyMap(), null, cancellation);
        if (!profile.has("inheritsFrom") || !gameVersion.equals(profile.get("inheritsFrom").getAsString())) {
            throw new IOException("Loader profile does not inherit the requested Minecraft version");
        }
        return saveLoaderProfile(profile);
    }

    /** Imports the JSON emitted by the official Forge/NeoForge installer, never GPL launcher code. */
    public String importInstalledLoaderProfile(Path json) throws IOException {
        return saveLoaderProfile(read(json));
    }

    private String saveLoaderProfile(JsonObject profile) throws IOException {
        try {
            String id = profile.get("id").getAsString();
            validateId(id);
            if (!profile.has("inheritsFrom") || !profile.has("mainClass") || !profile.has("libraries")) {
                throw new IOException("An installed loader profile must declare inheritance, main class and libraries");
            }
            validateId(profile.get("inheritsFrom").getAsString());
            Path target = cache.resolve("versions/" + id + "/" + id + ".json");
            Files.createDirectories(target.getParent());
            Files.write(target, profile.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(target.resolveSibling("autoplug-loader-profile"), new byte[0]);
            return id;
        } catch (RuntimeException error) {
            throw new IOException("Invalid loader profile", error);
        }
    }

    /** Loader libraries override the same group/artifact/classifier; base JVM/game arguments remain. */
    public JsonObject merge(JsonObject parent, JsonObject child) throws IOException {
        JsonObject merged = parent.deepCopy();
        for (Map.Entry<String, JsonElement> entry : child.entrySet()) {
            if (!entry.getKey().equals("libraries") && !entry.getKey().equals("arguments")) {
                merged.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        LinkedHashMap<String, JsonElement> libraries = new LinkedHashMap<>();
        if (parent.has("libraries")) for (JsonElement entry : parent.getAsJsonArray("libraries")) libraries.put(libraryKey(entry), entry.deepCopy());
        if (child.has("libraries")) for (JsonElement entry : child.getAsJsonArray("libraries")) libraries.put(libraryKey(entry), entry.deepCopy());
        JsonArray list = new JsonArray();
        for (JsonElement entry : libraries.values()) list.add(entry);
        merged.add("libraries", list);
        JsonObject arguments = new JsonObject();
        for (String group : new String[]{"jvm", "game"}) {
            JsonArray combined = new JsonArray();
            for (JsonObject source : new JsonObject[]{parent, child}) {
                if (source.has("arguments") && source.getAsJsonObject("arguments").has(group)) {
                    for (JsonElement entry : source.getAsJsonObject("arguments").getAsJsonArray(group)) combined.add(entry.deepCopy());
                }
            }
            if (combined.size() > 0) arguments.add(group, combined);
        }
        if (arguments.size() > 0) merged.add("arguments", arguments);
        // The inherited client JAR belongs to the vanilla base, not the loader profile ID.
        merged.addProperty("autoplugClientVersion", parent.has("autoplugClientVersion")
                ? parent.get("autoplugClientVersion").getAsString() : parent.get("id").getAsString());
        return merged;
    }

    private String libraryKey(JsonElement entry) throws IOException {
        String[] parts = entry.getAsJsonObject().get("name").getAsString().split(":");
        if (parts.length < 3 || parts.length > 4) throw new IOException("Invalid Maven library coordinate");
        return parts[0] + ":" + parts[1] + (parts.length == 4 ? ":" + parts[3] : "");
    }

    private JsonObject read(Path file) throws IOException {
        if (Files.size(file) > 8 * 1024 * 1024) throw new IOException("Version metadata too large");
        try { return JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException error) { throw new IOException("Invalid version JSON", error); }
    }

    private void validateId(String id) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,159}")) throw new IOException("Invalid version identifier");
    }
}
