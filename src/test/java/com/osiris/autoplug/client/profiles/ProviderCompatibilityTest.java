package com.osiris.autoplug.client.profiles;

import com.google.gson.*;
import com.osiris.autoplug.client.tasks.updater.mods.*;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProviderCompatibilityTest {
    @TempDir Path temp;
    @Test void curseForgeDowngradeUsesArtifactHashNotCopiedTimestamp() throws Exception {
        Path jar = temp.resolve("mod.jar"); Files.write(jar, "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.setLastModifiedTime(jar, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 86400000));
        MinecraftMod mod = new MinecraftMod(jar.toString(), "Mod", "2", "Author", null, "123", null);
        JsonObject release = JsonParser.parseString("{\"fileName\":\"mod-1.jar\",\"downloadUrl\":\"https://example.invalid/mod.jar\",\"fileLength\":3,\"fileDate\":\"2020-01-01T00:00:00Z\",\"hashes\":[{\"algo\":1,\"value\":\"0000000000000000000000000000000000000000\"}]}").getAsJsonObject();
        SearchResult result = new CurseForgeAPI().compatibleArtifactResult(release, mod);
        assertEquals(SearchResult.Type.UPDATE_AVAILABLE, result.type);
        release.getAsJsonArray("hashes").get(0).getAsJsonObject().addProperty("value", "a9993e364706816aba3e25717850c26c9cd0d89d");
        assertEquals(SearchResult.Type.UP_TO_DATE, new CurseForgeAPI().compatibleArtifactResult(release, mod).type);
    }
    @Test void exactModrinthSelectionRejectsWrongLoaderAndPreservesPrimaryChecksum() throws Exception {
        JsonArray releases = JsonParser.parseString("["
                + "{\"version_number\":\"wrong\",\"date_published\":\"2025-01-01T00:00:00Z\",\"game_versions\":[\"1.20.1\"],\"loaders\":[\"neoforge\"],\"files\":[]},"
                + "{\"version_number\":\"older-compatible\",\"date_published\":\"2024-01-01T00:00:00Z\",\"game_versions\":[\"1.20.1\"],\"loaders\":[\"fabric\"],\"files\":[{\"filename\":\"sources.jar\",\"primary\":false,\"url\":\"https://example.invalid/sources\",\"hashes\":{}},{\"filename\":\"main.jar\",\"primary\":true,\"url\":\"https://example.invalid/main\",\"size\":3,\"hashes\":{\"sha1\":\"a9993e364706816aba3e25717850c26c9cd0d89d\"}}]}]").getAsJsonArray();
        Path installed = temp.resolve("mod.jar"); Files.write(installed, "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        SearchResult found = new ModrinthAPI().compatibleResult(releases, java.util.List.of("fabric"), "1.20.1", installed);
        assertEquals("older-compatible", found.latestVersion); assertEquals("main.jar", found.fileName); assertEquals(SearchResult.Type.UP_TO_DATE, found.type);
        assertEquals(SearchResult.Type.RESOURCE_NOT_FOUND, new ModrinthAPI().compatibleResult(releases, java.util.List.of("fabric"), "1.21.1", installed).type);
    }
}
