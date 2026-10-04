package com.osiris.autoplug.client.launcher;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeLauncherTest {
    @TempDir Path temp;
    static JsonObject fixture(String file) throws IOException {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(NativeLauncherTest.class.getResourceAsStream("/native-launcher/" + file)), StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
    LaunchPlan plan(JsonObject metadata, String version, LauncherPlatform platform, LaunchAccount account) {
        Path directory = temp.resolve("profile with spaces"); Path client = temp.resolve("minecraft.jar");
        return new LaunchPlan(metadata, version, directory, temp.resolve("natives"), temp.resolve("assets"), temp.resolve("libraries"), client, Arrays.asList(temp.resolve("library.jar"), client), account, platform, null);
    }
    @ParameterizedTest @ValueSource(strings = {"windows", "linux", "osx"})
    void realModernMojangArgumentsKeepPathsIntactAndDirectConnect(String os) throws Exception {
        LaunchPlan launch = plan(fixture("1.20.4.json"), "1.20.4", new LauncherPlatform(os, "amd64", "10.0"), LaunchAccount.offline("TestPlayer"));
        List<String> command = launch.buildCommand(temp.resolve("java with spaces/bin/java"), "localhost", 25566);
        assertEquals(17, launch.getJavaMajor()); assertEquals(temp.resolve("java with spaces/bin/java").toString(), command.get(0));
        assertEquals("localhost:25566", command.get(command.indexOf("--quickPlayMultiplayer") + 1));
        assertFalse(command.contains("--server")); assertFalse(command.contains("--port"));
        assertEquals(temp.resolve("profile with spaces").toString(), command.get(command.indexOf("--gameDir") + 1));
        assertEquals("TestPlayer", command.get(command.indexOf("--username") + 1));
        assertFalse(command.stream().anyMatch(s -> s.contains("${"))); assertFalse(command.contains("--demo"));
        assertEquals(os.equals("osx"), command.contains("-XstartOnFirstThread"));
        assertEquals(os.equals("windows"), command.stream().anyMatch(s -> s.startsWith("-XX:HeapDumpPath=")));
        assertEquals("net.minecraft.client.main.Main", command.get(command.indexOf("-cp") + 2));
    }
    @Test void realLegacyMetadataUsesJava8AndAuthenticationArguments() throws Exception {
        LaunchPlan launch = plan(fixture("1.12.2.json"), "1.12.2", new LauncherPlatform("linux", "amd64", "6.0"), LaunchAccount.offline("Legacy"));
        List<String> command = launch.buildCommand(temp.resolve("java"), "127.0.0.1", 25565);
        assertEquals(8, launch.getJavaMajor()); assertTrue(command.contains("--server"));
        assertEquals("0", command.get(command.indexOf("--accessToken") + 1)); assertTrue(command.contains("-Djava.library.path=" + temp.resolve("natives")));
    }
    @Test void minecraft1194StillUsesLegacyServerPortAndJava17() throws Exception {
        LaunchPlan launch = plan(fixture("1.19.4.json"), "1.19.4", new LauncherPlatform("linux", "amd64", "6.0"), LaunchAccount.offline("Legacy"));
        List<String> command = launch.buildCommand(temp.resolve("java"), "localhost", 25566);
        assertEquals(17, launch.getJavaMajor()); assertEquals("localhost", command.get(command.indexOf("--server")+1));
        assertEquals("25566", command.get(command.indexOf("--port")+1)); assertFalse(command.contains("--quickPlayMultiplayer"));
    }
    @Test void modernJava21QuickPlayAvoidsRemovedArgumentsAndSupportsIpv6() throws Exception {
        LaunchAccount account = LaunchAccount.microsoft("OwnedPlayer", "123456781234123412341234567890ab", "SECRET-TOKEN", "1234", "client-id");
        LaunchPlan launch = plan(fixture("1.20.5.json"), "1.20.5", new LauncherPlatform("linux", "amd64", "6.0"), account);
        List<String> command = launch.buildCommand(temp.resolve("java"), "::1", 25565);
        assertEquals(21, launch.getJavaMajor()); assertFalse(command.contains("--server")); assertFalse(command.contains("--port"));
        assertEquals("[::1]:25565", command.get(command.indexOf("--quickPlayMultiplayer") + 1));
        assertEquals("SECRET-TOKEN", command.get(command.indexOf("--accessToken") + 1)); assertFalse(account.toString().contains("SECRET"));
        assertEquals(1, command.stream().filter("--quickPlayMultiplayer"::equals).count());
    }
    @Test void publicLoaderMetadataMergesMainClassAndRetainsVanillaAssets() throws Exception {
        JsonObject vanilla = fixture("1.20.4.json");
        for (String loader : Arrays.asList("fabric", "quilt")) {
            JsonObject merged = NativeLauncher.merge(vanilla, fixture(loader + "-1.20.4.json"));
            assertEquals("1.20.4", merged.get("jar").getAsString()); assertEquals(vanilla.get("assetIndex"), merged.get("assetIndex"));
            assertEquals(vanilla.get("downloads"), merged.get("downloads"));
            LaunchPlan launch = plan(merged, "1.20.4", new LauncherPlatform("linux", "amd64", "6"), LaunchAccount.offline("Player"));
            List<String> command = launch.buildCommand(temp.resolve("java"), "example.test", 25565);
            assertTrue(command.contains(loader.equals("fabric") ? "net.fabricmc.loader.impl.launch.knot.KnotClient" : "org.quiltmc.loader.impl.launch.knot.KnotClient"));
            assertEquals(17, launch.getJavaMajor());
        }
    }
    @Test void orderedRulesHandleArchitectureRegexAndFeatures() {
        LauncherPlatform windows = new LauncherPlatform("windows", "x86", "10.0");
        JsonObject rule = JsonParser.parseString("{rules:[{action:'allow'},{action:'disallow',os:{name:'windows'}},{action:'allow',os:{name:'windows',arch:'x86',version:'10\\\\..*'},features:{is_demo_user:true}}]}").getAsJsonObject();
        assertFalse(windows.allows(rule)); assertTrue(windows.allows(rule, Collections.singletonMap("is_demo_user", true)));
        assertTrue(new LauncherPlatform("linux", "amd64", "6").allows(rule));
        assertFalse(windows.allows(JsonParser.parseString("{rules:[]}").getAsJsonObject()));
        assertTrue(windows.allows(new JsonObject()));
    }
    @Test void verifiedCacheRejectsCorruptDownloadAndDoesNotOverwriteGoodBytes() throws Exception {
        byte[] original = "verified library".getBytes(StandardCharsets.UTF_8); AtomicInteger requests = new AtomicInteger();
        LauncherDownloads cache = new LauncherDownloads(temp.resolve("cache"), uri -> { requests.incrementAndGet(); return new ByteArrayInputStream(original); });
        String hash = sha1(original); Path file = cache.download("https://official.test/library", "libraries/test.jar", hash, "SHA-1", original.length);
        assertEquals(1, requests.get()); assertEquals(file, cache.download("https://official.test/library", "libraries/test.jar", hash, "SHA-1", original.length)); assertEquals(1, requests.get());
        LauncherDownloads corrupt = new LauncherDownloads(temp.resolve("cache"), uri -> new ByteArrayInputStream(new byte[]{0,1}));
        assertThrows(IOException.class, () -> corrupt.download("https://official.test/library", "libraries/test.jar", "0000000000000000000000000000000000000000", "SHA-1", 2));
        assertArrayEquals(original, Files.readAllBytes(file));
        try (java.util.stream.Stream<Path> files = Files.walk(temp.resolve("cache"))) { assertFalse(files.anyMatch(p -> p.getFileName().toString().contains(".part-"))); }
    }
    @Test void metadataPathsMavenAndNativeZipCannotEscapeDirectories() throws Exception {
        Path root = temp.resolve("cache"); Files.createDirectories(root);
        for (String bad : Arrays.asList("../outside", "/tmp/outside", "C:/outside", "foo\\bar", "", ".", "../../x")) assertThrows(IOException.class, () -> LauncherDownloads.safeResolve(root, bad));
        assertEquals("org/example/lib/1.2/lib-1.2-natives-windows.jar", NativeLauncher.mavenPath("org.example:lib:1.2:natives-windows"));
        assertEquals("org/example/lib/1.2/lib-1.2.zip", NativeLauncher.mavenPath("org.example:lib:1.2@zip"));
        assertThrows(IOException.class, () -> NativeLauncher.mavenPath("a:../x:1"));
        Path zip = temp.resolve("malicious.zip"); try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) { out.putNextEntry(new ZipEntry("../outside")); out.write(1); out.closeEntry(); }
        assertThrows(IOException.class, () -> LauncherDownloads.extractZip(zip, root, Collections.emptyList())); assertFalse(Files.exists(temp.resolve("outside")));
    }
    @Test void prepareDownloadsClientAssetsLibrariesAndLegacyNativesFromFixtureProtocol() throws Exception {
        Map<String, byte[]> bytes = new HashMap<>(); bytes.put("https://fixture/client.jar", "client".getBytes(StandardCharsets.UTF_8));
        bytes.put("https://fixture/lib.jar", "library".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream archive = new ByteArrayOutputStream(); try (ZipOutputStream zip = new ZipOutputStream(archive)) { zip.putNextEntry(new ZipEntry("test-native.so")); zip.write(new byte[]{1,2,3}); zip.closeEntry(); zip.putNextEntry(new ZipEntry("META-INF/notice")); zip.write(1); zip.closeEntry(); }
        bytes.put("https://fixture/native.jar", archive.toByteArray()); byte[] asset = "sound bytes".getBytes(StandardCharsets.UTF_8); String assetHash = sha1(asset);
        bytes.put("https://resources.download.minecraft.net/" + assetHash.substring(0,2) + "/" + assetHash, asset);
        JsonObject object = new JsonObject(); object.addProperty("hash", assetHash); object.addProperty("size", asset.length);
        JsonObject objects = new JsonObject(); objects.add("sounds/test.ogg", object); JsonObject index = new JsonObject(); index.add("objects", objects); index.addProperty("virtual", true); index.addProperty("map_to_resources", true);
        bytes.put("https://fixture/index.json", index.toString().getBytes(StandardCharsets.UTF_8));
        JsonObject metadata = fixture("1.12.2.json"); JsonObject downloads = new JsonObject(); downloads.add("client", artifact("https://fixture/client.jar", null, bytes)); metadata.add("downloads", downloads);
        JsonObject assetIndex = artifact("https://fixture/index.json", null, bytes); assetIndex.addProperty("id", "fixture"); metadata.add("assetIndex", assetIndex); metadata.remove("logging");
        JsonObject library = new JsonObject(); library.addProperty("name", "fixture:lib:1"); JsonObject libraryDownloads = new JsonObject(); libraryDownloads.add("artifact", artifact("https://fixture/lib.jar", "fixture/lib/1/lib-1.jar", bytes));
        JsonObject classifiers = new JsonObject(); classifiers.add("natives-linux-64", artifact("https://fixture/native.jar", "fixture/lib/1/lib-1-natives.jar", bytes)); libraryDownloads.add("classifiers", classifiers); library.add("downloads", libraryDownloads);
        JsonObject nativeNames = new JsonObject(); nativeNames.addProperty("linux", "natives-linux-${arch}"); library.add("natives", nativeNames); JsonArray libraries = new JsonArray(); libraries.add(library); metadata.add("libraries", libraries);
        AtomicInteger count = new AtomicInteger(); NativeLauncher launcher = new NativeLauncher(temp.resolve("cache"), uri -> { count.incrementAndGet(); byte[] value = bytes.get(uri.toString()); if (value == null) throw new IOException("Unexpected fixture request"); return new ByteArrayInputStream(value); }, new LauncherPlatform("linux", "amd64", "6"), major -> temp.resolve("java"));
        LaunchPlan prepared = launcher.prepareMetadata(metadata, "1.12.2", temp.resolve("game"), LaunchAccount.offline("Fixture"));
        assertEquals(5, count.get()); assertEquals(2, prepared.getClasspath().size()); assertTrue(Files.exists(temp.resolve("game/autoplug/natives/1.12.2-linux-amd64/test-native.so")));
        assertFalse(Files.exists(temp.resolve("game/autoplug/natives/1.12.2-linux-amd64/META-INF/notice"))); assertArrayEquals(asset, Files.readAllBytes(temp.resolve("game/resources/sounds/test.ogg")));
        assertArrayEquals(asset, Files.readAllBytes(temp.resolve("cache/assets/virtual/fixture/sounds/test.ogg")));
        launcher.prepareMetadata(metadata, "1.12.2", temp.resolve("game"), LaunchAccount.offline("Fixture")); assertEquals(5, count.get());
    }
    @Test void forgeProcessorSidesNeoForgeSelectionAndXmlSecurity() throws Exception {
        assertTrue(LoaderInstaller.runsOnClient(JsonParser.parseString("{sides:['client'],jar:'a:b:1'}").getAsJsonObject()));
        assertFalse(LoaderInstaller.runsOnClient(JsonParser.parseString("{sides:['server'],jar:'a:b:1'}").getAsJsonObject()));
        assertEquals("21.1.10", LoaderInstaller.selectNeoForgeVersion("<metadata><versioning><versions><version>21.1.2</version><version>21.1.10</version><version>21.2.1</version></versions></versioning></metadata>", "1.21.1"));
        assertThrows(Exception.class, () -> LoaderInstaller.selectNeoForgeVersion("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><metadata><version>&x;</version></metadata>", "1.21.1"));
    }
    @Test void currentYearBasedMinecraftVersionUsesJava25QuickPlayAndActualNeoForgeNaming() throws Exception {
        JsonObject metadata = fixture("26.3.json"); LaunchPlan launch = plan(metadata,"26.3",new LauncherPlatform("linux","amd64","6"),LaunchAccount.offline("Fixture"));
        List<String> command = launch.buildCommand(temp.resolve("java"),"localhost",25565);
        assertEquals(25,launch.getJavaMajor()); assertTrue(command.contains("--quickPlayMultiplayer")); assertFalse(command.contains("--server"));
        String xml = "<metadata><versioning><versions><version>26.2.0.4-beta</version><version>26.3.0.45-beta</version><version>26.3.0.46-beta</version><version>26.3.1.50-beta</version></versions></versioning></metadata>";
        assertEquals("26.3.0.46-beta",LoaderInstaller.selectNeoForgeVersion(xml,"26.3"));
        assertEquals("26.3.1.50-beta",LoaderInstaller.selectNeoForgeVersion(xml,"26.3.1"));
    }
    @Test void invalidHostPortAndAccountFailBeforeProcessCreation() throws Exception {
        LaunchPlan launch = plan(fixture("1.20.4.json"), "1.20.4", new LauncherPlatform("linux", "amd64", "6"), LaunchAccount.offline("Player"));
        assertThrows(IllegalArgumentException.class, () -> launch.buildCommand(temp.resolve("java"), "-malicious", 25565));
        assertThrows(IllegalArgumentException.class, () -> launch.buildCommand(temp.resolve("java"), "localhost", 0));
        assertThrows(IllegalArgumentException.class, () -> LaunchAccount.offline("invalid name"));
        assertThrows(IOException.class, () -> launch.launch(temp.resolve("missing-java"), "localhost", 25565));
    }
    static String sha1(byte[] bytes) throws Exception { StringBuilder result = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-1").digest(bytes)) result.append(String.format("%02x", b & 255)); return result.toString(); }
    static JsonObject artifact(String url, String path, Map<String, byte[]> bytes) throws Exception { JsonObject result = new JsonObject(); result.addProperty("url", url); result.addProperty("sha1", sha1(bytes.get(url))); result.addProperty("size", bytes.get(url).length); if (path != null) result.addProperty("path", path); return result; }
}
