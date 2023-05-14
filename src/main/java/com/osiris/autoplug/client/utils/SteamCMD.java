/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.tasks.updater.TaskDownload;
import com.osiris.autoplug.client.utils.io.AsyncReader;
import com.osiris.autoplug.client.utils.tasks.MyBThreadManager;
import com.osiris.autoplug.client.utils.terminal.AsyncTerminal;
import com.osiris.betterthread.BThreadManager;
import com.osiris.dyml.exceptions.*;
import com.osiris.jlib.logger.AL;
import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.osiris.jprocesses2.util.OS.isMac;
import static com.osiris.jprocesses2.util.OS.isWindows;

@SuppressWarnings({"WeakerAccess", "unused"})
public class SteamCMD {

    private final String steamcmdArchive = "steamcmd" + (isWindows ? ".zip" : isMac ? "_osx.tar.gz" : "_linux.tar.gz");
    private final String steamcmdExtension = isWindows ? ".exe" : ".sh";
    private final String steamcmdExecutable = "steamcmd" + steamcmdExtension;
    // removed "{APP} validate" because it takes ages, doesn't work?
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

            String login = new UpdaterConfig().server_steamcmd_login.asString();
            if (login == null || login.isEmpty()) login = "anonymous";

            File gameInstallDir = new File(dirSteamServersDownloads + "/" + appId);
            gameInstallDir.mkdirs();
            List<String> logLines = new ArrayList<>();
            List<String> logErrLines = new ArrayList<>();
            AtomicBoolean isFinished = new AtomicBoolean(false);
            AtomicBoolean isSuccess = new AtomicBoolean(true);
            AtomicBoolean isLoginSuccess = new AtomicBoolean(false);
            // Doesn't work when directly executing via ProcessBuilder thats why we execute it from an
            // actual terminal
            AsyncTerminal terminal = new AsyncTerminal(destDir, line -> { // Without a reader it seems to never finish
                onLog.accept(line);
                logLines.add(line);
                if (line.toLowerCase().contains("logged in"))
                    isLoginSuccess.set(true);
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
                    destExe.getAbsolutePath());
            Thread.sleep(5000);
            // private final String steamcmdCommand = "+login {LOGIN} +force_install_dir \"{DESTINATION}\" +app_update {APP} +quit";
            while (!isLoginSuccess.get()) {
                terminal.sendCommands("login " + login);
                Thread.sleep(10000);
                if (isLoginSuccess.get()) break;
                try {
                    MyBThreadManager.lastCreatedPrinter.get().interrupt();
                } catch (Exception e) {
                }
                AL.info("Paused task printing, login into Steam timed out (10 seconds).");
                AL.info("Steam Guard key seems to be needed (check your email)!");
                AL.info("Insert it below and press enter:");
                String steamGuardKey = new Scanner(System.in).nextLine();
                AL.info("Steam Guard key will be provided to SteamCMD.");
                terminal.sendCommands(steamGuardKey);
                Thread.sleep(10000);
                if (isLoginSuccess.get()) break;
            }
            terminal.sendCommands("force_install_dir \"" + gameInstallDir.getAbsolutePath() + "\"",
                    "app_update " + appId,
                    "quit");

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

    public String getResolutionForError(String error) {
        for (Map.Entry<String, String> entry : errorResolutions.entrySet())
            if (error.contains(entry.getKey())) return entry.getValue();
        return "Unknown. :(";
    }

}
