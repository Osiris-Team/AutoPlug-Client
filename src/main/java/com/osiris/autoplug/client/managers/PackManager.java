/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.UtilsFile;
import com.osiris.autoplug.client.utils.UtilsMinecraft;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class PackManager {
    public File systemPacksDir = new File(GD.WORKING_DIR + "/autoplug/system/packs");
    public List<Pack> installedPacks = new ArrayList<>();

    public PackManager() {
        systemPacksDir.mkdirs();
        for (File packDir : systemPacksDir.listFiles()) {
            if (packDir.isDirectory()) {
                File packJson = new File(packDir + "/autoplug-pack.json");
                if (packJson.exists()) {
                    try {
                        installedPacks.add(load(packJson));
                    } catch (IOException e) {
                        AL.warn(e);
                    }
                }
            }
        }
    }

    public void save(File file, Pack pack) throws IOException {
        file.createNewFile();
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        Files.write(file.toPath(), gson.toJson(pack).getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
    }

    public Pack load(File file) throws IOException {
        Gson gson = new GsonBuilder()
                .create();
        return gson.fromJson(new String(Files.readAllBytes(file.toPath())), Pack.class);
    }

    /**
     * Copies/Installs the contents of the provided folder
     * into /autoplug/system/packs and handles pack name conflicts.
     *
     * @param packDir the pack directory containing the plugins/mods to install.
     */
    public void installPack(File packDir) throws IOException {
        File[] files = packDir.listFiles();
        Pack pack = null;
        for (File file : files) {
            if (file.getName().equals("autoplug-pack.json")) {
                pack = load(file);
            }
        }
        if (pack == null) {
            pack = new Pack();
            pack.name = getDefaultPackName();
            int modsCount = new UtilsMinecraft().getMods(packDir).size();
            int pluginsCount = new UtilsMinecraft().getMods(packDir).size();
            pack.type = (modsCount > pluginsCount ? Pack.Type.MINECRAFT_MODS : Pack.Type.MINECRAFT_PLUGINS);
            save(new File(packDir + "/autoplug-pack.json"), pack);
        }
        int i = 0;
        while (isPackNameTaken(pack.name)) {
            pack.name = pack.name + "-" + i;
            i++;
        }
        save(new File(packDir + "/autoplug-pack.json"), pack);
        new UtilsFile().copyDirectoryContent(packDir, new File(systemPacksDir + "/" + pack.name));
    }

    public boolean isPackNameTaken(String name) {
        for (Pack installedPack : installedPacks) {
            if (installedPack.name.equals(name)) return true;
        }
        return false;
    }

    public String getDefaultPackName() {
        int i = 0;
        for (Pack installedPack : installedPacks) {
            if (installedPack.name.startsWith("default-")) {
                i++;
            }
        }
        return "default-" + i;
    }
}
