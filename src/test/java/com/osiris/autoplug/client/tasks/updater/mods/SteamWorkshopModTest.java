/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.configs.ModsConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.SteamCMD;
import com.osiris.betterthread.BThreadManager;
import com.osiris.jlib.logger.AL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamWorkshopModTest {

    @TempDir
    Path tempDir;

    @Test
    void readsPublishedIdAndNameFromMetaCpp() throws Exception {
        File modDir = tempDir.resolve("@CF").toFile();
        modDir.mkdirs();
        File metaFile = writeMeta(modDir,
                "protocol = 1;",
                "publishedid = 1559212036;",
                "name = \"CF\";",
                "timestamp = 5249804932187309401;");

        SteamWorkshopMod mod = SteamWorkshopMod.readFromMeta(modDir, metaFile);

        assertEquals(modDir, mod.getDirectory());
        assertEquals("CF", mod.getName());
        assertEquals("1559212036", mod.getPublishedId());
        assertEquals("5249804932187309401", mod.getTimestamp());
    }

    @Test
    void findsModsWithMetaCppInDirectSubdirectories() throws Exception {
        File firstModDir = tempDir.resolve("@CF").toFile();
        File secondModDir = tempDir.resolve("@CommunityOnlineTools").toFile();
        File ignoredDir = tempDir.resolve("keys").toFile();
        firstModDir.mkdirs();
        secondModDir.mkdirs();
        ignoredDir.mkdirs();
        writeMeta(firstModDir, "publishedid = 1559212036;", "name = \"CF\";");
        writeMeta(secondModDir, "publishedid = 1564026768;", "name = \"Community-Online-Tools\";");

        List<SteamWorkshopMod> mods = SteamWorkshopMod.findIn(tempDir.toFile());

        assertEquals(2, mods.size());
        assertEquals("1559212036", mods.get(0).getPublishedId());
        assertEquals("1564026768", mods.get(1).getPublishedId());
    }

    @Test
    void taskDoesNotDownloadWorkshopModWhenSteamMetadataIsCurrent() throws Exception {
        File oldWorkingDir = GD.WORKING_DIR;
        File oldDownloadsDir = GD.DOWNLOADS_DIR;
        String oldUserDir = System.getProperty("user.dir");
        useWorkingDir(tempDir);
        try {
            File modDir = tempDir.resolve("mods/@CF").toFile();
            modDir.mkdirs();
            writeMeta(modDir, "publishedid = 1559212036;", "name = \"CF\";", "timestamp = 100;");
            Files.write(modDir.toPath().resolve("old.txt"), Arrays.asList("old"), StandardCharsets.UTF_8);

            configureUpdater("200");
            FakeSteamCMD steamCMD = new FakeSteamCMD("200", null);
            TaskModsUpdater task = createTask(steamCMD);

            task.runAtStart();

            assertEquals(1, steamCMD.detailsCalls);
            assertEquals(0, steamCMD.updateCalls);
            assertFalse(Files.exists(modDir.toPath().resolve("updated.txt")));
            assertTrue(task.getWarnings().isEmpty());

            ModsConfig modsConfig = new ModsConfig();
            modsConfig.load();
            assertEquals("1559212036", modsConfig.get("mods", "CF", "steam-workshop-id").asString());
            assertEquals("200", modsConfig.get("mods", "CF", "version").asString());
        } finally {
            System.setProperty("user.dir", oldUserDir);
            GD.WORKING_DIR = oldWorkingDir;
            GD.DOWNLOADS_DIR = oldDownloadsDir;
        }
    }

    @Test
    void taskDownloadsWorkshopModWhenSteamMetadataIsNewerAndCachesVersion() throws Exception {
        File oldWorkingDir = GD.WORKING_DIR;
        File oldDownloadsDir = GD.DOWNLOADS_DIR;
        String oldUserDir = System.getProperty("user.dir");
        useWorkingDir(tempDir);
        try {
            File modDir = tempDir.resolve("mods/@CF").toFile();
            modDir.mkdirs();
            writeMeta(modDir, "publishedid = 1559212036;", "name = \"CF\";", "timestamp = 100;");
            Files.write(modDir.toPath().resolve("old.txt"), Arrays.asList("old"), StandardCharsets.UTF_8);

            File downloadedDir = tempDir.resolve("steamcmd/steamapps/workshop/content/221100/1559212036").toFile();
            downloadedDir.mkdirs();
            writeMeta(downloadedDir, "publishedid = 1559212036;", "name = \"CF\";", "timestamp = 200;");
            Files.write(downloadedDir.toPath().resolve("updated.txt"), Arrays.asList("updated"), StandardCharsets.UTF_8);

            configureUpdater("100");
            FakeSteamCMD steamCMD = new FakeSteamCMD("200", downloadedDir);
            TaskModsUpdater task = createTask(steamCMD);

            task.runAtStart();

            assertEquals(1, steamCMD.detailsCalls);
            assertEquals(1, steamCMD.updateCalls);
            assertEquals("221100", steamCMD.workshopAppId);
            assertEquals("1559212036", steamCMD.workshopItemId);
            assertEquals("updated", Files.readAllLines(modDir.toPath().resolve("updated.txt"), StandardCharsets.UTF_8).get(0));
            assertTrue(task.getWarnings().isEmpty());

            ModsConfig modsConfig = new ModsConfig();
            modsConfig.load();
            assertEquals("1559212036", modsConfig.get("mods", "CF", "steam-workshop-id").asString());
            assertEquals("200", modsConfig.get("mods", "CF", "version").asString());
        } finally {
            System.setProperty("user.dir", oldUserDir);
            GD.WORKING_DIR = oldWorkingDir;
            GD.DOWNLOADS_DIR = oldDownloadsDir;
        }
    }

    private File writeMeta(File modDir, String... lines) throws Exception {
        File metaFile = new File(modDir, "meta.cpp");
        Files.write(metaFile.toPath(), Arrays.asList(lines), StandardCharsets.UTF_8);
        return metaFile;
    }

    private void useWorkingDir(Path dir) throws IOException {
        System.setProperty("user.dir", dir.toAbsolutePath().toString());
        GD.VERSION = "AutoPlug-Client Test-Version";
        GD.WORKING_DIR = dir.toFile();
        GD.DOWNLOADS_DIR = dir.resolve("autoplug/downloads").toFile();
        GD.DOWNLOADS_DIR.mkdirs();
        File logFile = dir.resolve("autoplug/logs/latest.log").toFile();
        logFile.getParentFile().mkdirs();
        new AL().start("AL", true, logFile, false, false);
    }

    private void configureUpdater(String cachedWorkshopVersion) throws Exception {
        UpdaterConfig updaterConfig = new UpdaterConfig();
        updaterConfig.mods_updater.setValues("true");
        updaterConfig.mods_updater_profile.setValues("AUTOMATIC");
        updaterConfig.mods_updater_path.setValues("./mods");
        updaterConfig.mods_updater_version.setValues("1.20.1");
        updaterConfig.mods_updater_async.setValues("false");
        updaterConfig.server_software.setValues("221100");
        updaterConfig.save();

        ModsConfig modsConfig = new ModsConfig();
        modsConfig.put("mods", "CF", "version").setValues(cachedWorkshopVersion);
        modsConfig.put("mods", "CF", "steam-workshop-id").setValues("1559212036");
        modsConfig.save();
    }

    private TaskModsUpdater createTask(FakeSteamCMD steamCMD) {
        return new TaskModsUpdater("ModsUpdater", new BThreadManager()) {
            @Override
            SteamCMD createSteamCMD() {
                return steamCMD;
            }
        };
    }

    private static class FakeSteamCMD extends SteamCMD {
        final File workshopItemDir;
        final String latestVersion;
        int detailsCalls;
        int updateCalls;
        String workshopAppId;
        String workshopItemId;

        FakeSteamCMD(String latestVersion, File workshopItemDir) {
            this.latestVersion = latestVersion;
            this.workshopItemDir = workshopItemDir;
        }

        @Override
        public SteamWorkshopItemDetails getWorkshopItemDetails(String workshopItemId) {
            detailsCalls++;
            return new SteamWorkshopItemDetails(workshopItemId, "CF", latestVersion, null);
        }

        @Override
        public boolean installOrUpdateWorkshopItem(String workshopAppId, String workshopItemId, Consumer<String> onLog, Consumer<String> onLogErr) {
            this.updateCalls++;
            this.workshopAppId = workshopAppId;
            this.workshopItemId = workshopItemId;
            onLog.accept("Success. Downloaded item " + workshopItemId);
            return true;
        }

        @Override
        public File getWorkshopItemDir(String workshopAppId, String workshopItemId) {
            return workshopItemDir;
        }
    }
}
