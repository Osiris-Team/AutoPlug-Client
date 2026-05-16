/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.tasks.updater.TaskDownload;
import com.osiris.autoplug.client.utils.io.AsyncReader;
import com.osiris.autoplug.client.utils.terminal.AsyncTerminal;
import com.osiris.betterthread.BThreadManager;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.osiris.jprocesses2.util.OS.isMac;
import static com.osiris.jprocesses2.util.OS.isWindows;

@SuppressWarnings({"WeakerAccess", "unused"})
public class SteamCMD {

    private static final String STEAM_WORKSHOP_DETAILS_URL = "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/";
    private static final String STEAMCMD_WORKSHOP_COMMAND = "+login {LOGIN} +workshop_download_item {WORKSHOP_APP} {WORKSHOP_ITEM} validate +quit";
    private final String steamcmdArchive = "steamcmd" + (isWindows ? ".zip" : isMac ? "_osx.tar.gz" : "_linux.tar.gz");
    private final String steamcmdExtension = isWindows ? ".exe" : ".sh";
    private final String steamcmdExecutable = "steamcmd" + steamcmdExtension;
    // removed "{APP} validate" because it takes ages, doesn't work?
    private final String steamcmdCommand = "+login {LOGIN} +force_install_dir \"{DESTINATION}\" +app_update {APP} +quit";
    private final String steamcmdUrl = "https://steamcdn-a.akamaihd.net/client/installer/" + steamcmdArchive;
    public Map<String, String> errorResolutions = new HashMap<String, String>() {{
        put("Invalid platform", "This server does not support this OS; nothing we can do about it.");
    }};
    public File destDir = new File(GD.WORKING_DIR + "/autoplug/system/steamcmd");
    public File destExe = new File(destDir + "/" + steamcmdExecutable);
    public File dirSteamServersDownloads = new File(GD.DOWNLOADS_DIR + "/steam-servers");
    public File destArchive = new File(destDir + "/" + steamcmdArchive);

    public boolean isInstalled() {
        return destExe.exists();
    }

    public boolean installIfNeeded() {
        if (!isInstalled())
            return installOrUpdate(line -> {
            }, errLine -> {
            });
        return true;
    }

    public boolean installOrUpdate(Consumer<String> onLog, Consumer<String> onLogErr) {
        try {
            AL.debug(this.getClass(), "Installing SteamCMD, this might take a bit...");
            onLog.accept("Installing SteamCMD, this might take a bit...");
            destDir.mkdirs();
            destExe.delete();
            BThreadManager man = new BThreadManager();
            TaskDownload download = new TaskDownload("Download", man, steamcmdUrl, destArchive, true);
            download.start();
            while (!download.isFinished()) {
                Thread.sleep(100);
            }
            AL.debug(this.getClass(), download.getStatus());

            if (!download.isSuccess()) {
                AL.warn("Failed to download SteamCMD! " + download.getStatus());
                return false;
            }
            Archiver archiver;
            if (isWindows) // A zip
                archiver = ArchiverFactory.createArchiver(ArchiveFormat.ZIP);
            else
                archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP);
            archiver.extract(destArchive, destDir);
            destArchive.delete();
            AL.debug(this.getClass(), "SteamCMD installed successfully at: " + destExe);

            Process p = new ProcessBuilder(destExe.getAbsolutePath(), "+quit").start();
            Thread thread = new Thread(p::destroyForcibly);
            Runtime.getRuntime().addShutdownHook(thread);
            new AsyncReader(p.getInputStream(), onLog); // Without a reader it seems to never finish
            new AsyncReader(p.getErrorStream(), onLogErr); // Without a reader it seems to never finish
            p.waitFor();
            Runtime.getRuntime().removeShutdownHook(thread);
            AL.debug(this.getClass(), "SteamCMD installed and setup successfully!");
            onLog.accept("SteamCMD installed and setup successfully!");
        } catch (Exception e) {
            AL.warn(e);
            return false;
        }

