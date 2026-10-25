package com.osiris.autoplug.client.mc.update;

import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileRepository;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import com.osiris.autoplug.client.tasks.updater.mods.InstalledModLoader;
import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyProfileUpdateEngineTest {
    @TempDir Path temporary;

    @Test void readsActualFabricMetadataAndOnlyTheSelectedProfilesYaml() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary.resolve("data"));
        Profile p = repository.create("fabric", ProfileType.MODS, "1.21", "fabric", false);
        Path jar = descriptor(p, "fabric.mod.json", "{\"id\":\"embedded\",\"name\":\"Example\",\"version\":\"3\",\"authors\":[\"Fixture\"]}");
        Path config = p.getConfigDirectory().resolve("mods.yml");
        String settings = "mods:\n  Example:\n    modrinth-id: explicit-project\n    curseforge-id: 123\n    force-latest: true\n";
        Files.write(config, settings.getBytes(StandardCharsets.UTF_8));
        String workingDirectory = System.getProperty("user.dir");
        final Path[] inspected = new Path[1];
        LegacyProfileUpdateEngine engine = new LegacyProfileUpdateEngine(new ResourceFinder() {
            @Override public SearchResult findCompatibleProfileMod(InstalledModLoader loader, MinecraftMod mod, String version) {
                assertEquals("fabric", loader.getProfileLoader());
                assertEquals("1.20.1", version);
                assertEquals("explicit-project", mod.modrinthId);
                assertEquals("123", mod.curseforgeId);
                assertFalse(mod.forceLatest, "Global force-latest must not override the profile target");
                inspected[0] = java.nio.file.Paths.get(mod.installationPath);
                assertTrue(Files.exists(inspected[0]));
                return release(SearchResult.Type.UPDATE_AVAILABLE);
            }
        });
        assertEquals(ProfileUpdateService.Action.REPLACE, engine.resolve(p, jar, "1.20.1").getAction());
        assertEquals(settings, new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
        assertEquals(workingDirectory, System.getProperty("user.dir"));
        assertFalse(Files.exists(inspected[0]), "Temporary parser input is removed");
    }

    @Test void quiltDescriptorUsesTheActualQuiltLoaderAndCanInspectDisabledJars() throws Exception {
        Profile p = new ProfileRepository(temporary.resolve("data")).create("quilt", ProfileType.MODS, "1.21", "quilt", false);
        Path jar = descriptor(p, "quilt.mod.json", "{\"quilt_loader\":{\"id\":\"example-quilt\",\"version\":\"3\",\"metadata\":{\"name\":\"Quilt Example\",\"contributors\":{\"Fixture\":\"Author\"}}}}");
        Path disabled = Files.move(jar, jar.resolveSibling("example.jar.disabled"));
        LegacyProfileUpdateEngine engine = new LegacyProfileUpdateEngine(new ResourceFinder() {
            @Override public SearchResult findCompatibleProfileMod(InstalledModLoader loader, MinecraftMod mod, String version) {
                assertTrue(loader.isQuilt); assertFalse(loader.isFabric);
                assertEquals("example-quilt", mod.modrinthId);
                assertEquals("Quilt Example", mod.getName());
                return release(SearchResult.Type.NO_COMPATIBLE_RELEASE);
            }
        });
        assertEquals(ProfileUpdateService.Action.DISABLE, engine.resolve(p, disabled, "1.20.1").getAction());
        assertTrue(Files.exists(disabled));
    }

    @Test void pluginCompatibilityUsesProfileLoaderAndMappingWithoutGlobalPluginConfig() throws Exception {
        Profile p = new ProfileRepository(temporary.resolve("data")).create("paper", ProfileType.PLUGINS, "1.21", "paper", false);
        Path jar = descriptor(p, "plugin.yml", "name: Example\nversion: 3\nauthor: Fixture\n");
        Files.write(p.getConfigDirectory().resolve("plugins.yml"), "plugins:\n  Example:\n    modrinth-id: plugin-project\n".getBytes(StandardCharsets.UTF_8));
        LegacyProfileUpdateEngine engine = new LegacyProfileUpdateEngine(new ResourceFinder() {
            @Override public SearchResult findCompatibleProfilePlugin(List<String> loaders, MinecraftPlugin plugin, String version) {
                assertTrue(loaders.contains("paper"));
                assertEquals("plugin-project", plugin.modrinthId);
                assertEquals("1.20.1", version);
                return release(SearchResult.Type.UPDATE_AVAILABLE);
            }
        });
        assertEquals(ProfileUpdateService.Action.REPLACE, engine.resolve(p, jar, "1.20.1").getAction());
    }

    @Test void missingPluginMappingAndProviderOutageAreErrorsNotDisabledAssets() throws Exception {
        Profile p = new ProfileRepository(temporary.resolve("data")).create("paper", ProfileType.PLUGINS, "1.21", "paper", false);
        Path jar = descriptor(p, "plugin.yml", "name: Example\nversion: 3\nauthor: Fixture\n");
        ResourceFinder finder = new ResourceFinder() {
            @Override public SearchResult findCompatibleProfilePlugin(List<String> loaders, MinecraftPlugin plugin, String version) {
                return release(SearchResult.Type.API_ERROR);
            }
        };
        LegacyProfileUpdateEngine engine = new LegacyProfileUpdateEngine(finder);
        assertEquals(ProfileUpdateService.Action.ERROR, engine.resolve(p, jar, "1.20.1").getAction());
        Files.write(p.getConfigDirectory().resolve("plugins.yml"), "plugins:\n  Example:\n    modrinth-id: example\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(ProfileUpdateService.Action.ERROR, engine.resolve(p, jar, "1.20.1").getAction());
    }

    private static SearchResult release(SearchResult.Type type) {
        return new SearchResult(null, type, "compatible-2", "https://example.invalid/fixture.jar", ".jar", null, null, false);
    }
    private Path descriptor(Profile profile, String name, String contents) throws Exception {
        Path jar = profile.getAssetsDirectory().resolve("example.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry(name)); output.write(contents.getBytes(StandardCharsets.UTF_8)); output.closeEntry();
        }
        return jar;
    }
}
