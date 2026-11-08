package com.osiris.autoplug.client.profiles;

import com.google.gson.*;
import com.osiris.autoplug.client.tasks.updater.mods.ModrinthAPI;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** A deliberately small client utility recipe. No shader packs, maps or gameplay changes. */
public final class FabricDefaultProfile {
    public static final String PRESET = "fabric-client-utilities-v1";
    static final List<String> ROOTS = Collections.unmodifiableList(Arrays.asList(
            "fabric-api", "sodium", "entityculling", "immediatelyfast", "modmenu"));
    public interface Catalog {
        JsonObject project(String id) throws Exception;
        JsonArray versions(String project, String gameVersion) throws Exception;
        JsonObject version(String id) throws Exception;
    }
    private final ProfileStore profiles;
    private final Catalog catalog;
    private final ProfileUpdates.Downloader downloader;
    public FabricDefaultProfile(ProfileStore profiles, ProfileUpdates updates) {
        this(profiles, new Catalog() {
            private final ModrinthAPI api = new ModrinthAPI();
            public JsonObject project(String id) throws Exception { return api.projectMetadata(id); }
            public JsonArray versions(String id, String game) throws Exception { return api.versionsFor(id, game, "fabric"); }
            public JsonObject version(String id) throws Exception { return api.versionMetadata(id); }
        }, updates::downloadVerified);
    }
    FabricDefaultProfile(ProfileStore profiles, Catalog catalog, ProfileUpdates.Downloader downloader) {
        this.profiles = profiles; this.catalog = catalog; this.downloader = downloader;
    }
    /** Caller holds the profile lease through preparation and the launched process. */
    public void prepare(Profile profile, Consumer<String> progress) throws Exception {
        if (profile.builtinPreset == null || profile.builtinPresetInstalled) return;
        if (!PRESET.equals(profile.builtinPreset) || profile.type != ProfileType.MODS || !"FABRIC".equals(profile.loader))
            throw new IOException("Unsupported default mod recipe for this profile");
        Consumer<String> report = progress == null ? ignored -> { } : progress;
        Path stage = profile.getDirectory().resolve(".updates").resolve("fabric-default-" + UUID.randomUUID());
        List<Path> activated = new ArrayList<>();
        byte[] oldProfile = Files.readAllBytes(profile.getDirectory().resolve("profile.json"));
        Path collectionPath = profile.getDirectory().resolve("collection.json");
        byte[] oldCollection = Files.exists(collectionPath) ? Files.readAllBytes(collectionPath) : null;
        try {
            report.accept("Checking complete Fabric utility pack for Minecraft " + profile.gameVersion);
            Resolver resolver = new Resolver(profile);
            for (String root : ROOTS) resolver.resolve(root, null);
            resolver.checkConflicts();
            if (Files.isSymbolicLink(stage.getParent())) throw new IOException("Profile update directory must not be a symbolic link");
            Files.createDirectories(stage);
            ProfileCollection collection = new ProfileCollection().scan(profile);
            if (!collection.entries.isEmpty()) throw new IOException("The uninstalled default profile already contains user mods. Use an empty default profile to avoid changing that collection.");
            Map<Path, Path> ready = new LinkedHashMap<>(); Set<String> filenames = new HashSet<>();
            for (Artifact artifact : resolver.selected.values()) {
                SearchResult release = artifact.release;
                Path destination = collection.resolve(profile, release.fileName);
                if (!filenames.add(release.fileName.toLowerCase(Locale.ROOT))) throw new IOException("Conflicting mod filenames: " + release.fileName);
                if (Files.exists(destination)) throw new IOException("Utility pack would overwrite an existing mod: " + release.fileName + ". Use a separate profile.");
                Path download = stage.resolve(release.fileName);
                report.accept("Downloading Fabric utility " + artifact.name + " " + release.latestVersion);
                downloader.download(release, download);
                // Injected downloaders also pass through the same integrity/JAR checks in tests.
                validateDownloaded(release, download);
                ready.put(destination, profiles.getCache().store(download));
                CollectionEntry entry = new CollectionEntry(); entry.file = release.fileName; entry.name = artifact.name;
                entry.version = release.latestVersion; entry.modrinthId = artifact.projectId;
                collection.entries.add(entry);
            }
            // All releases, dependencies and bytes are valid before any mod becomes active.
            if (!new ProfileCollection().scan(profile).entries.isEmpty()) throw new IOException("Profile collection changed during utility download; retry with an empty default profile");
            for (Map.Entry<Path, Path> item : ready.entrySet()) {
                if (Files.exists(item.getKey())) throw new IOException("Profile changed during utility download: " + item.getKey().getFileName());
                profiles.getCache().link(item.getValue(), item.getKey()); activated.add(item.getKey());
            }
            collection.save(profile);
            profile.builtinPresetInstalled = true;
            profile.migrationSummary = "Fabric client utilities installed for " + profile.gameVersion + ": Fabric API, Sodium, Entity Culling, ImmediatelyFast, Mod Menu and required libraries. Updates use this profile's collection.";
            profiles.save(profile);
            report.accept("Fabric utility pack ready (" + resolver.selected.size() + " mods and libraries)");
        } catch (Exception e) {
            profile.builtinPresetInstalled = false;
            for (Path file : activated) try { Files.deleteIfExists(file); } catch (IOException rollback) { e.addSuppressed(rollback); }
            try { restore(profile.getDirectory().resolve("profile.json"), oldProfile); } catch (IOException rollback) { e.addSuppressed(rollback); }
            try { if (oldCollection == null) Files.deleteIfExists(collectionPath); else restore(collectionPath, oldCollection); }
            catch (IOException rollback) { e.addSuppressed(rollback); }
            throw new IOException("Fabric utility pack is not ready for Minecraft " + profile.gameVersion + ". Nothing will launch. " + e.getMessage(), e);
        } finally {
            if (Files.isDirectory(stage)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(stage)) { for (Path file : files) Files.deleteIfExists(file); }
                Files.deleteIfExists(stage);
            }
        }
    }
    private void restore(Path path, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".preset-rollback-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private void validateDownloaded(SearchResult release, Path path) throws Exception {
        ProfileUpdates.verify(release, path);
        try (ZipFile jar = new ZipFile(path.toFile())) {
            java.util.zip.ZipEntry metadata = jar.getEntry("fabric.mod.json");
            if (metadata == null || metadata.getSize() > 1024 * 1024) throw new IOException("Mod has no bounded Fabric metadata: " + release.fileName);
            try (InputStream input = jar.getInputStream(metadata)) {
                byte[] bytes = new byte[1024 * 1024 + 1];
                int length = input.readNBytes(bytes, 0, bytes.length);
                if (length > 1024 * 1024) throw new IOException("Fabric metadata exceeds 1 MiB");
                JsonObject mod = JsonParser.parseString(new String(bytes, 0, length, StandardCharsets.UTF_8)).getAsJsonObject();
                if (mod.has("environment") && "server".equals(mod.get("environment").getAsString()))
                    throw new IOException("Server-only artifact rejected: " + release.fileName);
            }
        }
    }
    private class Resolver {
        final Profile profile;
        final Map<String, Artifact> selected = new LinkedHashMap<>();
        final Map<String, JsonObject> projects = new HashMap<>();
        Resolver(Profile profile) { this.profile = profile; }
        Artifact resolve(String projectId, String pinnedVersion) throws Exception {
            JsonObject pinned = pinnedVersion == null ? null : catalog.version(pinnedVersion);
            if (pinned != null && !pinnedVersion.equals(string(pinned, "id"))) throw new IOException("Provider returned the wrong required dependency version");
            if (projectId == null && pinned != null) projectId = string(pinned, "project_id");
            if (projectId == null) throw new IOException("Required dependency has no project or version identity");
            JsonObject project = projects.get(projectId);
            if (project == null) { project = catalog.project(projectId); projects.put(projectId, project); }
            String id = string(project, "id"), name = string(project, "title"); projects.put(id, project);
            if (!"mod".equals(string(project, "project_type")) || !Arrays.asList("required", "optional").contains(string(project, "client_side"))
                    || !Arrays.asList("unsupported", "optional").contains(string(project, "server_side")))
                throw new IOException(name + " is not a client-compatible mod usable without server changes");
            Artifact existing = selected.get(id);
            if (existing != null) {
                if (pinnedVersion != null && !pinnedVersion.equals(string(existing.version, "id")))
                    throw new IOException("Conflicting required versions of " + name);
                return existing;
            }
            if (selected.size() >= 32) throw new IOException("Utility dependency graph is unexpectedly large");
            JsonObject version = pinned;
            if (version == null) for (JsonElement element : catalog.versions(id, profile.gameVersion)) {
                JsonObject candidate = element.getAsJsonObject();
                if (compatible(candidate, id) && (version == null || Instant.parse(string(candidate, "date_published")).isAfter(Instant.parse(string(version, "date_published"))))) version = candidate;
            }
            if (version == null || !compatible(version, id))
                throw new IOException("No stable Fabric release of " + name + " for exact Minecraft " + profile.gameVersion);
            JsonArray one = new JsonArray(); one.add(version);
            SearchResult release = new ModrinthAPI().compatibleResult(one, List.of("fabric"), profile.gameVersion, profile.getDirectory().resolve(".not-installed"));
            release.modrinthProjectId = id;
            URI uri = URI.create(release.downloadUrl);
            if (!"https".equals(uri.getScheme()) || !"cdn.modrinth.com".equals(uri.getHost()) || uri.getUserInfo() != null)
                throw new IOException("Utility artifact is not hosted on Modrinth's HTTPS CDN");
            if (release.fileSize <= 0 || (release.sha512 == null && release.sha1 == null)) throw new IOException("Utility artifact has no verifiable size/checksum");
            Artifact artifact = new Artifact(id, name, version, release); selected.put(id, artifact);
            JsonArray dependencies = version.getAsJsonArray("dependencies");
            if (dependencies != null) for (JsonElement element : dependencies) {
                JsonObject dependency = element.getAsJsonObject();
                if ("required".equals(string(dependency, "dependency_type"))) resolve(nullable(dependency, "project_id"), nullable(dependency, "version_id"));
            }
            return artifact;
        }
        boolean compatible(JsonObject version, String projectId) throws IOException {
            return projectId.equals(string(version, "project_id")) && "release".equals(string(version, "version_type"))
                    && (!version.has("status") || "listed".equals(string(version, "status")))
                    && contains(version.getAsJsonArray("loaders"), "fabric") && contains(version.getAsJsonArray("game_versions"), profile.gameVersion);
        }
        void checkConflicts() throws IOException {
            for (Artifact artifact : selected.values()) {
                JsonArray dependencies = artifact.version.getAsJsonArray("dependencies");
                if (dependencies == null) continue;
                for (JsonElement element : dependencies) {
                    JsonObject dependency = element.getAsJsonObject();
                    if (!"incompatible".equals(string(dependency, "dependency_type"))) continue;
                    String project = nullable(dependency, "project_id"), version = nullable(dependency, "version_id");
                    for (Artifact other : selected.values()) if ((project == null || project.equals(other.projectId))
                            && (version == null || version.equals(string(other.version, "id"))))
                        throw new IOException("Provider declares incompatible utilities: " + artifact.name + " / " + other.name);
                }
            }
        }
    }
    private static boolean contains(JsonArray values, String value) { if (values != null) for (JsonElement item : values) if (value.equals(item.getAsString())) return true; return false; }
    private static String nullable(JsonObject object, String key) { return !object.has(key) || object.get(key).isJsonNull() ? null : object.get(key).getAsString(); }
    private static String string(JsonObject object, String key) throws IOException {
        String value = nullable(object, key); if (value == null || value.isEmpty()) throw new IOException("Provider metadata missing " + key); return value;
    }
    private static class Artifact {
        final String projectId, name; final JsonObject version; final SearchResult release;
        Artifact(String projectId, String name, JsonObject version, SearchResult release) { this.projectId = projectId; this.name = name; this.version = version; this.release = release; }
    }
}
