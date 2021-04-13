/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.autoplug.client.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.updater.plugins.Plugin;
import com.osiris.autoplug.client.updater.plugins.PluginManager;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;

public class PluginsConfig extends DreamYaml {
    private final List<DetailedPlugin> detailedPlugins;

    public DYModule keep_removed;

    public PluginsConfig() {
        super(System.getProperty("user.dir")+"/autoplug-plugins-config.yml");
        this.detailedPlugins = new ArrayList<>();
        try{
            load();
            String name = getFileNameWithoutExt();
            add(name).setComment(
                    "#######################################################################################################################\n" +
                            "    ___       __       ___  __\n" +
                            "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                            "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                            " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                            "                                /___/ Plugins-Config\n" +
                            "Thank you for using AutoPlug!\n" +
                            "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                            "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                            "\n" +
                            "#######################################################################################################################\n" +
                            "This file contains detailed information about your installed plugins. It is fetched from each plugins 'plugin.yml' file (located inside its jar).\n" +
                            "The data gets refreshed before performing an update-check. To exclude a plugin from the check set exclude=true.\n" +
                            "If a name/author/version is missing, the plugin gets excluded automatically.\n" +
                            "You can add extra information by defining an id (spigot or bukkit) and a custom link (optional & must be a static link to the latest plugin jar).\n" +
                            "The spigot-id of a plugin, can be found directly in the url. Example:\n" +
                            "Url: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                            "The examples spigot-id is therefore 78414. For the bukkit-id (or project-id) you need to visit the plugins bukkit site and read it from the box at the right.\n" +
                            "If a spigot-id is not given, AutoPlug will try and find the matching id by using its unique search-algorithm (if it successes the spigot-id gets set, else it stays 0).\n" +
                            "If both (bukkit and spigot) ids are provided, the spigot-id will be used.\n" +
                            "The configuration for uninstalled plugins wont be removed from this file, but they are automatically excluded from future checks (the exclude value is ignored).\n" +
                            "If multiple authors are provided, only the first author will be used by the search-algorithm.\n" +
                            "Note: Remember, that the values for exclude, version and author get overwritten if new data is available.\n" +
                            "Note for plugin devs: You can add your spigot/bukkit-id to your plugin.yml file. For more information visit "+GD.OFFICIAL_WEBSITE+"faq");

            keep_removed = add(name, "general", "keep-removed").setDefValue("true").setComment("Keep the plugins entry in this file even after its removal/uninstallation?");

            PluginManager man = new PluginManager();
            List<Plugin> pls = man.getPlugins();
            if (!pls.isEmpty())
            for (Plugin pl :
                    pls) {

                final String plName = pl.getName();

                DYModule exclude       = add(name, plName,"exclude").setDefValue("false"); // Check this plugin?
                DYModule version       = add(name, plName,"version").setDefValue(pl.getVersion());
                DYModule latestVersion = add(name, plName,"latest-version");
                DYModule authors       = add(name, plName,"author").setDefValue(pl.getAuthor());
                DYModule spigotId      = add(name, plName,"spigot-id").setDefValue("0");
                //DYModule songodaId = new DYModule(config, getModules(), name, plName,+".songoda-id", 0); // TODO WORK_IN_PROGRESS
                DYModule bukkitId      = add(name, plName,"bukkit-id").setDefValue("0");
                DYModule customLink    = add(name, plName,"c-link");

                if ((pl.getVersion()==null || pl.getVersion().isEmpty()) ||
                        (pl.getAuthor()==null || pl.getAuthor().isEmpty())){
                    exclude.setValue("true");
                    AL.warn("Plugin "+pl.getName()+" is missing critical information and was excluded.");
                }

                if (!exclude.asBoolean())
                    detailedPlugins.add(
                            new DetailedPlugin(
                                    pl.getInstallationPath(),
                                    pl.getName(), pl.getVersion(),
                                    pl.getAuthor(), spigotId.asInt(),
                                    bukkitId.asInt(), customLink.asString()
                                ));
            }

            if (keep_removed.asBoolean())
                save();
            else
                save(true); // This overwrites the file and removes everything else that wasn't added via the add method before.

        }
        catch (DuplicateKeyException e){
            AL.error("Duplicate plugin in /plugins! Please remove and restart AutoPlug.", e);
        }
        catch (Exception e) {
            AL.error(e);
        }
    }

    public List<DetailedPlugin> getDetailedPlugins() {
        return detailedPlugins;
    }
}
