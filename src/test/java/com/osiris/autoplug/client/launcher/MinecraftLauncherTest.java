package com.osiris.autoplug.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftLauncherTest {
    @TempDir Path temporary;

    // Unmodified publisher metadata retrieved 2026-10-04 from Mojang's version_manifest_v2,
    // meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.10/profile/json and
    // meta.quiltmc.org/v3/versions/loader/1.20.1/0.26.4/profile/json. Tests never need the network.
    static JsonObject fixture(String name) {
        InputStream stream = MinecraftLauncherTest.class.getResourceAsStream("/launcher/" + name + ".json");
        assertNotNull(stream, name);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) { throw new AssertionError(e); }
    }

    @Test void manifestRuntimeRequirementsIncludeJava16() {
        assertEquals(8, MinecraftLauncher.requiredJava(fixture("1.16.5")));
        assertEquals(16, MinecraftLauncher.requiredJava(fixture("1.17.1")));
        assertEquals(17, MinecraftLauncher.requiredJava(fixture("1.20.1")));
        assertEquals(21, MinecraftLauncher.requiredJava(fixture("1.21.1")));
    }

    @Test void officialLoaderProfilesPreserveVanillaArgumentsAndOverrideMainClass() throws Exception {
        for (String loader : Arrays.asList("fabric-1.21.1", "quilt-1.20.1")) {
            String version = loader.contains("1.21.1") ? "1.21.1" : "1.20.1";
            JsonObject merged = MinecraftLauncher.merge(fixture(version), fixture(loader));
            assertEquals(fixture(loader).get("mainClass"), merged.get("mainClass"));
            List<String> args = args(merged, new LaunchRequest(temporary.resolve("game with spaces"), version, "VANILLA", null, MinecraftAccount.offline("Player"), "localhost", 25570));
            assertTrue(args.contains("--username"));
            assertEquals(1, Collections.frequency(args, "-cp"));
            assertTrue(args.contains(temporary.resolve("game with spaces").toString()));
            assertTrue(args.contains("localhost:25570"));
            assertFalse(args.stream().anyMatch(a -> a.contains("${")));
        }
    }

    @Test void directJoinUsesManifestQuickPlayAndLegacyServerFlags() throws Exception {
        LaunchRequest old = new LaunchRequest(temporary, "1.16.5", "VANILLA", null, MinecraftAccount.offline("Player"), "example.test", 25570);
        List<String> legacy = args(fixture("1.16.5"), old);
        assertEquals("example.test", legacy.get(legacy.indexOf("--server") + 1));
        assertEquals("25570", legacy.get(legacy.indexOf("--port") + 1));
        LaunchRequest modern = new LaunchRequest(temporary, "1.21.1", "VANILLA", null, MinecraftAccount.offline("Player"), "::1", 25570);
        List<String> quick = args(fixture("1.21.1"), modern);
        assertEquals("[::1]:25570", quick.get(quick.indexOf("--quickPlayMultiplayer") + 1));
        assertFalse(quick.contains("--demo"));
        assertFalse(quick.contains("--quickPlayRealms"));
    }

    @Test void conditionalArgumentsUseLastMatchingRule() {
        JsonObject rules = JsonParser.parseString("{\"rules\":[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"" + MinecraftLauncher.osName() + "\"}}]}").getAsJsonObject();
        assertFalse(MinecraftLauncher.allowed(rules, Collections.emptyMap()));
        JsonObject feature = JsonParser.parseString("{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}]}").getAsJsonObject();
        assertFalse(MinecraftLauncher.allowed(feature, Collections.emptyMap()));
        assertTrue(MinecraftLauncher.allowed(feature, Collections.singletonMap("is_demo_user", true)));
    }

    @Test void localQuickPlayUsesSaveFolderAndRetainsOriginalGameDirectory() throws Exception {
        Path original = temporary.resolve(".minecraft");
        LaunchRequest request = new LaunchRequest(original, "1.21.1", "VANILLA", null,
                MinecraftAccount.offline("Player"), null, 0, "My world folder");
        List<String> args = args(fixture("1.21.1"), request);
        assertEquals("My world folder", args.get(args.indexOf("--quickPlaySingleplayer") + 1));
        assertEquals(original.toString(), args.get(args.indexOf("--gameDir") + 1));
        assertEquals(original.resolve("logs/quick-play.json").toString(), args.get(args.indexOf("--quickPlayPath") + 1));
        assertFalse(args.contains("--quickPlayMultiplayer"));
        assertFalse(args.contains("--quickPlayRealms"));
        assertFalse(args.contains("--server"));
    }

    @Test void legacyLocalWorldKeepsExactVersionWithoutInventingDirectLaunchFlags() throws Exception {
        LaunchRequest request = new LaunchRequest(temporary, "1.16.5", "VANILLA", null,
                MinecraftAccount.offline("Player"), null, 0, "Legacy world");
        List<String> args = args(fixture("1.16.5"), request);
        assertEquals("1.16.5", args.get(args.indexOf("--version") + 1));
        assertFalse(args.contains("--quickPlaySingleplayer"));
        assertFalse(args.contains("--server"));
        assertFalse(args.stream().anyMatch(value -> value.contains("${")));
    }

    @Test void localWorldRequestsRejectTraversalAndAmbiguousTargets() {
        for (String invalid : Arrays.asList("", ".", "..", "../world", "nested/world", "nested\\world", "C:world", "world\n"))
            assertThrows(IllegalArgumentException.class, () -> new LaunchRequest(temporary, "1.21.1", "VANILLA", null,
                    MinecraftAccount.offline("Player"), null, 0, invalid));
        assertThrows(IllegalArgumentException.class, () -> new LaunchRequest(temporary, "1.21.1", "VANILLA", null,
                MinecraftAccount.offline("Player"), "localhost", 25565, "World"));
    }

    @Test void mavenCoordinatesAndCachePathsRejectTraversal() throws Exception {
        assertEquals("net/fabricmc/intermediary/1.21.1/intermediary-1.21.1.jar", MinecraftLauncher.mavenPath("net.fabricmc:intermediary:1.21.1"));
        assertEquals("g/a/1/a-1-natives.jar", MinecraftLauncher.mavenPath("g:a:1:natives"));
        assertThrows(IOException.class, () -> MinecraftLauncher.mavenPath("g:a:../../outside"));
        assertThrows(IOException.class, () -> LauncherFiles.child(temporary, "../outside"));
        Path zip = temporary.resolve("native.jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) { out.putNextEntry(new ZipEntry("../escape.dll")); out.write(1); }
        assertThrows(IOException.class, () -> MinecraftLauncher.extractNatives(zip, temporary.resolve("natives"), Collections.emptyList()));
        assertFalse(Files.exists(temporary.resolve("escape.dll")));
    }

    @Test void unknownPlaceholdersFailInsteadOfLaunchingBrokenCommand() {
        assertThrows(IOException.class, () -> MinecraftLauncher.substitute("${unknown}", Collections.emptyMap()));
    }

    @Test void unchangedNativesAreNotOverwrittenWhileAnotherProcessMayHaveThemLoaded() throws Exception {
        Path jar = temporary.resolve("native.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) { zip.putNextEntry(new ZipEntry("native.dll")); zip.write(new byte[]{1, 2, 3}); }
        Path directory = temporary.resolve("profile-natives");
        MinecraftLauncher.extractNatives(jar, directory, Collections.emptyList());
        Path dll = directory.resolve("native.dll");
        java.nio.file.attribute.FileTime before = java.nio.file.attribute.FileTime.fromMillis(123456000);
        Files.setLastModifiedTime(dll, before);
        MinecraftLauncher.extractNatives(jar, directory, Collections.emptyList());
        assertEquals(before, Files.getLastModifiedTime(dll));
        Files.write(dll, new byte[]{9, 9, 9});
        MinecraftLauncher.extractNatives(jar, directory, Collections.emptyList());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(dll));
    }

    @Test void inheritedClientJarMatchesNeoForgeModuleIgnoreList() throws Exception {
        // This fixture was produced by the official NeoForge 21.1.172 installer. Keeping the
        // vanilla jar's original filename causes duplicate packages in its module layers.
        JsonObject metadata = MinecraftLauncher.merge(fixture("1.21.1"), fixture("neoforge-1.21.1"));
        Path vanilla = temporary.resolve("1.21.1.jar"); Files.write(vanilla, new byte[]{1, 2, 3});
        Path effective = MinecraftLauncher.clientJarForProfile(vanilla, metadata, temporary.resolve("cache"));
        assertEquals("neoforge-21.1.172.jar", effective.getFileName().toString());
        assertArrayEquals(Files.readAllBytes(vanilla), Files.readAllBytes(effective));
        LaunchRequest request = new LaunchRequest(temporary.resolve("profile"), "1.21.1", "NEOFORGE", "21.1.172", MinecraftAccount.offline("Player"), null, 0);
        List<String> args = MinecraftLauncher.buildArguments(metadata, request, Collections.singletonList(effective), temporary.resolve("natives"), temporary.resolve("assets"), "17", null);
        assertTrue(args.contains("-DignoreList=client-extra," + effective.getFileName()));
    }

    @Test void offlineLaunchPreparesDownloadsVerifiesAndReusesCacheWithoutNetwork() throws Exception {
        byte[] jar = clientJar();
        byte[] assets = "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        JsonObject metadata = JsonParser.parseString("{\"id\":\"fixture\",\"mainClass\":\"com.osiris.autoplug.client.launcher.OfflineClientFixture\",\"libraries\":[],\"javaVersion\":{\"majorVersion\":21},\"arguments\":{\"jvm\":[\"-cp\",\"${classpath}\"],\"game\":[\"--username\",\"${auth_player_name}\",\"--gameDir\",\"${game_directory}\",\"--accessToken\",\"${auth_access_token}\"]}}").getAsJsonObject();
        JsonObject downloads = new JsonObject(); downloads.add("client", descriptor(base + "/client.jar", jar)); metadata.add("downloads", downloads);
        JsonObject assetIndex = descriptor(base + "/assets.json", assets); assetIndex.addProperty("id", "fixture"); metadata.add("assetIndex", assetIndex);
        byte[] manifestBody = metadata.toString().getBytes(StandardCharsets.UTF_8);
        JsonObject entry = descriptor(base + "/fixture.json", manifestBody); entry.addProperty("id", "fixture");
        JsonObject manifest = new JsonObject(); JsonArray versions = new JsonArray(); versions.add(entry); manifest.add("versions", versions);
        serve(server, "/manifest", manifest.toString().getBytes(StandardCharsets.UTF_8));
        serve(server, "/fixture.json", manifestBody); serve(server, "/client.jar", jar); serve(server, "/assets.json", assets);
        server.start();
        Path java = Paths.get(System.getProperty("java.home"), "bin", JavaRuntimeManager.executableName());
        JavaRuntimeManager runtime = new JavaRuntimeManager(temporary.resolve("runtimes")) {
            @Override public Path resolve(int major, java.util.function.Consumer<String> log) { return java; }
        };
        MinecraftLauncher launcher = new MinecraftLauncher(temporary.resolve("cache"), runtime, base + "/manifest");
        LaunchRequest request = new LaunchRequest(temporary.resolve("isolated profile"), "fixture", "VANILLA", null, MinecraftAccount.offline("OfflinePlayer"), null, 0);
        PreparedLaunch prepared;
        try { prepared = launcher.prepare(request, ignored -> { }); }
        finally { server.stop(0); }
        Process process = launcher.launch(prepared);
        assertTrue(process.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        assertEquals(request.gameDir.toString(), new String(Files.readAllBytes(request.gameDir.resolve("fixture-working-directory.txt")), StandardCharsets.UTF_8));
        assertEquals(prepared.command(), launcher.prepare(request, ignored -> { }).command());
        assertFalse(Files.exists(temporary.resolve("fixture-working-directory.txt")));
    }

    private List<String> args(JsonObject metadata, LaunchRequest request) throws Exception {
        return MinecraftLauncher.buildArguments(metadata, request, Collections.singletonList(temporary.resolve("client with spaces.jar")),
                temporary.resolve("natives"), temporary.resolve("assets"), "17", null);
    }
    private static JsonObject descriptor(String url, byte[] content) {
        JsonObject result = new JsonObject(); result.addProperty("url", url); result.addProperty("size", content.length);
        result.addProperty("sha1", com.osiris.autoplug.client.utils.UtilsCrypto.calculateSHA1Hash(content)); return result;
    }
    private static void serve(HttpServer server, String path, byte[] bytes) {
        server.createContext(path, exchange -> { exchange.sendResponseHeaders(200, bytes.length); try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(bytes); } });
    }
    private static byte[] clientJar() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String name = "com/osiris/autoplug/client/launcher/OfflineClientFixture.class";
        try (JarOutputStream jar = new JarOutputStream(bytes); InputStream in = MinecraftLauncherTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(in); jar.putNextEntry(new JarEntry(name)); byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) jar.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
}
