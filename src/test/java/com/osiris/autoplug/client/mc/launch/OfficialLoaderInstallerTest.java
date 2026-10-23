package com.osiris.autoplug.client.mc.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OfficialLoaderInstallerTest {
    @TempDir Path temporaryDirectory;

    @Test void resolvesForgeRecommendationVerifiesInstallerAndImportsItsGeneratedProfileAndLibraries() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        fixture.transport.responses.put("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json",
                fixture.json("{\"promos\":{\"1.20.4-recommended\":\"49.0.1\",\"1.20.4-latest\":\"49.0.2\"}}"));
        installerFixture(fixture, "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.4-49.0.1/forge-1.20.4-49.0.1-installer.jar");
        List<String> actualCommand = new ArrayList<>();
        OfficialLoaderInstaller installer = new OfficialLoaderInstaller(fixture.cache, fixture.metadata, fixture.transport, fixture.runtimes,
                (command, directory, cancellation) -> {
                    actualCommand.addAll(command);
                    installedProfile(fixture, directory, "1.20.4-forge-49.0.1");
                    Path generated = directory.resolve("libraries/fixture/generated/1/generated-1.jar");
                    Files.createDirectories(generated.getParent()); Files.write(generated, new byte[]{7, 8});
                    return 0;
                });
        String id = installer.install("FORGE", "1.20.4", null, temporaryDirectory.resolve("game"), new Cancellation());
        assertEquals("1.20.4-forge-49.0.1", id);
        assertEquals("--installClient", actualCommand.get(3));
        assertEquals(temporaryDirectory.resolve("game").toRealPath().toString(), actualCommand.get(4));
        assertEquals("fixture.ForgeMain", fixture.metadata.version(id, new Cancellation()).get("mainClass").getAsString());
        assertArrayEquals(new byte[]{7, 8}, Files.readAllBytes(fixture.cache.installedLibrary("libraries/fixture/generated/1/generated-1.jar")));
    }

    @Test void selectsNeoForgeStableVersionsNumericallyAndUsesTheOfficialVendorPath() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        fixture.transport.bytes.put("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
                "<metadata><versioning><versions><version>20.4.9</version><version>20.4.10</version><version>21.1.1</version><version>20.4.11-beta</version></versions></versioning></metadata>".getBytes(StandardCharsets.UTF_8));
        installerFixture(fixture, "https://maven.neoforged.net/releases/net/neoforged/neoforge/20.4.10/neoforge-20.4.10-installer.jar");
        OfficialLoaderInstaller installer = new OfficialLoaderInstaller(fixture.cache, fixture.metadata, fixture.transport, fixture.runtimes,
                (command, directory, cancellation) -> { installedProfile(fixture, directory, "neoforge-20.4.10"); return 0; });
        assertEquals("neoforge-20.4.10", installer.install("NEOFORGE", "1.20.4", null, temporaryDirectory.resolve("game"), new Cancellation()));
        assertTrue(fixture.transport.downloads.stream().anyMatch(url -> url.contains("neoforge-20.4.10-installer.jar")));
    }

    @Test void nonzeroInstallerExitIsNotReportedAsAnInstalledLoader() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        installerFixture(fixture, "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.4-49.0.1/forge-1.20.4-49.0.1-installer.jar");
        OfficialLoaderInstaller installer = new OfficialLoaderInstaller(fixture.cache, fixture.metadata, fixture.transport, fixture.runtimes,
                (command, directory, cancellation) -> 23);
        IOException error = assertThrows(IOException.class,
                () -> installer.install("FORGE", "1.20.4", "49.0.1", temporaryDirectory.resolve("game"), new Cancellation()));
        assertTrue(error.getMessage().contains("23"));
        assertFalse(Files.exists(fixture.cache.resolve("versions/1.20.4-forge-49.0.1/autoplug-loader-profile")));
    }

    @Test void installerCannotReportSuccessWithoutProducingItsVersionJson() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        installerFixture(fixture, "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.4-49.0.1/forge-1.20.4-49.0.1-installer.jar");
        OfficialLoaderInstaller installer = new OfficialLoaderInstaller(fixture.cache, fixture.metadata, fixture.transport, fixture.runtimes,
                (command, directory, cancellation) -> 0);
        assertThrows(IOException.class, () -> installer.install("FORGE", "1.20.4", "49.0.1", temporaryDirectory.resolve("game"), new Cancellation()));
    }

    private void installerFixture(LaunchFixtures fixture, String url) throws Exception {
        byte[] jar = new byte[]{1, 3, 5};
        fixture.transport.bytes.put(url, jar);
        fixture.transport.bytes.put(url + ".sha1", fixture.hash(jar).getBytes(StandardCharsets.US_ASCII));
    }

    private void installedProfile(LaunchFixtures fixture, Path game, String id) throws IOException {
        Path file = game.resolve("versions").resolve(id).resolve(id + ".json"); Files.createDirectories(file.getParent());
        Files.write(file, ("{\"id\":\"" + id + "\",\"inheritsFrom\":\"1.20.4\",\"mainClass\":\"fixture.ForgeMain\",\"libraries\":[]}").getBytes(StandardCharsets.UTF_8));
    }
}