        return isInstalled();
    }

    public boolean installOrUpdateServer(Consumer<String> onLog, Consumer<String> onLogErr) throws NotLoadedException, YamlReaderException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, IllegalListException {
        return installOrUpdateServer(new UpdaterConfig().server_software.asString(), onLog, onLogErr);
    }

    public boolean installOrUpdateServer(String appId, Consumer<String> onLog, Consumer<String> onLogErr) {
        try {
            installIfNeeded();
            AL.debug(this.getClass(), "Installing app " + appId + "...");
            onLog.accept("Installing app " + appId + "...");

            String login = getLogin();

            File gameInstallDir = new File(dirSteamServersDownloads + "/" + appId);
            gameInstallDir.mkdirs();
            List<String> logLines = new ArrayList<>();
            List<String> logErrLines = new ArrayList<>();
            AtomicBoolean isFinished = new AtomicBoolean(false);
            AtomicBoolean isSuccess = new AtomicBoolean(true);
            // Doesn't work when directly executing via ProcessBuilder thats why we execute it from an
            // actual terminal
            AsyncTerminal terminal = new AsyncTerminal(destDir, line -> { // Without a reader it seems to never finish
                onLog.accept(line);
                logLines.add(line);
                if (line.toLowerCase().startsWith("Success! App".toLowerCase()))
                    isFinished.set(true);
                if (line.toLowerCase().startsWith("ERROR!".toLowerCase())) // Case actually changes without reason?!
                {
                    isSuccess.set(false);
                    isFinished.set(true);
                }
            }, line -> {
                onLogErr.accept(line);
                logErrLines.add(line);
            },
                    destExe.getAbsolutePath() + " " + steamcmdCommand
                            .replace("{LOGIN}", login)
                            .replace("{DESTINATION}", gameInstallDir.getAbsolutePath())
                            .replace("{APP}", appId));

            Thread thread = new Thread(() -> {
                terminal.process.destroy();
            });
            Runtime.getRuntime().addShutdownHook(thread);
            while (!isFinished.get()) Thread.sleep(100);
            terminal.process.destroy();
            Runtime.getRuntime().removeShutdownHook(thread);
            return isSuccess.get();
        } catch (Exception e) {
            AL.warn(e);
            return false;
        }
    }

    public boolean installOrUpdateWorkshopItem(String workshopAppId, String workshopItemId, Consumer<String> onLog, Consumer<String> onLogErr) {
        try {
            if (!installIfNeeded()) return false;
            AL.debug(this.getClass(), "Installing workshop item " + workshopItemId + " for app " + workshopAppId + "...");
            onLog.accept("Installing workshop item " + workshopItemId + "...");

            String command = buildWorkshopItemCommand(getLogin(), workshopAppId, workshopItemId);
            List<String> logLines = new ArrayList<>();
            List<String> logErrLines = new ArrayList<>();
            AtomicBoolean isFinished = new AtomicBoolean(false);
            AtomicBoolean isSuccess = new AtomicBoolean(true);
            AsyncTerminal terminal = new AsyncTerminal(destDir, line -> {
                onLog.accept(line);
                logLines.add(line);
                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith("success.") && lowerLine.contains("item " + workshopItemId.toLowerCase()))
                    isFinished.set(true);
                if (lowerLine.startsWith("error!")) {
                    isSuccess.set(false);
                    isFinished.set(true);
                }
            }, line -> {
                onLogErr.accept(line);
                logErrLines.add(line);
            }, destExe.getAbsolutePath() + " " + command);

            Thread thread = new Thread(() -> terminal.process.destroy());
            Runtime.getRuntime().addShutdownHook(thread);
            while (!isFinished.get() && terminal.process.isAlive()) Thread.sleep(100);
            if (terminal.process.isAlive()) terminal.process.destroy();
            Runtime.getRuntime().removeShutdownHook(thread);
            return isSuccess.get() && getWorkshopItemDir(workshopAppId, workshopItemId).exists();
        } catch (Exception e) {
            AL.warn(e);
            return false;
        }
    }

    public SteamWorkshopItemDetails getWorkshopItemDetails(String workshopItemId) throws IOException {
        Request request = new Request.Builder()
                .url(STEAM_WORKSHOP_DETAILS_URL)
                .post(new FormBody.Builder()
                        .add("itemcount", "1")
                        .add("publishedfileids[0]", workshopItemId)
                        .build())
                .header("User-Agent", "AutoPlug-Client - https://autoplug.one")
                .build();

        Response response = new OkHttpClient().newCall(request).execute();
        ResponseBody body = null;
        try {
            if (response.code() != 200)
                throw new IOException("Steam Workshop details request failed for item " + workshopItemId + " with code " + response.code() + " message: " + response.message());

            body = response.body();
            if (body == null)
                throw new IOException("Steam Workshop details request returned no body for item " + workshopItemId);

            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonObject responseObject = root.getAsJsonObject("response");
            JsonArray details = responseObject == null ? null : responseObject.getAsJsonArray("publishedfiledetails");
            if (details == null || details.size() == 0)
                throw new IOException("Steam Workshop details request returned no details for item " + workshopItemId);

            JsonObject detail = details.get(0).getAsJsonObject();
            int result = detail.has("result") ? detail.get("result").getAsInt() : 0;
            if (result != 1)
                throw new IOException("Steam Workshop details request failed for item " + workshopItemId + " with result " + result);

            String timeUpdated = getString(detail, "time_updated");
            if (timeUpdated == null || timeUpdated.isEmpty())
                throw new IOException("Steam Workshop details for item " + workshopItemId + " did not contain time_updated.");

            return new SteamWorkshopItemDetails(
                    getString(detail, "publishedfileid"),
                    getString(detail, "title"),
                    timeUpdated,
                    getString(detail, "file_url"));
        } finally {
            if (body != null) body.close();
            response.close();
        }
    }

    public File getWorkshopItemDir(String workshopAppId, String workshopItemId) {
        return new File(destDir + "/steamapps/workshop/content/" + workshopAppId + "/" + workshopItemId);
    }

    static String buildWorkshopItemCommand(String login, String workshopAppId, String workshopItemId) {
        return STEAMCMD_WORKSHOP_COMMAND
                .replace("{LOGIN}", login)
                .replace("{WORKSHOP_APP}", workshopAppId)
                .replace("{WORKSHOP_ITEM}", workshopItemId);
    }

    private String getLogin() throws NotLoadedException, YamlReaderException, YamlWriterException, IOException, IllegalKeyException, DuplicateKeyException, IllegalListException {
        String login = new UpdaterConfig().server_steamcmd_login.asString();
        if (login == null || login.isEmpty()) login = "anonymous";
        return login;
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull())
            return null;
        return object.get(key).getAsString();
    }

    public String getResolutionForError(String error) {
        for (Map.Entry<String, String> entry : errorResolutions.entrySet())
            if (error.contains(entry.getKey())) return entry.getValue();
        return "Unknown. :(";
    }

    public static class SteamWorkshopItemDetails {
        private final String publishedFileId;
        private final String title;
        private final String timeUpdated;
        private final String fileUrl;

        public SteamWorkshopItemDetails(String publishedFileId, String title, String timeUpdated, String fileUrl) {
            this.publishedFileId = publishedFileId;
            this.title = title;
            this.timeUpdated = timeUpdated;
            this.fileUrl = fileUrl;
        }

        public String getPublishedFileId() {
            return publishedFileId;
        }

        public String getTitle() {
            return title;
        }

        public String getTimeUpdated() {
            return timeUpdated;
        }

        public String getFileUrl() {
            return fileUrl;
        }
    }

}
