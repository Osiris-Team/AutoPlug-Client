/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DayZWorkshopModUpdater {
    public static final String DAYZ_SERVER_APP_ID = "223350";
    public static final String DAYZ_WORKSHOP_APP_ID = "221100";
    private static final Pattern PUBLISHED_ID_PATTERN = Pattern.compile("(?m)^\\s*publishedid\\s*=\\s*\"?([0-9]+)\"?\\s*;?\\s*$");
    private static final Pattern NAME_PATTERN = Pattern.compile("(?m)^\\s*name\\s*=\\s*\"?([^\";]+)\"?\\s*;?\\s*$");

    private final File serverDir;
    private final File workshopDownloadDir;

    public DayZWorkshopModUpdater(File serverDir, File workshopDownloadDir) {
        this.serverDir = serverDir;
        this.workshopDownloadDir = workshopDownloadDir;
    }

    public File getWorkshopDownloadDir() {
        return workshopDownloadDir;
    }

    public List<DayZWorkshopMod> findInstalledMods() throws IOException {
        List<DayZWorkshopMod> mods = new ArrayList<>();
        File[] children = serverDir.listFiles(File::isDirectory);
        if (children == null) return mods;

        for (File dir : children) {
            File metaCpp = findMetaCpp(dir);
            if (metaCpp == null) continue;
            DayZWorkshopMod mod = parseMetaCpp(metaCpp, dir);
            mods.add(mod);
        }
        return mods;
    }

    public DayZWorkshopMod parseMetaCpp(File metaCpp, File modDir) throws IOException {
        String content = new String(Files.readAllBytes(metaCpp.toPath()), StandardCharsets.UTF_8);
        String publishedId = matchRequired(PUBLISHED_ID_PATTERN, content, "publishedid", metaCpp);
        String name = matchOptional(NAME_PATTERN, content);
        if (name == null || name.trim().isEmpty()) name = modDir.getName();
        return new DayZWorkshopMod(name.trim(), publishedId, modDir);
    }

    public List<String> getWorkshopIds(List<DayZWorkshopMod> mods) {
        List<String> ids = new ArrayList<>();
        for (DayZWorkshopMod mod : mods) {
            ids.add(mod.publishedId);
        }
        return ids;
    }

    public int installDownloadedMods(List<DayZWorkshopMod> mods) throws IOException {
        int installed = 0;
        for (DayZWorkshopMod mod : mods) {
            File downloadedModDir = getDownloadedWorkshopContentDir(mod);
            if (!downloadedModDir.isDirectory()) {
                throw new IOException("Downloaded DayZ Workshop mod was not found at " + downloadedModDir.getAbsolutePath());
            }
            replaceDirectory(downloadedModDir, mod.directory);
            copyKeysIfPresent(mod.directory);
            installed++;
        }
        return installed;
    }

    public File getDownloadedWorkshopContentDir(DayZWorkshopMod mod) {
        return new File(workshopDownloadDir + "/steamapps/workshop/content/" + DAYZ_WORKSHOP_APP_ID + "/" + mod.publishedId);
    }

    private File findMetaCpp(File dir) {
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().equalsIgnoreCase("meta.cpp"));
        if (files == null || files.length == 0) return null;
        return files[0];
    }

    private void replaceDirectory(File sourceDir, File targetDir) throws IOException {
        File backupDir = new File(targetDir.getParentFile(), targetDir.getName() + ".autoplug-backup");
        deleteDirectory(backupDir);

        boolean hadTarget = targetDir.exists();
        if (hadTarget) moveDirectory(targetDir, backupDir);

        try {
            copyDirectory(sourceDir, targetDir);
            if (hadTarget) deleteDirectory(backupDir);
        } catch (IOException e) {
            deleteDirectory(targetDir);
            if (backupDir.exists()) moveDirectory(backupDir, targetDir);
            throw e;
        }
    }

    private void copyKeysIfPresent(File modDir) throws IOException {
        File modKeysDir = new File(modDir, "keys");
        File[] keys = modKeysDir.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".bikey"));
        if (keys == null || keys.length == 0) return;

        File serverKeysDir = new File(serverDir, "keys");
        serverKeysDir.mkdirs();
        for (File key : keys) {
            Files.copy(key.toPath(), new File(serverKeysDir, key.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyDirectory(File sourceDir, File targetDir) throws IOException {
        Path sourcePath = sourceDir.toPath();
        Path targetPath = targetDir.toPath();
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, targetPath.resolve(sourcePath.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(File dir) throws IOException {
        if (!dir.exists()) return;
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void moveDirectory(File sourceDir, File targetDir) throws IOException {
        Files.move(sourceDir.toPath(), targetDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private String matchRequired(Pattern pattern, String content, String fieldName, File file) throws IOException {
        String value = matchOptional(pattern, content);
        if (value == null) {
            throw new IOException("Missing " + fieldName + " in " + file.getAbsolutePath());
        }
        return value;
    }

    private String matchOptional(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) return null;
        return matcher.group(1);
    }

    public static class DayZWorkshopMod {
        public final String name;
        public final String publishedId;
        public final File directory;

        public DayZWorkshopMod(String name, String publishedId, File directory) {
            this.name = name;
            this.publishedId = publishedId;
            this.directory = directory;
        }
    }
}
