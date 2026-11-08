package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthArtifactIdentityTest {
    private static final String SHA1_ABC = "a9993e364706816aba3e25717850c26c9cd0d89d";
    @TempDir Path directory;

    @Test void resolvesTopLevelProjectFromExactBytesWithoutChangingInstalledFile() throws Exception {
        Path installed = artifact();
        byte[] before = Files.readAllBytes(installed);
        AtomicReference<FixtureConnection> request = new AtomicReference<>();
        ModrinthAPI api = new ModrinthAPI(url -> {
            FixtureConnection connection = new FixtureConnection(url, 200,
                    "{\"id\":\"Version1\",\"project_id\":\"AABBCCDD\",\"dependencies\":[{\"project_id\":\"Other123\"}],\"files\":[{\"hashes\":{\"sha1\":\"" + SHA1_ABC + "\"}}]}");
            request.set(connection); return connection;
        });
        assertEquals("AABBCCDD", api.findProjectForArtifact(installed));
        assertEquals("https://api.modrinth.com/v2/version_file/" + SHA1_ABC + "?algorithm=sha1", request.get().getURL().toString());
        assertEquals("GET", request.get().getRequestMethod());
        assertEquals(15000, request.get().getConnectTimeout());
        assertEquals(20000, request.get().getReadTimeout());
        assertTrue(request.get().getRequestProperty("User-Agent").contains("AutoPlug-Client"));
        assertTrue(request.get().disconnected);
        assertArrayEquals(before, Files.readAllBytes(installed));
    }

    @Test void unknownHashReturnsNullButProviderErrorsRemainErrors() throws Exception {
        Path installed = artifact();
        FixtureConnection absent = new FixtureConnection(new URL("https://example.invalid"), 404, "");
        assertNull(new ModrinthAPI(url -> absent).findProjectForArtifact(installed));
        assertTrue(absent.disconnected);
        FixtureConnection failed = new FixtureConnection(new URL("https://example.invalid"), 503, "unavailable");
        IOException exception = assertThrows(IOException.class, () -> new ModrinthAPI(url -> failed).findProjectForArtifact(installed));
        assertTrue(exception.getMessage().contains("503"));
        assertTrue(failed.disconnected);
        assertThrows(SocketTimeoutException.class, () -> new ModrinthAPI(url -> { throw new SocketTimeoutException("Fixture timeout"); }).findProjectForArtifact(installed));
    }

    @Test void malformedSuccessfulResponsesDoNotLookLikeUnknownArtifacts() throws Exception {
        Path installed = artifact();
        for (String body : List.of("not-json", "[]", "{}", "{\"project_id\":null}", "{\"project_id\":12}", "{\"project_id\":\"../escape\"}")) {
            ModrinthAPI api = new ModrinthAPI(url -> new FixtureConnection(url, 200, body));
            assertThrows(IOException.class, () -> api.findProjectForArtifact(installed), body);
        }
    }

    @Test void absentLocalArtifactFailsBeforeAnyNetworkRequest() {
        ModrinthAPI api = new ModrinthAPI(url -> { throw new AssertionError("Missing files must not trigger a lookup"); });
        assertThrows(IOException.class, () -> api.findProjectForArtifact(directory.resolve("missing.jar")));
    }

    @Test void compatibleResultsCarryIdentityForCurrentAndReplacementButNotMissingRelease() throws Exception {
        Path installed = artifact();
        String response = "[{\"project_id\":\"AABBCCDD\",\"version_number\":\"1.0\",\"date_published\":\"2024-01-01T00:00:00Z\","
                + "\"game_versions\":[\"1.20.1\"],\"loaders\":[\"paper\"],\"files\":[{\"filename\":\"plugin.jar\",\"primary\":true,"
                + "\"url\":\"https://cdn.modrinth.com/data/AABBCCDD/plugin.jar\",\"size\":3,\"hashes\":{\"sha1\":\"" + SHA1_ABC + "\"}}]}]";
        ModrinthAPI api = new ModrinthAPI(url -> new FixtureConnection(url, 200, response));
        SearchResult current = api.searchCompatible(List.of("paper"), "AABBCCDD", "1.20.1", installed);
        assertEquals(SearchResult.Type.UP_TO_DATE, current.type);
        assertEquals("AABBCCDD", current.modrinthProjectId);
        Files.write(installed, "different".getBytes(StandardCharsets.UTF_8));
        SearchResult replacement = api.searchCompatible(List.of("paper"), "AABBCCDD", "1.20.1", installed);
        assertEquals(SearchResult.Type.UPDATE_AVAILABLE, replacement.type);
        assertEquals("AABBCCDD", replacement.modrinthProjectId);
        SearchResult missing = api.searchCompatible(List.of("paper"), "AABBCCDD", "1.21.1", installed);
        assertEquals(SearchResult.Type.RESOURCE_NOT_FOUND, missing.type);
        assertNull(missing.modrinthProjectId);
        SearchResult failed = new ModrinthAPI(url -> new FixtureConnection(url, 500, "")).searchCompatible(List.of("paper"), "AABBCCDD", "1.20.1", installed);
        assertEquals(SearchResult.Type.API_ERROR, failed.type);
        assertNull(failed.modrinthProjectId);
    }

    private Path artifact() throws IOException {
        Path file = directory.resolve("a-name-unrelated-to-its-project.jar");
        Files.write(file, "abc".getBytes(StandardCharsets.UTF_8)); return file;
    }

    private static final class FixtureConnection extends HttpURLConnection {
        private final int status;
        private final byte[] body;
        boolean disconnected;
        FixtureConnection(URL url, int status, String body) {
            super(url); this.status = status; this.body = body.getBytes(StandardCharsets.UTF_8);
        }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
