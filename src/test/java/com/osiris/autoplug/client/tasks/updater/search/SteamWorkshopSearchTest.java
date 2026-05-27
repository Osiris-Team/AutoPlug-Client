/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.osiris.autoplug.client.tasks.updater.mods.SteamWorkshopMod;
import com.osiris.autoplug.client.utils.SteamCMD;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SteamWorkshopSearchTest {

    @TempDir
    File tempDir;

    @Test
    void returnsUpdateAvailableWhenSteamTimestampIsNewer() {
        SteamWorkshopMod mod = new SteamWorkshopMod(tempDir, "CF", "1559212036", "100");
        SteamWorkshopSearch search = new SteamWorkshopSearch();

        SearchResult result = search.search(mod, "221100", new FakeSteamCMD("200"));

        assertEquals(SearchResult.Type.UPDATE_AVAILABLE, result.type);
        assertEquals("200", result.getLatestVersion());
        assertEquals("steam-workshop", result.getDownloadType());
        assertEquals(mod, result.mod);
    }

    @Test
    void treatsMetaCppTimestampAsStaleWhenSteamReturnsUnixTime() {
        SteamWorkshopMod mod = new SteamWorkshopMod(tempDir, "CF", "1559212036", "5249804932187309401");
        SteamWorkshopSearch search = new SteamWorkshopSearch();

        SearchResult result = search.search(mod, "221100", new FakeSteamCMD("1715700000"));

        assertEquals(SearchResult.Type.UPDATE_AVAILABLE, result.type);
    }

    @Test
    void returnsUpToDateWhenSteamTimestampMatches() {
        SteamWorkshopMod mod = new SteamWorkshopMod(tempDir, "CF", "1559212036", "200");
        SteamWorkshopSearch search = new SteamWorkshopSearch();

        SearchResult result = search.search(mod, "221100", new FakeSteamCMD("200"));

        assertEquals(SearchResult.Type.UP_TO_DATE, result.type);
    }

    @Test
    void rejectsNonNumericSteamAppId() {
        SteamWorkshopMod mod = new SteamWorkshopMod(tempDir, "CF", "1559212036", "100");
        SteamWorkshopSearch search = new SteamWorkshopSearch();

        SearchResult result = search.search(mod, "paper", new FakeSteamCMD("200"));

        assertEquals(SearchResult.Type.API_ERROR, result.type);
    }

    private static class FakeSteamCMD extends SteamCMD {
        private final String timeUpdated;

        private FakeSteamCMD(String timeUpdated) {
            this.timeUpdated = timeUpdated;
        }

        @Override
        public SteamWorkshopItemDetails getWorkshopItemDetails(String workshopItemId) throws IOException {
            return new SteamWorkshopItemDetails(workshopItemId, "CF", timeUpdated, null);
        }
    }
}
