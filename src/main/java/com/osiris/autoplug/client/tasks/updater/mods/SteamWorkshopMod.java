/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SteamWorkshopMod {
    private final File directory;
    private final String name;
    private final String publishedId;

    public SteamWorkshopMod(File directory, String name, String publishedId) {
        this.directory = directory;
        this.name = name;
        this.publishedId = publishedId;
    }

    public File getDirectory() {
        return directory;
    }

    public String getName() {
        return name;
    }

    public String getPublishedId() {
        return publishedId;
    }

    @NotNull
    public static List<SteamWorkshopMod> findIn(File dir) throws IOException {
        if (!dir.exists()) throw new FileNotFoundException("Directory does not exist: " + dir);
        List<SteamWorkshopMod> mods = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return mods;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (!file.isDirectory()) continue;
            File metaFile = new File(file, "meta.cpp");
            if (metaFile.exists())
                mods.add(readFromMeta(file, metaFile));
        }
        return mods;
    }

    static SteamWorkshopMod readFromMeta(File modDir, File metaFile) throws IOException {
        String name = modDir.getName();
        String publishedId = null;
        for (String line : Files.readAllLines(metaFile.toPath(), StandardCharsets.UTF_8)) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("//")) continue;

            int equalsIndex = trimmedLine.indexOf('=');
            if (equalsIndex < 0) continue;

            String key = trimmedLine.substring(0, equalsIndex).trim();
            String value = trimmedLine.substring(equalsIndex + 1).trim();
            int semicolonIndex = value.indexOf(';');
            if (semicolonIndex < 0) continue;
            value = value.substring(0, semicolonIndex).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                value = value.substring(1, value.length() - 1);

            if (key.equals("name")) name = value;
            if (key.equals("publishedid")) publishedId = value;
        }

        if (publishedId == null || !publishedId.matches("\\d+"))
            throw new IOException("Failed to read publishedid from " + metaFile);

        return new SteamWorkshopMod(modDir, name, publishedId);
    }
}
