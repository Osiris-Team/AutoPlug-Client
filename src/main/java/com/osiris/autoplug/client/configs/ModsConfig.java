/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class ModsConfig extends MyYaml {
    public YamlSection keep_removed;

    public ModsConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, YamlWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/mods.yml");

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
                        "                                /___/ Mods-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions here: https://autoplug.one/installer\n" +
                        "If there are any questions or you just want chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################\n" +
                        "This file contains detailed information about your installed mods. It is fetched from each mods config file (located inside their jars).\n" +
                        "The data gets refreshed before performing an update-check. To exclude a mod from the check set exclude=true.\n" +
                        "If a name/author/version is missing, the mod gets excluded automatically.\n" +
                        "If there are mods that weren't found by the search-algorithm, you can add an id (spigot or bukkit) and a custom link (optional & must be a static link to the latest mod jar).\n" +
                        "modrinth-id: Is the 'Project-ID' and can be found on the mods modrinth site inside of the 'About' box, under 'Technical Information' at the bottom left.\n" +
                        "curseforge-id: Is also called 'Project-ID' and can be found on the mods curseforge site inside of the 'About' box at the right.\n" +
                        "ignore-content-type: If true, does not check if the downloaded file is of type jar or zip, and downloads it anyway.\n" +
                        "force-latest: If true, does not search for updates compatible with this Minecraft version and simply picks the latest release.\n" +
                        "custom-check-url: #### Must link to a json file which contains a list/array of plugin versions where each item/object contains specific keys for version (\"version_number\", \"version\") and download URL (\"download_url\", \"download\", \"file\", \"download_file\").\n" +
                        "custom-download-url: must be a static url to the mods latest jar file.\n" +
                        "alternatives.github.repo-name: Example: 'EssentialsX/Essentials' (can be found in its url: https://github.com/EssentialsX/Essentials)\n" +
                        "alternatives.github.asset-name: Example: 'EssentialsX' (wrong: 'EssentialsX-1.7.23.jar', we discard the version info).\n" +
                        "alternatives.jenkins.project-url: Example: 'https://ci.ender.zone/job/EssentialsX/'\n" +
                        "alternatives.jenkins.artifact-name: Example: 'EssentialsX' (wrong: 'EssentialsX-1.7.23.jar', we discard the version info).\n" +
                        "alternatives.jenkins.build-id: The currently installed build identifier. Don't touch this.\n" +
                        "If a modrinth-id is not given, AutoPlug will try and find the matching id by using its unique search-algorithm (if it succeeds the modrinth-id gets set, else it stays 0).\n" +
                        "If both (bukkit and modrinth) ids are provided, the modrinth-id will be used.\n" +
                        "The configuration for uninstalled mods wont be removed from this file, but they are automatically excluded from future checks (the exclude value is ignored).\n" +
                        "If multiple authors are provided, only the first author will be used by the search-algorithm.\n" +
                        "Note: Remember, that the values for exclude, version and author get overwritten if new data is available.\n");

        keep_removed = put(name, "general", "keep-removed").setDefValues("true")
                .setComments("Keep the mods entry in this file even after its removal/uninstallation?");

        save();
        unlockFile();
    }


    @Override
    public Yaml validateValues() {
        return this;
    }
}
