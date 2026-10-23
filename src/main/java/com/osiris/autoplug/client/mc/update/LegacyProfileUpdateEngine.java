package com.osiris.autoplug.client.mc.update;

import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import com.osiris.autoplug.client.tasks.updater.mods.InstalledModLoader;
import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.plugins.InstalledPluginLoader;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Profile adapter for the existing metadata reader and resource providers, without global YAML writes. */
public final class LegacyProfileUpdateEngine implements ProfileUpdateService.Engine {
    private final ResourceFinder finder;
    private final OkHttpClient http;

    public LegacyProfileUpdateEngine() { this(new ResourceFinder()); }
    public LegacyProfileUpdateEngine(ResourceFinder finder) {
        this.finder = java.util.Objects.requireNonNull(finder);
        http = new OkHttpClient.Builder().followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build();
    }

    @Override public ProfileUpdateService.Decision resolve(Profile profile, Path installedJar, String targetVersion) throws IOException {
        // UtilsMinecraft deliberately scans *.jar only. A private read-only copy lets the same
        // real parser inspect an already-disabled jar without renaming a live profile file.
        Path temporary = Files.createTempDirectory("autoplug-profile-inspect-");
        Path jar = temporary.resolve("inspect.jar");
        try {
            Files.copy(installedJar, jar);
            SearchResult result;
            if (profile.getType() == ProfileType.PLUGINS) {
                String software = profile.getLoader().toLowerCase(java.util.Locale.ROOT);
                if (!java.util.Set.of("bukkit", "spigot", "paper", "purpur", "velocity", "bungeecord", "waterfall",
                        "folia", "luminolmc", "canvas", "shreddedpaper").contains(software)) {
                    return ProfileUpdateService.Decision.error("Unsupported plugin loader: " + profile.getLoader());
                }
                List<MinecraftPlugin> plugins = new UtilsMinecraft().getPlugins(temporary.toFile());
                if (plugins.size() != 1) return ProfileUpdateService.Decision.error("Cannot read a unique plugin descriptor; configure or remove this jar");
                MinecraftPlugin plugin = plugins.get(0);
                Yaml settings = settings(profile, "plugins");
                if (excluded(settings, "plugins", plugin.getName())) return excluded(profile, targetVersion);
                plugin.modrinthId = value(settings, "plugins", plugin.getName(), "modrinth-id", plugin.modrinthId);
                if (plugin.modrinthId == null) {
                    return ProfileUpdateService.Decision.error("Set plugins.<name>.modrinth-id in this profile's config/plugins.yml; legacy latest-only Spigot/Bukkit checks cannot prove target-version compatibility");
                }
                result = finder.findCompatibleProfilePlugin(new InstalledPluginLoader(profile.getLoader()).getLoaderList(), plugin, targetVersion);
            } else {
                List<MinecraftMod> mods = new UtilsMinecraft().getMods(temporary.toFile());
                if (mods.size() != 1) return ProfileUpdateService.Decision.error("Cannot read a unique mod descriptor; configure or remove this jar");
                MinecraftMod mod = mods.get(0);
                Yaml settings = settings(profile, "mods");
                if (excluded(settings, "mods", mod.getName())) return excluded(profile, targetVersion);
                mod.modrinthId = value(settings, "mods", mod.getName(), "modrinth-id", mod.modrinthId);
                mod.curseforgeId = value(settings, "mods", mod.getName(), "curseforge-id", mod.curseforgeId);
                // force-latest is intentionally not copied: it would violate this profile's target.
                result = finder.findCompatibleProfileMod(new InstalledModLoader(profile.getLoader()), mod, targetVersion);
            }
            if (result.type == SearchResult.Type.NO_COMPATIBLE_RELEASE) {
                return ProfileUpdateService.Decision.incompatible("No release for Minecraft " + targetVersion + " / " + profile.getLoader());
            }
            if (!SearchResult.isMatchFound(result) || result.downloadUrl == null) {
                return ProfileUpdateService.Decision.error(result.exception == null
                        ? "No compatible provider mapping; add this profile's modrinth-id or curseforge-id"
                        : "Provider check failed: " + result.exception.getClass().getSimpleName());
            }
            requireHttps(result.downloadUrl);
            return ProfileUpdateService.Decision.replace(result.downloadUrl, result.sha256,
                    "Install compatible release " + result.latestVersion + " for " + targetVersion);
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Cannot inspect profile asset: " + installedJar.getFileName(), failure);
        } finally {
            Files.deleteIfExists(jar);
            Files.deleteIfExists(temporary);
        }
    }

    @Override public void download(ProfileUpdateService.Decision decision, Path destination) throws IOException {
        requireHttps(decision.getDownloadUrl());
        Request request = new Request.Builder().url(decision.getDownloadUrl()).header("User-Agent", "AutoPlug-Client").build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Artifact HTTP " + response.code());
            if (!response.request().url().isHttps()) throw new IOException("Artifact redirected to insecure HTTP");
            long maximum = 512L * 1024 * 1024;
            if (response.body().contentLength() > maximum) throw new IOException("Artifact is too large");
            try (InputStream input = response.body().byteStream(); OutputStream output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[16384]; long total = 0; int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maximum) throw new IOException("Artifact is too large");
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    private static ProfileUpdateService.Decision excluded(Profile profile, String target) {
        return profile.getGameVersion().equals(target)
                ? ProfileUpdateService.Decision.keep("Explicitly excluded by this profile's configuration; not checked")
                : ProfileUpdateService.Decision.error("Excluded asset cannot be certified for a different Minecraft version");
    }

    private static Yaml settings(Profile profile, String kind) throws Exception {
        Path file = profile.getConfigDirectory().resolve(kind + ".yml");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a direct profile configuration file");
        // A stream-backed reader has no output path or watcher and never saves on check.
        try (InputStream input = Files.newInputStream(file)) {
            Yaml yaml = new Yaml(input, null);
            yaml.load();
            return yaml;
        }
    }

    private static String value(Yaml yaml, String root, String name, String key, String fallback) throws Exception {
        if (yaml == null || name == null) return fallback;
        YamlSection section = yaml.get(root, name, key);
        return section == null || section.asString() == null || section.asString().trim().isEmpty() ? fallback : section.asString().trim();
    }

    private static boolean excluded(Yaml yaml, String root, String name) throws Exception {
        return Boolean.parseBoolean(value(yaml, root, name, "exclude", "false"));
    }

    private static void requireHttps(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IOException("Expected an HTTPS artifact URL without credentials");
            }
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid artifact URL", invalid); }
    }
}
