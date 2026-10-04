/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.profiles;

import com.google.gson.*;
import com.osiris.autoplug.client.tasks.updater.mods.ModrinthAPI;
import com.osiris.autoplug.client.tasks.updater.mods.CurseForgeAPI;
import com.osiris.autoplug.client.tasks.updater.plugins.InstalledPluginLoader;
import com.osiris.autoplug.client.tasks.updater.plugins.ResourceFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileProviderTest {
    @TempDir Path directory;
    private String version(String id, String game, String loader, String date) {
        return "{\"id\":\"" + id + "\",\"project_id\":\"project\",\"version_number\":\"" + id + "\",\"game_versions\":[\"" + game + "\"],\"loaders\":[\"" + loader + "\"],\"date_published\":\"" + date + "\",\"files\":[{\"primary\":true,\"filename\":\"mod.jar\",\"url\":\"https://cdn.modrinth.com/data/project/mod.jar\",\"hashes\":{\"sha1\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}}],\"dependencies\":[]}";
    }
    @Test void selectsExactTargetAndLoaderInsteadOfNewerIncompatibleRelease() throws Exception {
        try (FixtureServer server = new FixtureServer("[" + version("new", "1.21.1", "fabric", "2026-09-01T00:00:00Z") + "," + version("old", "1.20.1", "fabric", "2024-01-01T00:00:00Z") + "]", 200)) {
            JsonObject selected = new ModrinthAPI(server.url()).compatibleProfileRelease("project", Collections.singletonList("fabric"), "1.20.1", null);
            assertEquals("old", selected.get("id").getAsString());
            assertTrue(server.request.contains("game_versions=")); assertTrue(server.request.contains("loaders="));
        }
    }
    @Test void emptySuccessfulReleaseListMeansUnsupportedBut503Throws() throws Exception {
        try (FixtureServer empty = new FixtureServer("[]", 200)) { assertNull(new ModrinthAPI(empty.url()).compatibleProfileRelease("project", Collections.singletonList("fabric"), "1.20.1", null)); }
        try (FixtureServer failed = new FixtureServer("{\"error\":\"unavailable\"}", 503)) { assertThrows(IOException.class, () -> new ModrinthAPI(failed.url()).compatibleProfileRelease("project", Collections.singletonList("fabric"), "1.20.1", null)); }
    }
    @Test void pinnedDependencyChecksLoaderAndGameCompatibility() throws Exception {
        try (FixtureServer server = new FixtureServer(version("pinned", "1.20.1", "forge", "2024-01-01T00:00:00Z"), 200)) {
            assertNull(new ModrinthAPI(server.url()).compatibleProfileRelease("project", Collections.singletonList("fabric"), "1.20.1", "pinned"));
        }
    }
    @Test void exactFileLookup404IsUnknownAndMalformedResponseIsError() throws Exception {
        Path file = directory.resolve("mod.jar"); Files.write(file, Collections.singletonList("fixture"));
        try (FixtureServer unknown = new FixtureServer("{}", 404)) { assertNull(new ModrinthAPI(unknown.url()).identifyProfileFile(file)); assertTrue(unknown.request.contains("algorithm=sha1")); }
        try (FixtureServer malformed = new FixtureServer("not JSON", 200)) { assertThrows(IOException.class, () -> new ModrinthAPI(malformed.url()).identifyProfileFile(file)); }
    }
    @Test void injectedProfileResolverUsesExistingResourceFinder() throws Exception {
        Path file = directory.resolve("mod.jar"); Files.write(file, Collections.singletonList("fixture"));
        Profile profile = new Profile("profile", Profile.Type.MODS, "1.20.1", "fabric", directory, false);
        int[] calls = {0};
        ModrinthAPI api = new ModrinthAPI() {
            @Override public JsonObject identifyProfileFile(Path installed) { return JsonParser.parseString("{\"project_id\":\"project\"}").getAsJsonObject(); }
        };
        ResourceFinder finder = new ResourceFinder() {
            @Override public JsonObject findProfileRelease(ModrinthAPI ignored, String project, List<String> loaders, String game, String pinned) {
                calls[0]++; assertEquals("1.20.1", game); assertEquals(Collections.singletonList("fabric"), loaders);
                return JsonParser.parseString(version("compatible", game, "fabric", "2024-01-01T00:00:00Z")).getAsJsonObject();
            }
        };
        ProfileUpdater.Resolution result = new AutoPlugProfileResolver(api, finder).resolve(profile, file);
        assertTrue(result.compatible); assertEquals(1, calls[0]); assertEquals("compatible", result.versionId);
    }
    @Test void distinguishesNeoForgeFromForgeAndOldTargetFromNewerRelease() {
        JsonArray files = JsonParser.parseString("[{\"id\":1,\"gameVersions\":[\"1.20.1\",\"Forge\"],\"fileDate\":\"2024-01-01T00:00:00Z\"},{\"id\":2,\"gameVersions\":[\"1.21.1\",\"Forge\"],\"fileDate\":\"2026-01-01T00:00:00Z\"},{\"id\":3,\"gameVersions\":[\"1.20.1\",\"NeoForge\"],\"fileDate\":\"2025-01-01T00:00:00Z\"}]").getAsJsonArray();
        assertEquals(1, CurseForgeAPI.selectCompatibleProfileRelease(files, "FORGE", "1.20.1").get("id").getAsInt());
        assertEquals(3, CurseForgeAPI.selectCompatibleProfileRelease(files, "NEOFORGE", "1.20.1").get("id").getAsInt());
        assertNull(CurseForgeAPI.selectCompatibleProfileRelease(files, "FABRIC", "1.20.1"));
    }
    @Test void bukkitAndSpigotTargetsNeverAcceptPaperOnlyArtifacts() {
        assertEquals(Collections.singletonList("bukkit"), InstalledPluginLoader.forProfile("BUKKIT"));
        assertFalse(InstalledPluginLoader.forProfile("SPIGOT").contains("paper"));
        assertTrue(InstalledPluginLoader.forProfile("PAPER").contains("bukkit"));
        assertFalse(InstalledPluginLoader.forProfile("PAPER").contains("purpur"));
    }
    /** A real TCP HTTP fixture, not a stubbed return value, covers transport status and parsing failures. */
    static final class FixtureServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        final ExecutorService executor = Executors.newSingleThreadExecutor(); volatile String request;
        FixtureServer(String body, int status) throws IOException {
            executor.submit(() -> {
                try (Socket client = socket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII)); request = reader.readLine();
                    String line; while ((line = reader.readLine()) != null && !line.isEmpty()) {}
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    OutputStream output = client.getOutputStream(); output.write(("HTTP/1.1 " + status + " Result\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); output.write(bytes); output.flush();
                } catch (IOException exception) { if (!socket.isClosed()) throw new UncheckedIOException(exception); }
            });
        }
        String url() { return "http://127.0.0.1:" + socket.getLocalPort(); }
        @Override public void close() throws IOException { socket.close(); executor.shutdownNow(); }
    }
}
