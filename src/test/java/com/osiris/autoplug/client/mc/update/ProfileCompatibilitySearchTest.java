package com.osiris.autoplug.client.mc.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.tasks.updater.mods.CurseForgeAPI;
import com.osiris.autoplug.client.tasks.updater.mods.InstalledModLoader;
import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.mods.ModrinthAPI;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileCompatibilitySearchTest {
    @Test void modrinthSelectsExactVersionLoaderAndPrimaryJarWithoutTimestampOrdering() {
        JsonArray releases = json("["
                + "{\"game_versions\":[\"1.21\"],\"loaders\":[\"fabric\"],\"files\":[]},"
                + "{\"game_versions\":[\"1.20.1\"],\"loaders\":[\"forge\"],\"files\":[]},"
                + "{\"game_versions\":[\"1.20.1\"],\"loaders\":[\"fabric\"],\"version_number\":\"old-2\",\"files\":["
                + "{\"filename\":\"sources.jar\",\"url\":\"https://example.invalid/sources.jar\",\"primary\":false},"
                + "{\"filename\":\"target.jar\",\"url\":\"https://example.invalid/target.jar\",\"primary\":true}]}]");
        ModrinthAPI api = new ModrinthAPI(url -> {
            assertTrue(url.contains("game_versions=")); assertTrue(url.contains("1.20.1")); return releases;
        });
        SearchResult result = api.searchCompatible(List.of("fabric"), "example", "1.20.1");
        assertEquals(SearchResult.Type.UPDATE_AVAILABLE, result.type);
        assertEquals("https://example.invalid/target.jar", result.downloadUrl);
        assertEquals("old-2", result.latestVersion);
    }

    @Test void emptyCompatibleSetAndTransportFailureHaveDifferentOutcomes() {
        SearchResult empty = new ModrinthAPI(url -> json("[]")).searchCompatible(List.of("neoforge"), "example", "1.20.1");
        assertEquals(SearchResult.Type.NO_COMPATIBLE_RELEASE, empty.type);
        assertFalse(SearchResult.isMatchFound(empty));
        SearchResult outage = new ModrinthAPI(url -> { throw new IOException("Synthetic outage"); })
                .searchCompatible(List.of("neoforge"), "example", "1.20.1");
        assertEquals(SearchResult.Type.API_ERROR, outage.type);
        assertNotNull(outage.exception);
    }

    @Test void missingOrMalformedProviderMappingDoesNotMakeAnApiRequest() {
        ModrinthAPI api = new ModrinthAPI(url -> { fail("Invalid mapping must not reach transport"); return null; });
        assertEquals(SearchResult.Type.RESOURCE_NOT_FOUND, api.searchCompatible(List.of("fabric"), null, "1.20.1").type);
        assertEquals(SearchResult.Type.RESOURCE_NOT_FOUND, api.searchCompatible(List.of("fabric"), "../../other", "1.20.1").type);
    }

    @Test void curseforgeRequiresExactVersionAndDistinguishesNeoForgeFromForge() {
        MinecraftMod mod = new MinecraftMod("unused", "Example", "9", "Fixture", null, "123", null);
        mod.forceLatest = true;
        CurseForgeAPI api = new CurseForgeAPI(url -> {
            assertTrue(url.contains("gameVersion=1.20.1"));
            return json("[{\"id\":9,\"gameVersions\":[\"1.21\",\"NeoForge\"]},"
                    + "{\"id\":8,\"gameVersions\":[\"1.20.1\",\"Forge\"]},"
                    + "{\"id\":7,\"gameVersions\":[\"1.20.1\",\"NeoForge\"],\"fileName\":\"compatible.jar\",\"downloadUrl\":\"https://example.invalid/compatible.jar\"}]");
        });
        SearchResult result = api.searchCompatible(new InstalledModLoader("neoforge"), mod, "1.20.1");
        assertEquals(SearchResult.Type.UPDATE_AVAILABLE, result.type);
        assertEquals("compatible.jar", result.fileName);
        assertTrue(mod.forceLatest, "Profile selection does not mutate legacy configuration values");
    }

    @Test void curseforgeMissingLoaderInformationCannotBeCertifiedIncompatible() {
        MinecraftMod mod = new MinecraftMod("unused", "Example", "9", "Fixture", null, "123", null);
        SearchResult result = new CurseForgeAPI(url -> json("[{\"id\":7,\"gameVersions\":[\"1.20.1\"]}]") )
                .searchCompatible(new InstalledModLoader("forge"), mod, "1.20.1");
        assertEquals(SearchResult.Type.API_ERROR, result.type);
        assertNotNull(result.exception);
    }

    @Test void curseforgeDoesNotTreatPaginationOrRestrictedDownloadsAsIncompatibility() {
        MinecraftMod mod = new MinecraftMod("unused", "Example", "9", "Fixture", null, "123", null);
        JsonArray fullPage = new JsonArray();
        for (int i = 0; i < 50; i++) fullPage.add(JsonParser.parseString("{\"id\":" + i + ",\"gameVersions\":[\"1.20.1\",\"Fabric\"]}"));
        assertEquals(SearchResult.Type.API_ERROR, new CurseForgeAPI(url -> fullPage)
                .searchCompatible(new InstalledModLoader("forge"), mod, "1.20.1").type);
        assertEquals(SearchResult.Type.API_ERROR, new CurseForgeAPI(url -> json("[{\"id\":7,\"gameVersions\":[\"1.20.1\",\"Forge\"],\"downloadUrl\":null}]") )
                .searchCompatible(new InstalledModLoader("forge"), mod, "1.20.1").type);
        assertEquals(SearchResult.Type.NO_COMPATIBLE_RELEASE, new CurseForgeAPI(url -> json("[]"))
                .searchCompatible(new InstalledModLoader("forge"), mod, "1.20.1").type);
    }

    private static JsonArray json(String source) { return JsonParser.parseString(source).getAsJsonArray(); }
}
