/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import net.lingala.zip4j.ZipFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PluginManager {

    @NotNull
    public List<DetailedPlugin> getPlugins() {
        List<DetailedPlugin> plugins = new ArrayList<>();

        FileManager fm = new FileManager();

        // Get a list of all jar files in the /plugins dir
        List<File> plJarFiles = fm.getAllPlugins();

        // Location where each plugin.yml file will be extracted to
        File ymlFile = new File(System.getProperty("user.dir") + "/autoplug/system/plugin.yml");
        byte[] buffer = new byte[1024];

        /*
        1. Extract information from each jars "plugin.yml" file
        2. Convert into a Plugin.class
        3. Add the Plugin.class to the plugins list
         */
        if (!plJarFiles.isEmpty())
            for (File jar :
                    plJarFiles) {
                FileInputStream fis = null;
                ZipInputStream zis = null;
                ZipEntry ze = null;
                try {
                    // Clean up old
                    if (ymlFile.exists()) {
                        ymlFile.delete();
                        ymlFile.createNewFile();
                    }

                    fis = new FileInputStream(jar);
                    zis = new ZipInputStream(fis);
                    ze = zis.getNextEntry();
                    boolean found = false;
                    while (ze != null) {
                        String fileName = ze.getName();
                        if (!found && (fileName.equals("plugin.yml") || fileName.equals("bungee.yml"))) {
                            found = true;
                            // Extract this plugin.yml file
                            FileOutputStream fos = new FileOutputStream(ymlFile);
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                            fos.close();
                            zis.closeEntry();

                            // Load the plugin.yml and get its details
                            final Yaml ymlConfig = new Yaml(ymlFile);
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

                            plugins.add(new DetailedPlugin(jar.getPath(), name, version.asString(), author, spigotId, bukkitId, null));
                        }
                        // Get next file in zip
                        zis.closeEntry();
                        ze = zis.getNextEntry();
                    } // Loop end
                    // Close last ZipEntry
                    zis.closeEntry();
                    zis.close();
                    fis.close();

                } catch (Exception e) {
                    AL.warn("Failed to get plugin information for: " + jar.getName(), e);
                } finally {
                    try {
                        if (zis != null && ze != null)
                            zis.closeEntry();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        if (zis != null)
                            zis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        if (fis != null) fis.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        return plugins;
    }

    @NotNull
    @Deprecated
    private File extractPluginYmlFile(@NotNull File jar) throws Exception {
        // A jar file is actually a zip file, thats why we can use this method
        ZipFile zip = new ZipFile(jar);

        // The plugin yml file we will extract from the jar
        String path = System.getProperty("user.dir") + "/autoplug/system";
        File pluginYml = new File(path);
        if (pluginYml.exists()) {
            pluginYml.delete();
        }

        // This extracts the plugin yml file
        zip.extractFile("plugin.yml", path);

        return pluginYml;
    }

}
