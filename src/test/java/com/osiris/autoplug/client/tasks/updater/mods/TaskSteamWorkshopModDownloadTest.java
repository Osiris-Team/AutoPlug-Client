/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.UtilsTest;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.SteamCMD;
import com.osiris.betterthread.BThreadManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSteamWorkshopModDownloadTest {

    @TempDir
    File tempDir;

    @Test
    void automaticProfileDownloadsAndInstallsWorkshopDirectory() throws Exception {
        UtilsTest.init();
        File installedDir = new File(tempDir, "@CF");
        installedDir.mkdirs();
        SteamWorkshopMod mod = new SteamWorkshopMod(installedDir, "CF", "1559212036", "100");
        SearchResult result = new SearchResult(null, SearchResult.Type.UPDATE_AVAILABLE, "200", null, "steam-workshop", null, null, false);
        result.mod = mod;
        FakeSteamCMD steamCMD = new FakeSteamCMD(tempDir);

        TaskSteamWorkshopModDownload task = new TaskSteamWorkshopModDownload(
                "SteamWorkshopModDownloader", new BThreadManager(), mod, "221100", "AUTOMATIC", steamCMD, result);
        task.runAtStart();

        assertTrue(task.isDownloadSuccessful());
        assertTrue(task.isInstallSuccessful());
        assertTrue(new File(installedDir, "updated.txt").exists());
        assertEquals(result, task.getSearchResult());
    }

    private static class FakeSteamCMD extends SteamCMD {
        private final File root;

        private FakeSteamCMD(File root) {
            this.root = root;
            this.destDir = new File(root, "steamcmd");
        }

        @Override
        public boolean installOrUpdateWorkshopItem(String workshopAppId, String workshopItemId, Consumer<String> onLog, Consumer<String> onLogErr) {
            try {
                File downloaded = getWorkshopItemDir(workshopAppId, workshopItemId);
                downloaded.mkdirs();
                Files.write(new File(downloaded, "updated.txt").toPath(), "updated".getBytes(StandardCharsets.UTF_8));
                onLog.accept("Success. Downloaded item " + workshopItemId);
                return true;
            } catch (Exception e) {
                onLogErr.accept(e.getMessage());
                return false;
            }
        }
    }
}
