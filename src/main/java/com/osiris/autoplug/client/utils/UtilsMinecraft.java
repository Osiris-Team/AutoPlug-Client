/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import org.jetbrains.annotations.NotNull;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UtilsMinecraft {

    public String getInstalledVersion() {
        return getInstalledVersion(GD.SERVER_JAR);
    }

    /**
     * Returns the version string like "1.18.1" from the
     * version.json that is located in every server jar.
     * Returns null if that wasn't possible.
     */
    public String getInstalledVersion(File serverJar) {
        String version = null;
        if (serverJar == null || !serverJar.exists()) return null;
        try {
            ZipFile zipFile = new ZipFile(serverJar);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            boolean found = false;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String fileName = entry.getName();
                if (fileName.equals("version.json")) { // Support for regular mc servers
                    found = true;
                    version = JsonParser.parseReader(new InputStreamReader(zipFile.getInputStream(entry))).getAsJsonObject().get("id").getAsString();
                } else if (fileName.equals("install.properties")) { // Support for mc paper servers
                    found = true;
                    Properties p = new Properties();
                    p.load(zipFile.getInputStream(entry));
                    version = p.getProperty("version");
                    if (version == null) version = p.getProperty("game-version");
                }
                if (found) break;
            }
        } catch (Exception e) {
            AL.warn(e);
        }
        return version;
    }

    @NotNull
    public List<MinecraftPlugin> getPlugins(File dir) throws FileNotFoundException {
        Objects.requireNonNull(dir);
        if (!dir.exists()) throw new FileNotFoundException("Directory does not exist: " + dir);
        List<MinecraftPlugin> plugins = new ArrayList<>();
        for (File jar :
                dir.listFiles()) {
            if (!jar.getName().endsWith(".jar") || jar.isDirectory()) continue;

            try {
                ZipFile zipFile = new ZipFile(jar);
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                boolean found = false;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String fileName = entry.getName();
                    if (fileName.equals("plugin.yml") || fileName.equals("bungee.yml")) {
                        found = true;
                        final Yaml ymlConfig = new Yaml(zipFile.getInputStream(entry), null);
                        ymlConfig.load();

                        String name = ymlConfig.put("name").asString();
                        //if (name==null || name.isEmpty()){ // In this case use the jars name as name
                        //    name = jar.getName();
                        //} // Don't do this, because the jars name contains its version and generally it wouldn't be nice
                        YamlSection version = ymlConfig.put("version");
                        YamlSection authorRaw = ymlConfig.put("author");
                        YamlSection authorsRaw = ymlConfig.put("authors");

                        String author = null;
                        if (!authorRaw.getValues().isEmpty())
                            author = authorRaw.asString();
                        else
                            author = authorsRaw.asString(); // Returns only the first author

                        // Why this is done? Because each plugin.yml file stores its authors list differently (Array or List, or numbers idk, or some other stuff...)
                        // and all we want is just a simple list. This causes errors.
                        // We get the list as a String, remove all "[]" brackets and " "(spaces) so we get a list of names only separated by commas
                        // That is then sliced into a list.
                        // Before: [name1, name2]
                        // After: name1,name2
                        if (author != null) author = Arrays.asList(
                                        author.replaceAll("[\\[\\]]", "")
                                                .split(","))
                                .get(0);

                        // Also check for ids in the plugin.yml
                        int spigotId = 0;
                        int bukkitId = 0;
                        YamlSection mSpigotId = ymlConfig.get("spigot-id");
                        YamlSection mBukkitId = ymlConfig.get("bukkit-id");
                        if (mSpigotId != null && mSpigotId.asString() != null) spigotId = mSpigotId.asInt();
                        if (mBukkitId != null && mBukkitId.asString() != null) bukkitId = mBukkitId.asInt();

                        plugins.add(new MinecraftPlugin(jar.getPath(), name, version.asString(), author, spigotId, bukkitId, null));
                    } else if (fileName.equals("velocity-plugin.json")) {
                        found = true;
                        JsonObject jsonConfig = JsonParser.parseReader(new InputStreamReader(zipFile.getInputStream(entry))).getAsJsonObject();

                        String name = jsonConfig.get("name").getAsString();
                        //if (name==null || name.isEmpty()){ // In this case use the jars name as name
                        //    name = jar.getName();
                        //} // Don't do this, because the jars name contains its version and generally it wouldn't be nice
                        String version = jsonConfig.get("version").getAsString();
                        JsonElement authorRaw = jsonConfig.get("author");
                        JsonElement authorsRaw = jsonConfig.get("authors");

                        String author = null;
                        if (authorRaw != null && !authorRaw.isJsonNull())
                            author = authorRaw.getAsString();
                        else
                            author = authorsRaw.getAsJsonArray().get(0).getAsString(); // Returns only the first author

                        // Also check for ids in the plugin.yml
                        int spigotId = 0;
                        int bukkitId = 0;
                        JsonElement mSpigotId = jsonConfig.get("spigot-id");
                        JsonElement mBukkitId = jsonConfig.get("bukkit-id");
                        if (mSpigotId != null && !mSpigotId.isJsonNull()) spigotId = mSpigotId.getAsInt();
                        if (mBukkitId != null && !mBukkitId.isJsonNull()) bukkitId = mBukkitId.getAsInt();

                        plugins.add(new MinecraftPlugin(jar.getPath(), name, version, author, spigotId, bukkitId, null));
                    }
                    if (found) break;
                }
            } catch (Exception e) {
                AL.warn("Failed to get details of " + jar.getName(), e);
            }
        }
        return plugins;
    }

    @NotNull
    public List<MinecraftMod> getMods(File dir) throws FileNotFoundException {
        Objects.requireNonNull(dir);
        if (!dir.exists()) throw new FileNotFoundException("Directory does not exist: " + dir);
        List<MinecraftMod> mods = new ArrayList<>();
        for (File jar :
                dir.listFiles()) {
            if (!jar.getName().endsWith(".jar") || jar.isDirectory()) continue;

            try {
                ZipFile zipFile = new ZipFile(jar);
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                boolean found = false;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String fileName = entry.getName();
                    if (fileName.equals("mods.toml")) { // Forge mod
                        found = true;
                        TomlParseResult result = Toml.parse(zipFile.getInputStream(entry));
                        //result.errors().forEach(error -> System.err.println(error.toString())); // Ignore errors
                        TomlTable table = result.getArray("mods").getTable(0);
                        String name = null;
                        try {
                            name = table.getString("displayName");
                        } catch (Exception e) {
                        }
                        String author = null;
                        try {
                            author = table.getString("authors"); // TODO find out how people separate multiple authors here
                        } catch (Exception e) {
                        }
                        if (author == null) try {
                            author = table.getString("author");
                        } catch (Exception e) {
                        }
                        String version = null;
                        try {
                            version = table.getString("version");
                        } catch (Exception e) {
                        }
                        String bukkitId = null;
                        try {
                            bukkitId = table.getString("modId");
                        } catch (Exception e) {
                        }
                        mods.add(new MinecraftMod(jar.getPath(), name, version, author, null, bukkitId, null));
                    } else if (fileName.equals("fabric.mod.json")) { // Fabric mod
                        found = true;
                        JsonObject obj = JsonParser.parseReader(new InputStreamReader(zipFile.getInputStream(entry))).getAsJsonObject();
                        String name = obj.get("name").getAsString();
                        //if (name==null || name.isEmpty()){ // In this case use the jars name as name
                        //    name = jar.getName();
                        //} // Don't do this, because the jars name contains its version and generally it wouldn't be nice
                        String version = obj.get("version").getAsString();
                        JsonElement authorRaw = obj.get("author");
                        JsonElement authorsRaw = obj.get("authors");

                        String author = null;
                        if (authorRaw != null && !authorRaw.isJsonNull())
                            author = authorRaw.getAsString();
                        else
                            author = authorsRaw.getAsJsonArray().get(0).getAsString(); // Returns only the first author

                        // Also check for ids in the config
                        String modrinthId = obj.get("id").getAsString();
                        mods.add(new MinecraftMod(jar.getPath(), name, version, author, modrinthId, null, null));
                    }
                    if (found) break;
                }
            } catch (Exception e) {
                AL.warn("Failed to get details of " + jar.getName(), e);
            }
        }
        return mods;
    }

}
