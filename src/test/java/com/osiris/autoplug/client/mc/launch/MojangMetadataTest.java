package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MojangMetadataTest {
    @TempDir Path temporaryDirectory;

    @Test void importsLoaderInheritanceOverridesLibrariesAndRetainsTheVanillaClientJar() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        JsonObject loader = fixture.json("{\"id\":\"fixture-loader\",\"inheritsFrom\":\"1.20.4\",\"mainClass\":\"fixture.loader.Main\",\"libraries\":[{\"name\":\"example:library:2.0\"}],\"arguments\":{\"game\":[\"--loaderFlag\",\"fixture\"]}}");
        Path input = temporaryDirectory.resolve("loader.json"); Files.write(input, loader.toString().getBytes(StandardCharsets.UTF_8));
        fixture.metadata.importInstalledLoaderProfile(input);
        JsonObject merged = fixture.metadata.version("fixture-loader", new Cancellation());
        assertEquals("fixture.loader.Main", merged.get("mainClass").getAsString());
        assertEquals("1.20.4", merged.get("autoplugClientVersion").getAsString());
        assertEquals(1, merged.getAsJsonArray("libraries").size());
        assertEquals("example:library:2.0", merged.getAsJsonArray("libraries").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("--username", merged.getAsJsonObject("arguments").getAsJsonArray("game").get(0).getAsString());
        assertEquals("fixture", merged.getAsJsonObject("arguments").getAsJsonArray("game").get(7).getAsString());
    }

    @Test void fabricAndQuiltUseTheirOwnMetadataApisAndValidateTheRequestedBase() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        fixture.transport.responses.put("https://meta.fabricmc.net/v2/versions/loader/1.20.4/0.15.11/profile/json",
                fixture.json("{\"id\":\"fabric-fixture\",\"inheritsFrom\":\"1.20.4\",\"mainClass\":\"fixture.Fabric\",\"libraries\":[]}"));
        fixture.transport.responses.put("https://meta.quiltmc.org/v3/versions/loader/1.20.4/0.26.0/profile/json",
                fixture.json("{\"id\":\"quilt-fixture\",\"inheritsFrom\":\"1.20.4\",\"mainClass\":\"fixture.Quilt\",\"libraries\":[]}"));
        assertEquals("fabric-fixture", fixture.metadata.installFabric("1.20.4", "0.15.11", new Cancellation()));
        assertEquals("quilt-fixture", fixture.metadata.installQuilt("1.20.4", "0.26.0", new Cancellation()));
        assertEquals("fixture.Quilt", fixture.metadata.version("quilt-fixture", new Cancellation()).get("mainClass").getAsString());
    }

    @Test void inheritedProfileCyclesAndMismatchedIdsFailClearly() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        JsonObject cyclic = fixture.json("{\"id\":\"cycle\",\"inheritsFrom\":\"cycle\",\"mainClass\":\"fixture.Main\",\"libraries\":[]}");
        Path input = temporaryDirectory.resolve("cycle.json"); Files.write(input, cyclic.toString().getBytes(StandardCharsets.UTF_8));
        fixture.metadata.importInstalledLoaderProfile(input);
        assertThrows(IOException.class, () -> fixture.metadata.version("cycle", new Cancellation()));
        fixture.version.addProperty("id", "wrong-id");
        fixture.publishVersion();
        assertThrows(IOException.class, () -> fixture.metadata.version("1.20.4", new Cancellation()));
    }

    @Test void cachedMetadataMustStillMatchItsOfficialHash() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        fixture.metadata.version("1.20.4", new Cancellation());
        Path cached = fixture.cache.resolve("versions/1.20.4/1.20.4.json");
        Files.write(cached, "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("1.20.4", fixture.metadata.version("1.20.4", new Cancellation()).get("id").getAsString());
        assertTrue(Files.size(cached) > 2);
    }

    @Test void libraryChecksumSidecarsAreVerifiedAndInstallerOutputsDetectLaterChanges() throws Exception {
        LaunchFixtures fixture = new LaunchFixtures(temporaryDirectory);
        byte[] library = new byte[]{9, 8};
        fixture.transport.bytes.put("https://fixture.invalid/loader.jar", library);
        fixture.transport.bytes.put("https://fixture.invalid/loader.jar.sha1", (fixture.hash(library) + "  loader.jar").getBytes(StandardCharsets.US_ASCII));
        Path artifact = fixture.cache.obtainWithSidecar("libraries/loader.jar", java.net.URI.create("https://fixture.invalid/loader.jar"), new Cancellation());
        assertArrayEquals(library, Files.readAllBytes(artifact));
        Path generated = temporaryDirectory.resolve("generated.jar"); Files.write(generated, new byte[]{3});
        fixture.cache.importInstallerLibrary("libraries/generated.jar", generated);
        Path installed = fixture.cache.installedLibrary("libraries/generated.jar"); assertNotNull(installed);
        Files.write(installed, new byte[]{4});
        assertThrows(IOException.class, () -> fixture.cache.installedLibrary("libraries/generated.jar"));
    }
}
