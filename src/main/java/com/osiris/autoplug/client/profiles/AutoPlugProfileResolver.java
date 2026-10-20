/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import com.google.gson.*;
import com.osiris.autoplug.client.tasks.updater.mods.ModrinthAPI;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.InstalledPluginLoader;
import com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exact-file Modrinth lookup, plus explicitly configured Spigot/Bukkit identities using existing AutoPlug searches. */
public final class AutoPlugProfileResolver implements ProfileUpdater.Resolver {
    private final ModrinthAPI api;
    private final ResourceFinder finder;
    public AutoPlugProfileResolver() { this(new ModrinthAPI(), new ResourceFinder()); }
    public AutoPlugProfileResolver(ModrinthAPI api, ResourceFinder finder) { this.api = api; this.finder = finder; }
    @Override public ProfileUpdater.Resolution resolve(Profile profile, Path installed) throws Exception {
        Properties sources = new Properties();
        Path sourceFile = profile.directoryPath.resolve("asset-sources.properties");
        if (Files.exists(sourceFile)) try (InputStream input = Files.newInputStream(sourceFile)) { sources.load(input); }
        String filename = installed.getFileName().toString().replaceFirst("\\.disabled$", "");
        String project = sources.getProperty(filename + ".modrinth");
        if (project == null) {
            JsonObject exact = api.identifyProfileFile(installed);
            if (exact != null) project = exact.get("project_id").getAsString();
        }
        if (project != null) return fromVersion(project, finder.findProfileRelease(api, project, loaders(profile), profile.gameVersion, null));
        String curseforge = sources.getProperty(filename + ".curseforge");
        if (curseforge != null && profile.type != Profile.Type.PLUGINS) return fromCurseForge(curseforge, finder.findProfileCurseForgeRelease(curseforge, profile.loader, profile.gameVersion));
        if (profile.type == Profile.Type.PLUGINS) {
            String spigot = sources.getProperty(filename + ".spigot"), bukkit = sources.getProperty(filename + ".bukkit");
            if (spigot != null || bukkit != null) return plugin(profile, installed, spigot, bukkit);
        }
        // Unknown identity is reported for explicit review, never mistaken for a timeout or malformed API response.
        return ProfileUpdater.Resolution.unsupported("unknown:" + SharedArtifactCache.hash(installed, "SHA-1"));
    }
    @Override public ProfileUpdater.Resolution dependency(Profile profile, ProfileUpdater.Dependency dependency) throws Exception {
        String project = dependency.projectId;
        if (project != null && project.startsWith("curseforge:")) {
            String id = project.substring("curseforge:".length());
            return fromCurseForge(id, finder.findProfileCurseForgeRelease(id, profile.loader, profile.gameVersion));
        }
        if (project == null && dependency.versionId != null) {
            JsonObject pinned = api.profileVersion(dependency.versionId);
            if (pinned != null) project = pinned.get("project_id").getAsString();
        }
        if (project == null) throw new IOException("Required dependency has no resolvable project identity");
        return fromVersion(project, finder.findProfileRelease(api, project, loaders(profile), profile.gameVersion, dependency.versionId));
    }
    private ProfileUpdater.Resolution plugin(Profile profile, Path installed, String spigot, String bukkit) throws Exception {
        MinecraftPlugin plugin = new MinecraftPlugin(installed.toString(), installed.getFileName().toString(), "0", "", parseId(spigot), parseId(bukkit), null);
        SearchResult found;
        String identity;
        if (spigot != null) {
            JsonObject resource = getJson("https://api.spiget.org/v2/resources/" + parseId(spigot));
            boolean compatible = false;
            JsonArray tested = resource.getAsJsonArray("testedVersions");
            if (tested != null) for (JsonElement version : tested) if (version.getAsString().equals(profile.gameVersion) || profile.gameVersion.startsWith(version.getAsString() + ".")) compatible = true;
            if (!compatible) return ProfileUpdater.Resolution.unsupported("spigot:" + spigot);
            found = finder.findPluginBySpigotId(plugin); identity = "spigot:" + spigot;
        } else {
            found = finder.findProfilePluginByBukkitId(plugin, profile.gameVersion); identity = "bukkit:" + bukkit;
        }
        if (found == null || found.isError()) throw new IOException("Plugin lookup failed", found == null ? null : found.getException());
        if (found.isPremium) throw new IOException("Premium plugin requires a separately authorized download");
        if (found.type == SearchResult.Type.RESOURCE_NOT_FOUND) return ProfileUpdater.Resolution.unsupported(identity);
        String name = found.fileName == null ? installed.getFileName().toString().replaceFirst("\\.disabled$", "") : found.fileName;
        return new ProfileUpdater.Resolution(true, identity, found.latestVersion, found.latestVersion, name, found.downloadUrl, null, null, found.expectedMD5, Collections.emptyList());
    }
    private static int parseId(String value) throws IOException { if (value == null) return 0; try { return Integer.parseInt(value); } catch (NumberFormatException bad) { throw new IOException("Invalid plugin project id", bad); } }
    private static List<String> loaders(Profile profile) {
        if (profile.type == Profile.Type.PLUGINS) return InstalledPluginLoader.forProfile(profile.loader);
        return Collections.singletonList(profile.loader.toLowerCase(Locale.ROOT));
    }
    private static ProfileUpdater.Resolution fromVersion(String project, JsonObject version) throws IOException {
        if (version == null) return ProfileUpdater.Resolution.unsupported(project);
        JsonObject selected = null;
        for (JsonElement element : version.getAsJsonArray("files")) {
            JsonObject file = element.getAsJsonObject();
            if (!file.get("filename").getAsString().endsWith(".jar")) continue;
            if (selected == null || file.has("primary") && file.get("primary").getAsBoolean()) selected = file;
        }
        if (selected == null) throw new IOException("Compatible release has no jar artifact");
        JsonObject hashes = selected.getAsJsonObject("hashes");
        List<ProfileUpdater.Dependency> dependencies = new ArrayList<>();
        List<String> incompatible = new ArrayList<>();
        if (version.has("dependencies")) for (JsonElement element : version.getAsJsonArray("dependencies")) {
            JsonObject dependency = element.getAsJsonObject();
            if ("required".equals(dependency.get("dependency_type").getAsString())) dependencies.add(new ProfileUpdater.Dependency(string(dependency, "project_id"), string(dependency, "version_id")));
            if ("incompatible".equals(dependency.get("dependency_type").getAsString())) {
                String incompatibleProject = string(dependency, "project_id");
                if (incompatibleProject == null) throw new IOException("Incompatible dependency is missing its project identity");
                incompatible.add(incompatibleProject);
            }
        }
        return new ProfileUpdater.Resolution(true, version.get("project_id").getAsString(), version.get("id").getAsString(), version.get("version_number").getAsString(), selected.get("filename").getAsString(), selected.get("url").getAsString(), string(hashes, "sha1"), string(hashes, "sha512"), null, dependencies, incompatible);
    }
    private static ProfileUpdater.Resolution fromCurseForge(String project, JsonObject file) throws IOException {
        if (file == null) return ProfileUpdater.Resolution.unsupported("curseforge:" + project);
        String download = string(file, "downloadUrl");
        if (download == null) throw new IOException("CurseForge author has not exposed an authorized download URL");
        String sha1 = null, md5 = null;
        for (JsonElement value : file.getAsJsonArray("hashes")) {
            JsonObject hash = value.getAsJsonObject(); int type = hash.get("algo").getAsInt();
            if (type == 1) sha1 = hash.get("value").getAsString(); if (type == 2) md5 = hash.get("value").getAsString();
        }
        List<ProfileUpdater.Dependency> dependencies = new ArrayList<>(); List<String> incompatible = new ArrayList<>();
        if (file.has("dependencies")) for (JsonElement value : file.getAsJsonArray("dependencies")) {
            JsonObject dependency = value.getAsJsonObject(); String id = "curseforge:" + dependency.get("modId").getAsString(); int type = dependency.get("relationType").getAsInt();
            if (type == 3) dependencies.add(new ProfileUpdater.Dependency(id, null)); if (type == 5) incompatible.add(id);
        }
        return new ProfileUpdater.Resolution(true, "curseforge:" + project, file.get("id").getAsString(), file.get("displayName").getAsString(), file.get("fileName").getAsString(), download, sha1, null, md5, dependencies, incompatible);
    }
    private static String string(JsonObject object, String key) { return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null; }
    private static JsonObject getJson(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setRequestProperty("User-Agent", "AutoPlug-Client/10.2.0");
        try {
            int status = connection.getResponseCode(); if (status != 200) throw new IOException("Plugin API returned HTTP " + status);
            try (Reader input = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) { return JsonParser.parseReader(input).getAsJsonObject(); }
            catch (RuntimeException malformed) { throw new IOException("Invalid plugin API response", malformed); }
        } finally { connection.disconnect(); }
    }
}
