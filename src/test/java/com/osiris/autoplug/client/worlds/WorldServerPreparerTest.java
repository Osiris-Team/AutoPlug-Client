/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.worlds;

import com.google.gson.*;
import com.osiris.autoplug.client.launcher.*;
import com.osiris.autoplug.client.profiles.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldServerPreparerTest {
    @TempDir Path temporary;
    static String resource(String name) throws IOException {
        try (InputStream input = WorldServerPreparerTest.class.getResourceAsStream("/" + name)) { assertNotNull(input, name); return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
    static Path fixtureJar(Path directory, String mainClass, String gameVersion) throws IOException {
        Path jar = directory.resolve("fixture-" + UUID.randomUUID() + ".jar"); Manifest manifest = new Manifest(); manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0"); manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry("version.json")); output.write(("{\"id\":\"" + gameVersion + "\"}").getBytes(StandardCharsets.UTF_8)); output.closeEntry();
            String classFile = WorldFixtureProcesses.class.getName().replace('.', '/') + ".class";
            try (InputStream input = WorldFixtureProcesses.class.getResourceAsStream("/" + classFile)) { assertNotNull(input); output.putNextEntry(new JarEntry(classFile)); output.write(input.readAllBytes()); output.closeEntry(); }
        }
        return jar;
    }
    static WorldServerPreparer offlinePreparer(Path cache, Path java, List<Integer> javaRequests, boolean offline) throws IOException {
        byte[] game = resource("native-launcher/1.20.4.json").getBytes(StandardCharsets.UTF_8);
        Path gameFile = Files.createTempFile(cache.getParent(), "game-metadata-", ".json"); Files.write(gameFile, game);
        JsonObject entry = new JsonObject(); entry.addProperty("id", "1.20.4"); entry.addProperty("url", "https://fixture.test/1.20.4.json"); entry.addProperty("sha1", LauncherDownloads.hash(gameFile, "SHA-1"));
        JsonArray versions = new JsonArray(); versions.add(entry); JsonObject manifest = new JsonObject(); manifest.add("versions", versions);
        return new WorldServerPreparer(cache, uri -> {
            if (uri.toString().equals(NativeLauncher.VERSION_MANIFEST)) return new ByteArrayInputStream(manifest.toString().getBytes(StandardCharsets.UTF_8));
            if (uri.toString().equals("https://fixture.test/1.20.4.json")) return new ByteArrayInputStream(game);
            throw new IOException("Unexpected fixture network request: " + uri);
        }, major -> { javaRequests.add(major); return java; }, () -> offline);
    }
    @Test void parsesCapturedOfficialStablePaperPurpurAndFabricMetadataAndPublishedHashes() throws Exception {
        WorldServerPreparer.Artifact paper = WorldServerPreparer.paper(JsonParser.parseString(resource("virtual-worlds/paper-1.20.4-builds.json")).getAsJsonArray(), "1.20.4");
        assertEquals("paper/1.20.4/499.jar", paper.cacheKey); assertEquals("SHA-256", paper.algorithm); assertEquals(42781488, paper.size);
        assertEquals("cabed3ae77cf55deba7c7d8722bc9cfd5e991201c211665f9265616d9fe5c77b", paper.hash); assertTrue(paper.url.startsWith("https://fill-data.papermc.io/"));
        WorldServerPreparer.Artifact purpur = WorldServerPreparer.purpur(JsonParser.parseString(resource("virtual-worlds/purpur-1.20.4-latest.json")).getAsJsonObject(), "1.20.4");
        assertEquals("https://api.purpurmc.org/v2/purpur/1.20.4/2176/download", purpur.url); assertEquals("c753922385b06885306c0b36a689c97f", purpur.hash); assertEquals("MD5", purpur.algorithm);
        WorldServerPreparer.Artifact fabric = WorldServerPreparer.fabric(JsonParser.parseString(resource("virtual-worlds/fabric-1.20.4-loaders.json")).getAsJsonArray(), JsonParser.parseString(resource("virtual-worlds/fabric-installers.json")).getAsJsonArray(), "1.20.4");
        assertEquals("https://meta.fabricmc.net/v2/versions/loader/1.20.4/0.19.5/1.1.2/server/jar", fabric.url); assertNull(fabric.hash);
        assertThrows(IOException.class, () -> WorldServerPreparer.paper(new JsonArray(), "1.20.4"));
        assertThrows(IOException.class, () -> WorldServerPreparer.purpur(JsonParser.parseString(resource("virtual-worlds/purpur-1.20.4-latest.json")).getAsJsonObject(), "1.21.1"));
    }
    @Test void rejectsWrongVersionNonExecutableAndSpigotBuildToolsJars() throws Exception {
        assertThrows(IOException.class, () -> WorldServerPreparer.validateSuppliedJar(fixtureJar(temporary, "example.Main", "1.21.1"), "1.20.4"));
        assertThrows(IOException.class, () -> WorldServerPreparer.validateSuppliedJar(fixtureJar(temporary, "org.spigotmc.builder.Builder", "1.20.4"), "1.20.4"));
        Path arbitrary = temporary.resolve("arbitrary.jar"); Files.write(arbitrary, new byte[]{1, 2, 3}); assertThrows(IOException.class, () -> WorldServerPreparer.validateSuppliedJar(arbitrary, "1.20.4"));
    }
    @Test void writesIndependentPropertiesOnlyAfterExplicitEulaAndRequestsMojangJavaVersion() throws Exception {
        ProfileStore profiles = new ProfileStore(temporary.resolve("autoplug")); Profile plugins = profiles.create("paper", Profile.Type.PLUGINS, "1.20.4", "PAPER", false); WorldStore worlds = new WorldStore(profiles.getRoot(), profiles);
        WorldDefinition world = worlds.create("world", plugins.id); Path jar = fixtureJar(temporary, WorldFixtureProcesses.class.getName(), "1.20.4"); world = worlds.setServerExecutable(world.id, jar);
        List<Integer> javaRequests = new ArrayList<>(); WorldServerPreparer preparer = offlinePreparer(temporary.resolve("cache"), Paths.get(System.getProperty("java.home"), "bin", LauncherPlatform.current().os.equals("windows") ? "java.exe" : "java"), javaRequests, true);
        WorldDefinition rejected = world; assertThrows(IOException.class, () -> preparer.prepare(rejected, 25577, false)); assertTrue(javaRequests.isEmpty()); assertFalse(Files.exists(world.directoryPath.resolve("eula.txt")));
        world = worlds.acceptEula(world.id, true); ProcessBuilder command = preparer.prepare(world, 25577, false);
        assertEquals(Collections.singletonList(17), javaRequests); assertEquals(world.directoryPath.toFile(), command.directory()); assertEquals("nogui", command.command().get(command.command().size() - 1));
        Properties p = new Properties(); try (InputStream input = Files.newInputStream(world.directoryPath.resolve("server.properties"))) { p.load(input); }
        assertEquals("127.0.0.1", p.getProperty("server-ip")); assertEquals("25577", p.getProperty("server-port")); assertEquals("false", p.getProperty("online-mode")); assertEquals("false", p.getProperty("enable-rcon"));
        WorldDefinition shared = world; assertThrows(IOException.class, () -> preparer.prepare(shared, 25577, true));
        assertFalse(Files.exists(plugins.directoryPath.resolve("server.properties"))); assertFalse(Files.exists(profiles.getRoot().resolve("server.properties")));
    }
}
