package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

class UtilsMinecraftTest {

    private UtilsMinecraft utilsMinecraft;
    private File testServerJarsDir;
    private File testPluginsDir;

    @BeforeEach
    void setUp() {
        utilsMinecraft = new UtilsMinecraft();
        // Assuming the test resources are copied to the build/resources/test directory
        // or accessible via getClassLoader().getResource()
        // For direct file access from src/test/resources, this path might need adjustment
        // depending on the execution context (IDE vs build tool)
        String resourcesPath = "src/test/resources"; // Path relative to project root
        testServerJarsDir = new File(resourcesPath, "testServerJars");
        testPluginsDir = new File(resourcesPath, "testPluginsDir");

        // Ensure these directories exist, if not, the tests for file operations will fail correctly.
        // The resource creation steps should have made these.
    }

    // Tests for getInstalledVersion
    @Test
    void testDetectFoliaVersion() {
        File foliaServerJar = new File(testServerJarsDir, "folia-server.jar");
        assertTrue(foliaServerJar.exists(), "Folia server JAR should exist at " + foliaServerJar.getAbsolutePath());
        assertEquals("Folia", utilsMinecraft.getInstalledVersion(foliaServerJar));
    }

    @Test
    void testDetectPaperVersion() {
        File paperServerJar = new File(testServerJarsDir, "paper-server.jar");
        assertTrue(paperServerJar.exists(), "Paper server JAR should exist.");
        assertEquals("1.19.4", utilsMinecraft.getInstalledVersion(paperServerJar));
    }

    @Test
    void testDetectVanillaVersion() {
        File vanillaServerJar = new File(testServerJarsDir, "vanilla-server.jar");
        assertTrue(vanillaServerJar.exists(), "Vanilla server JAR should exist.");
        assertEquals("1.18.2", utilsMinecraft.getInstalledVersion(vanillaServerJar));
    }

    @Test
    void testDetectNoVersion() {
        File noVersionServerJar = new File(testServerJarsDir, "noversion-server.jar");
        assertTrue(noVersionServerJar.exists(), "No-version server JAR should exist.");
        assertNull(utilsMinecraft.getInstalledVersion(noVersionServerJar));
    }

    @Test
    void testNonJarFileReturnsNull(@TempDir Path tempDir) throws IOException {
        File notAJarFile = tempDir.resolve("server.txt").toFile();
        assertTrue(notAJarFile.createNewFile(), "Failed to create dummy server.txt");
        assertNull(utilsMinecraft.getInstalledVersion(notAJarFile));
    }

    @Test
    void testMissingFileReturnsNull() {
        File nonExistentFile = new File(testServerJarsDir, "nonexistent.jar");
        assertFalse(nonExistentFile.exists(), "File should not exist for this test.");
        assertNull(utilsMinecraft.getInstalledVersion(nonExistentFile));
    }

    @Test
    void testNullFileReturnsNull() {
        assertNull(utilsMinecraft.getInstalledVersion(null));
    }

    // Tests for getPlugins and folia-supported flag
    @Test
    void testGetPluginsFoliaSupported() throws IOException {
        assertTrue(testPluginsDir.exists(), "Test plugins directory should exist.");
        assertTrue(testPluginsDir.isDirectory(), "Test plugins directory should be a directory.");

        List<MinecraftPlugin> plugins = utilsMinecraft.getPlugins(testPluginsDir);
        assertNotNull(plugins, "Plugin list should not be null.");
        assertEquals(6, plugins.size(), "Expected 6 plugins to be parsed.");

        Optional<MinecraftPlugin> pluginA = plugins.stream().filter(p -> "PluginA".equals(p.getName())).findFirst();
        assertTrue(pluginA.isPresent(), "PluginA should be found.");
        assertTrue(pluginA.get().isFoliaSupported(), "PluginA should be Folia supported.");

        Optional<MinecraftPlugin> pluginB = plugins.stream().filter(p -> "PluginB".equals(p.getName())).findFirst();
        assertTrue(pluginB.isPresent(), "PluginB should be found.");
        assertFalse(pluginB.get().isFoliaSupported(), "PluginB should not be Folia supported.");

        Optional<MinecraftPlugin> pluginC = plugins.stream().filter(p -> "PluginC".equals(p.getName())).findFirst();
        assertTrue(pluginC.isPresent(), "PluginC should be found.");
        assertFalse(pluginC.get().isFoliaSupported(), "PluginC should default to not Folia supported (key missing).");

        Optional<MinecraftPlugin> pluginD = plugins.stream().filter(p -> "PluginD".equals(p.getName())).findFirst();
        assertTrue(pluginD.isPresent(), "PluginD should be found.");
        assertFalse(pluginD.get().isFoliaSupported(), "PluginD should default to not Folia supported (invalid value).");

        Optional<MinecraftPlugin> bungeePlugin = plugins.stream().filter(p -> "BungeePlugin".equals(p.getName())).findFirst();
        assertTrue(bungeePlugin.isPresent(), "BungeePlugin should be found.");
        assertTrue(bungeePlugin.get().isFoliaSupported(), "BungeePlugin should be Folia supported (from bungee.yml).");

        Optional<MinecraftPlugin> velocityPlugin = plugins.stream().filter(p -> "VelocityPlugin".equals(p.getName())).findFirst();
        assertTrue(velocityPlugin.isPresent(), "VelocityPlugin should be found.");
        assertFalse(velocityPlugin.get().isFoliaSupported(), "VelocityPlugin should not be Folia supported.");
    }
}
