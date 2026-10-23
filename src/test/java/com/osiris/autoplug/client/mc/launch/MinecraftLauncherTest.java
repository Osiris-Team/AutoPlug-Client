package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftLauncherTest {
    @TempDir Path temporaryDirectory;

    @Test void resolvesModernLibrariesAssetsArgumentsAndDirectConnectWithoutStartingAnything() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        MinecraftLauncher launcher = fixture.launcher("linux", "amd64");
        Path game = temporaryDirectory.resolve("game with spaces");
        try (PreparedLaunch launch = launcher.prepare("1.20.4", game, launcher.offlineSession("FixturePlayer"), "example.invalid", 25570, new Cancellation())) {
            List<String> command = launch.getCommand();
            assertEquals(temporaryDirectory.resolve("jre17/bin/java").toString(), command.get(0));
            assertTrue(command.contains("example.fixture.Main"));
            assertEquals(game.toRealPath().toString(), command.get(command.indexOf("--gameDir") + 1));
            assertEquals("example.invalid", command.get(command.indexOf("--server") + 1));
            assertEquals("25570", command.get(command.indexOf("--port") + 1));
            assertTrue(command.get(command.indexOf("-cp") + 1).contains("library-1.0.jar"));
            assertTrue(Files.isRegularFile(game.resolve("resources/sounds/example.txt")));
            assertTrue(Files.isRegularFile(fixture.cache.resolve("assets/virtual/fixture-assets/sounds/example.txt")));
            assertEquals("PreparedLaunch[command redacted]", launch.toString());
        }
        try (Stream<Path> natives = Files.list(game.resolve(".autoplug"))) { assertEquals(0, natives.count()); }
    }

    @Test void cacheVerifiesExistingBytesAndRepairsCorruption() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        byte[] expected = new byte[]{1, 2, 3};
        Cancellation cancellation = new Cancellation();
        Path file = fixture.cache.obtain("fixture.bin", URI.create("https://fixture.invalid/client.jar"), fixture.hash(expected), 3, cancellation);
        int downloads = fixture.transport.downloads.size();
        fixture.cache.obtain("fixture.bin", URI.create("https://fixture.invalid/client.jar"), fixture.hash(expected), 3, cancellation);
        assertEquals(downloads, fixture.transport.downloads.size());
        Files.write(file, new byte[]{9, 9, 9});
        fixture.cache.obtain("fixture.bin", URI.create("https://fixture.invalid/client.jar"), fixture.hash(expected), 3, cancellation);
        assertArrayEquals(expected, Files.readAllBytes(file));
        assertEquals(downloads + 1, fixture.transport.downloads.size());
    }

    @Test void checksumFailurePublishesNeitherArtifactNorPartialFile() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        assertThrows(IOException.class, () -> fixture.cache.obtain("bad/artifact.jar", URI.create("https://fixture.invalid/client.jar"),
                "0000000000000000000000000000000000000000", 3, new Cancellation()));
        assertFalse(Files.exists(fixture.cache.resolve("bad/artifact.jar")));
        try (Stream<Path> files = Files.list(fixture.cache.resolve("bad"))) { assertEquals(0, files.count()); }
    }

    @Test void cancelledPreparationDoesNotDownloadOrBuildACommand() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        Cancellation cancellation = new Cancellation(); cancellation.cancel();
        assertThrows(IOException.class, () -> fixture.launcher("linux", "amd64").prepare("1.20.4", temporaryDirectory.resolve("game"),
                fixture.launcher("linux", "amd64").offlineSession("Fixture"), null, 0, cancellation));
        assertTrue(fixture.transport.downloads.isEmpty());
    }

    @Test void extractsOnlyTheMatchingNativeArchitectureAndOmitsMetadata() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        for (String classifier : new String[]{"natives-macos", "natives-macos-arm64"}) {
            JsonObject library = new JsonObject(); library.addProperty("name", "example:native:1:" + classifier);
            JsonObject downloads = new JsonObject();
            JsonObject artifact = fixture.artifact("https://fixture.invalid/" + classifier + ".jar",
                    fixture.zip(java.util.Map.of("libfixture.dylib", classifier, "META-INF/fixture.txt", "excluded")));
            artifact.addProperty("path", classifier + ".jar"); downloads.add("artifact", artifact); library.add("downloads", downloads);
            library.add("rules", fixture.json("{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}]}").get("rules"));
            fixture.version.getAsJsonArray("libraries").add(library);
        }
        fixture.publishVersion();
        MinecraftLauncher launcher = fixture.launcher("osx", "aarch64");
        try (PreparedLaunch launch = launcher.prepare("1.20.4", temporaryDirectory.resolve("game"), launcher.offlineSession("Fixture"), null, 0, new Cancellation())) {
            String nativeArgument = launch.getCommand().stream().filter(argument -> argument.startsWith("-Djava.library.path=")).findFirst().orElseThrow(AssertionError::new);
            Path natives = java.nio.file.Paths.get(nativeArgument.substring(nativeArgument.indexOf('=') + 1));
            assertEquals("natives-macos-arm64", new String(Files.readAllBytes(natives.resolve("libfixture.dylib")), StandardCharsets.UTF_8));
            assertFalse(Files.exists(natives.resolve("META-INF/fixture.txt")));
            assertFalse(launch.getCommand().get(launch.getCommand().indexOf("-cp") + 1).contains("natives-macos.jar"));
        }
    }

    @Test void legacyArgumentsExpandAfterTokenizationAndKeepSpacesWithinOneArgument() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        fixture.version.remove("arguments");
        fixture.version.addProperty("minecraftArguments", "--username ${auth_player_name} --gameDir ${game_directory} --quoted \"hello world\"");
        fixture.publishVersion();
        MinecraftLauncher launcher = fixture.launcher("linux", "amd64");
        try (PreparedLaunch launch = launcher.prepare("1.20.4", temporaryDirectory.resolve("space in name"), launcher.offlineSession("Fixture"), null, 0, new Cancellation())) {
            assertTrue(launch.getCommand().contains("hello world"));
            assertEquals(temporaryDirectory.resolve("space in name").toRealPath().toString(), launch.getCommand().get(launch.getCommand().indexOf("--gameDir") + 1));
            assertTrue(launch.getCommand().contains("-cp"));
        }
    }

    @Test void orderedOsAndFeatureRulesUseLastMatchingAction() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        LaunchPlatform linux = new LaunchPlatform("linux", "amd64", "6.1", Collections.emptyMap());
        assertTrue(linux.allows(fixture.json("{\"rules\":[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"windows\"}}]}")));
        assertFalse(linux.allows(fixture.json("{\"rules\":[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"linux\"}}]}")));
        assertFalse(linux.allows(fixture.json("{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}]}")));
    }

    @Test void exactRuntimeMajorIsRequiredRatherThanAssumingANewerJreWorks() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        assertEquals(temporaryDirectory.resolve("jre17/bin/java"), fixture.runtimes.select(17, "amd64"));
        assertThrows(IOException.class, () -> fixture.runtimes.select(8));
        assertThrows(IOException.class, () -> fixture.runtimes.select(21));
    }

    @Test void nativeExtractionFailureCleansTheUniqueNativeDirectory() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        JsonObject library = new JsonObject(); library.addProperty("name", "example:native:1:natives-linux");
        JsonObject artifact = fixture.artifact("https://fixture.invalid/native.jar", fixture.zip("../bad.so", "bad"));
        artifact.addProperty("path", "native.jar"); JsonObject downloads = new JsonObject(); downloads.add("artifact", artifact); library.add("downloads", downloads);
        fixture.version.getAsJsonArray("libraries").add(library); fixture.publishVersion();
        MinecraftLauncher launcher = fixture.launcher("linux", "amd64");
        Path game = temporaryDirectory.resolve("game");
        assertThrows(IOException.class, () -> launcher.prepare("1.20.4", game, launcher.offlineSession("Fixture"), null, 0, new Cancellation()));
        try (Stream<Path> natives = Files.list(game.resolve(".autoplug"))) { assertEquals(0, natives.count()); }
    }

    @Test void offlineSessionIsDeterministicAndNeverClaimsMicrosoftAuthentication() throws Exception {
        MinecraftLauncher launcher = new LaunchFixtures(temporaryDirectory).launcher("linux", "amd64");
        MinecraftSession first = launcher.offlineSession("Fixture");
        assertEquals(first.getUuid(), launcher.offlineSession("Fixture").getUuid());
        assertEquals("legacy", first.getUserType()); assertEquals("0", first.getAccessToken());
        assertThrows(IllegalArgumentException.class, () -> launcher.offlineSession("bad name"));
        assertFalse(new MinecraftSession("Fixture", first.getUuid(), "secret-token", "msa").toString().contains("secret-token"));
    }

    @Test void cancellationDuringAStreamingDownloadNeverPublishesItsPartialBytes() throws Exception {
        Cancellation cancellation = new Cancellation();
        LaunchTransport transport = new LaunchTransport() {
            @Override public JsonObject json(String method, URI uri, java.util.Map<String, String> headers, byte[] body, Cancellation scope) throws IOException { throw new IOException("Not needed"); }
            @Override public void download(URI uri, Path target, Cancellation scope) throws IOException {
                Files.write(target, new byte[]{1}); scope.cancel();
            }
        };
        ArtifactCache cache = new ArtifactCache(temporaryDirectory.resolve("cancel-cache"), transport);
        assertThrows(IOException.class, () -> cache.obtain("client.jar", URI.create("https://fixture.invalid/client"),
                "0000000000000000000000000000000000000000", 1, cancellation));
        try (Stream<Path> files = Files.list(cache.getRoot())) { assertEquals(0, files.count()); }
    }

    @Test void javaEightReleaseMetadataAndWindowsExecutableNamesAreRecognized() throws Exception {
        Path home = temporaryDirectory.resolve("windows-jre8"); Files.createDirectories(home.resolve("bin"));
        Files.write(home.resolve("bin/java.exe"), new byte[0]);
        Files.write(home.resolve("release"), "JAVA_VERSION=\"1.8.0_402\"\nOS_ARCH=\"amd64\"".getBytes(StandardCharsets.UTF_8));
        assertEquals(home.resolve("bin/java.exe"), new JavaRuntimeSelector(Collections.singletonList(home), true).select(8, "x86_64"));
    }

    @Test void rejectsAnIntelJreForAnArmLaunchAndSelectsAMatchingRuntimeWhenAvailable() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        JavaRuntimeSelector intelOnly = new JavaRuntimeSelector(Collections.singletonList(temporaryDirectory.resolve("jre17")), false);
        assertThrows(IOException.class, () -> intelOnly.select(17, "aarch64"));
        assertEquals(temporaryDirectory.resolve("jre17-arm64/bin/java"), fixture.runtimes.select(17, "arm64"));
    }

    @Test void legacyIntelOnlyMacNativesFailClearlyForAnArmJvmBeforeNativeDownload() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        JsonObject library = new JsonObject(); library.addProperty("name", "example:legacy-native:1");
        library.add("natives", fixture.json("{\"osx\":\"natives-osx\"}"));
        JsonObject nativeArtifact = fixture.artifact("https://fixture.invalid/legacy-native.jar", fixture.zip("legacy.dylib", "Intel only"));
        nativeArtifact.addProperty("path", "legacy-native.jar");
        JsonObject classifiers = new JsonObject(); classifiers.add("natives-osx", nativeArtifact);
        JsonObject downloads = new JsonObject(); downloads.add("classifiers", classifiers); library.add("downloads", downloads);
        fixture.version.getAsJsonArray("libraries").add(library); fixture.publishVersion();
        MinecraftLauncher launcher = fixture.launcher("osx", "aarch64");
        IOException error = assertThrows(IOException.class, () -> launcher.prepare("1.20.4", temporaryDirectory.resolve("game"), launcher.offlineSession("Fixture"), null, 0, new Cancellation()));
        assertTrue(error.getMessage().contains("native libraries"));
        assertFalse(fixture.transport.downloads.contains("https://fixture.invalid/legacy-native.jar"));
    }

    @Test void discoversAutoPlugManagedArchiveFoldersAndMacContentsHomeWithoutUnboundedRecursion() throws Exception {
        Path manager = temporaryDirectory.resolve("autoplug/system/jre");
        Path java8 = manager.resolve("jdk8/bin/java"); Files.createDirectories(java8.getParent()); Files.write(java8, new byte[0]);
        Files.write(manager.resolve("jdk8/release"), "JAVA_VERSION=\"1.8.0_402\"\nOS_ARCH=\"x86_64\"".getBytes(StandardCharsets.UTF_8));
        Path macHome = manager.resolve("jdk17.jdk/Contents/Home"); Files.createDirectories(macHome.resolve("bin")); Files.write(macHome.resolve("bin/java"), new byte[0]);
        Files.write(macHome.resolve("release"), "JAVA_VERSION=\"17.0.12\"\nOS_ARCH=\"aarch64\"".getBytes(StandardCharsets.UTF_8));
        Path tooDeep = manager.resolve("unrelated/nested/jdk21"); Files.createDirectories(tooDeep.resolve("bin")); Files.write(tooDeep.resolve("bin/java"), new byte[0]);
        Files.write(tooDeep.resolve("release"), "JAVA_VERSION=\"21.0.1\"\nOS_ARCH=\"aarch64\"".getBytes(StandardCharsets.UTF_8));
        JavaRuntimeSelector selector = new JavaRuntimeSelector(Collections.singletonList(manager), false);
        assertEquals(java8, selector.select(8, "amd64"));
        assertEquals(macHome.resolve("bin/java"), selector.select(17, "arm64"));
        assertThrows(IOException.class, () -> selector.select(21, "arm64"));
    }
}
