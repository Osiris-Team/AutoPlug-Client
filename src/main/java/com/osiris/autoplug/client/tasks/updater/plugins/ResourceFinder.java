/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;


import com.osiris.autoplug.client.tasks.updater.mods.CurseForgeAPI;
import com.osiris.autoplug.client.tasks.updater.mods.InstalledModLoader;
import com.osiris.autoplug.client.tasks.updater.mods.MinecraftMod;
import com.osiris.autoplug.client.tasks.updater.mods.ModrinthAPI;
import com.osiris.autoplug.client.tasks.updater.search.CustomCheckURL;
import com.osiris.autoplug.client.tasks.updater.search.GithubSearch;
import com.osiris.autoplug.client.tasks.updater.search.JenkinsSearch;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.tasks.updater.search.bukkit.BukkitSearchById;
import com.osiris.autoplug.client.tasks.updater.search.spigot.SpigotSearchByAuthor;
import com.osiris.autoplug.client.tasks.updater.search.spigot.SpigotSearchById;
import com.osiris.autoplug.client.tasks.updater.search.spigot.SpigotSearchByName;

import java.util.List;

public class ResourceFinder {

    /** Uses the same providers as the legacy updater, with a strict profile target and explicit loader. */
    public SearchResult findCompatibleProfileMod(InstalledModLoader loader, MinecraftMod mod, String mcVersion) {
        String modrinthId = mod.modrinthId;
        if (modrinthId == null && mod.curseforgeId != null && !mod.curseforgeId.matches("[0-9]+")) modrinthId = mod.curseforgeId;
        SearchResult primary = new ModrinthAPI().searchCompatible(List.of(loader.getProfileLoader()), modrinthId, mcVersion);
        primary.mod = mod;
        if (SearchResult.isMatchFound(primary)) return primary;
        if (mod.curseforgeId == null || !mod.curseforgeId.matches("[0-9]+")) return primary;
        SearchResult alternate = new CurseForgeAPI().searchCompatible(loader, mod, mcVersion);
        // A network error is unresolved, never proof that the asset is incompatible.
        if (SearchResult.isMatchFound(alternate)) return alternate;
        if (primary.type == SearchResult.Type.API_ERROR) return primary;
        return alternate;
    }

    public SearchResult findCompatibleProfilePlugin(List<String> loaders, MinecraftPlugin plugin, String mcVersion) {
        SearchResult result = new ModrinthAPI().searchCompatible(loaders, plugin.modrinthId, mcVersion);
        result.plugin = plugin;
        return result;
    }

    /**
     * If the spigot/bukkit id is not given this type of search
     * based on the plugins' name and author will be executed. <br>
     */
    public SearchResult findUnknownSpigotPlugin(MinecraftPlugin plugin) {
        // Do spigot search by name
        SearchResult sr = new SpigotSearchByName().search(plugin);

        SearchResult sr2 = null;
        if (!SearchResult.isMatchFound(sr)) {
            //Couldn't find author or resource via first search
            //Do alternative search:
            sr2 = new SpigotSearchByAuthor().search(plugin);
            sr2.similarPlugins.addAll(sr.similarPlugins);
            sr = sr2;
        }

        plugin.setPremium(sr.isPremium);
        sr.plugin = plugin;
        return sr;
    }

    /**
     * If the modrinth/bukkit id is not given this type of search
     * based on the mods' name and author will be executed.
     */
    public SearchResult findByModrinthOrCurseforge(InstalledModLoader modLoader, MinecraftMod mod, String mcVersion, boolean checkNameForModLoader) {
        // TODO actualy do search by name, since currently it still searches by id
        SearchResult sr = new ModrinthAPI().searchUpdateMod(modLoader, mod, mcVersion);

        if (sr == null || sr.type == SearchResult.Type.API_ERROR || sr.type == SearchResult.Type.RESOURCE_NOT_FOUND) {
            //Couldn't find author or resource via first search
            //Do alternative search:
            sr = new CurseForgeAPI().searchUpdate(modLoader, mod, mcVersion, checkNameForModLoader);
        }

        sr.mod = mod;
        return sr;
    }

    public SearchResult findPluginBySpigotId(MinecraftPlugin plugin) {
        SearchResult sr = new SpigotSearchById().search(plugin);
        plugin.setPremium(sr.isPremium);
        sr.plugin = plugin;
        return sr;
    }

    public SearchResult findPluginByBukkitId(MinecraftPlugin plugin) {
        SearchResult sr = new BukkitSearchById().search(plugin);
        plugin.setPremium(sr.isPremium);
        sr.plugin = plugin;
        return sr;
    }
    public SearchResult findPluginByModrinthId(List<String> loaders, MinecraftPlugin plugin, String mcVersion) {
        SearchResult sr = new ModrinthAPI().searchUpdatePlugin(loaders, plugin, mcVersion);
        sr.plugin = plugin;
        return sr;
    }

    public SearchResult findModByModrinthId(InstalledModLoader modLoader, MinecraftMod mod, String mcVersion) {
        SearchResult sr = new ModrinthAPI().searchUpdateMod(modLoader, mod, mcVersion);
        sr.mod = mod;
        return sr;
    }

    public SearchResult findModByCurseforgeId(InstalledModLoader modLoader, MinecraftMod mod, String mcVersion, boolean checkNameForModLoader) {
        SearchResult sr = new CurseForgeAPI().searchUpdate(modLoader, mod, mcVersion, checkNameForModLoader);
        sr.mod = mod;
        return sr;
    }

    public SearchResult findByGithubUrl(MinecraftMod mod) {
        SearchResult sr = new GithubSearch().search(mod.githubRepoName, mod.githubAssetName, mod.getVersion());
        sr.mod = mod;
        return sr;
    }

    public SearchResult findByJenkinsUrl(MinecraftMod mod) {
        SearchResult sr = new JenkinsSearch().search(mod.jenkinsProjectUrl, mod.jenkinsArtifactName, mod.jenkinsBuildId);
        sr.mod = mod;
        return sr;
    }

    public SearchResult findByGithubUrl(MinecraftPlugin plugin) {
        SearchResult sr = new GithubSearch().search(plugin.getGithubRepoName(), plugin.getGithubAssetName(), plugin.getVersion());
        plugin.setPremium(sr.isPremium);
        sr.plugin = plugin;
        return sr;
    }

    public SearchResult findByJenkinsUrl(MinecraftPlugin plugin) {
        SearchResult sr = new JenkinsSearch().search(plugin.getJenkinsProjectUrl(), plugin.getJenkinsArtifactName(), plugin.getJenkinsBuildId());
        plugin.setPremium(sr.isPremium);
        sr.plugin = plugin;
        return sr;
    }

    public SearchResult findByCustomCheckURL(MinecraftMod mod) {
        SearchResult sr = new CustomCheckURL().doCustomCheck(mod.customCheckURL, mod.getVersion());
        sr.mod = mod;
        return sr;
    }

    public SearchResult findByCustomCheckURL(MinecraftPlugin plugin) {
        SearchResult sr = new CustomCheckURL().doCustomCheck(plugin.customCheckURL, plugin.getVersion());
        sr.plugin = plugin;
        return sr;
    }
}
