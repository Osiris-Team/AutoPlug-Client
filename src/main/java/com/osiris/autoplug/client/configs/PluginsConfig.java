/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.utils.GD;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class PluginsConfig extends MyYaml {

    public YamlSection keep_removed;

    public PluginsConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/plugins.yml");

        addSingletonConfigFileEventListener(e -> {
        });

        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Plugins-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions here: https://autoplug.one/installer\n" +
                        "If there are any questions or you just want chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################\n" +
                        "\n" +
                        "\n" +
                        "IMPORTANT: Before changing a setting in this file, make sure to read its explanation below! This will save you and me a lot of time in some cases.\n" +
                        "\n" +
                        "\n" +
                        "This file contains detailed information about your installed plugins. It is fetched from each plugins 'plugin.yml' file (located inside their jars).\n" +
                        "Example configuration for the EssentialsX plugin with each setting explained:\n" +
                        "  Essentials: \n" +
                        "    exclude: false #### If a name/author/version is missing, the plugin gets excluded automatically\n " +
                        "    version: 1434 #### Gets fetched from 'plugin.yml' and refreshed after each check\n " +
                        "    latest-version: 1434 #### Gets refreshed after each check\n " +
                        "    author: Zenexer #### Gets fetched from 'plugin.yml' and refreshed after each check #### If multiple names are provided, only the first author will be used.\n" +
                        "    #### Note that only one id is necessary, provided both for demonstration purposes.\n" +
                        "    spigot-id: 871 #### Gets found by AutoPlugs' smart search algorithm and set in a check or can be set by you #### You can find it directly in the url. Example URLs id is 78414. Example URL: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n " +
                        "    bukkit-id: 93271 #### Gets found by AutoPlugs' smart search algorithm and set in a check or can be set by you #### Is the 'Project-ID' and can be found on the plugins bukkit site inside of the 'About' box at the right.\n " +
                        "    custom-check-url: #### Must link to a json file which contains a list/array of plugin versions where each item/object contains specific keys for version (\"version_number\", \"version\") and download URL (\"download_url\", \"download\", \"file\", \"download_file\").\n" +
                        "    custom-download-url: #### Must be a static url to the plugins latest jar file.\n" +
                        "    ignore-content-type: false #### When downloading a file the file host is asked for the file-type which must be .jar, when true this check is not performed.\n" +
                        "    force-update: false #### If true, downloads the update every time even if its already on the latest version. Do NOT enable, unless you have a really good reason to.\n" +
                        "    alternatives: #### below both alternatives are used for demonstration purposes, make sure to use only one)\n" +
                        "      github: \n" +
                        "        repo-name: EssentialsX/Essentials #### Provided by you #### Can be found in its url: https://github.com/EssentialsX/Essentials\n" +
                        "        asset-name: EssentialsX #### Provided by you #### Wrong: 'EssentialsX-1.7.23.jar', we discard the version information.\n" +
                        "      jenkins: \n" +
                        "        project-url: https://ci.ender.zone/job/EssentialsX/ #### Provided by you ### Note that each plugins jenkins url looks different.\n" +
                        "        artifact-name: EssentialsX #### Provided by you #### Wrong: 'EssentialsX-1.7.23.jar', we discard the version information.\n" +
                        "        build-id: 1434\n #### The currently installed build identifier. Don't touch this." +
                        "The configuration for uninstalled plugins wont be removed from this file, but they are automatically excluded from future checks (the exclude value is ignored).\n" +
                        "Note for plugin devs: You can add your spigot/bukkit-id to your plugin.yml file. For more information visit " + GD.OFFICIAL_WEBSITE + "faq/2\n");

        keep_removed = put(name, "general", "keep-removed").setDefValues("true")
                .setComments("Keep the plugins entry in this file even after its removal/uninstallation?");

        save();
        unlockFile();
    }


    @Override
    public Yaml validateValues() {
        return this;
    }
}
