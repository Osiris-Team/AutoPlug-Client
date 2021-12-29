/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;


import com.osiris.autoplug.client.tasks.updater.search.GithubSearch;
import com.osiris.autoplug.client.tasks.updater.search.JenkinsSearch;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.tasks.updater.search.bukkit.BukkitSearchById;
import com.osiris.autoplug.client.tasks.updater.search.spigot.SpigotSearchByAuthor;
import com.osiris.autoplug.client.tasks.updater.search.spigot.SpigotSearchById;
import com.osiris.autoplug.client.tasks.updater.search.spigot.SpigotSearchByName;

public class PluginUpdateFinder {

    /**
     * If the spigot/bukkit id is not given this type of search
     * based on the plugins name and author will be executed.
     */
    public SearchResult unknownSearch(DetailedPlugin plugin) {

        // Before passing over remove everything except numbers and dots
        plugin.setVersion(plugin.getVersion().replaceAll("[^0-9.]", ""));

        // Before passing over remove everything except words and numbers
        plugin.setAuthor(plugin.getAuthor().replaceAll("[^\\w]", ""));

        // Do spigot search by name
        SearchResult sr = new SpigotSearchByName().search(plugin);

        if (sr == null || sr.getResultCode() == 2 || sr.getResultCode() == 3) {
            //Couldn't find author or resource via first search
            //Do alternative search:
            sr = new SpigotSearchByAuthor().search(plugin);
        }

        plugin.setPremium(sr.isPremium);
        sr.plugin = plugin;
        return sr;
    }

    public SearchResult searchBySpigotId(DetailedPlugin plugin) {
        SearchResult rs = new SpigotSearchById().search(plugin);
        plugin.setPremium(rs.isPremium);
        rs.plugin = plugin;
        return rs;
    }

    public SearchResult searchByBukkitId(DetailedPlugin plugin) {
        SearchResult rs = new BukkitSearchById().search(plugin);
        plugin.setPremium(rs.isPremium);
        rs.plugin = plugin;
        return rs;
    }

    public SearchResult searchByGithubUrl(DetailedPlugin plugin) {
        SearchResult sr = new GithubSearch().search(plugin.getGithubRepoName(), plugin.getGithubAssetName(), plugin.getVersion());
        plugin.setPremium(sr.isPremium);
        sr.plugin = plugin;
        return sr;
    }

    public SearchResult searchByJenkinsUrl(DetailedPlugin plugin) {
        SearchResult sr = new JenkinsSearch().search(plugin.getJenkinsProjectUrl(), plugin.getJenkinsArtifactName(), plugin.getJenkinsBuildId());
        plugin.setPremium(sr.isPremium);
        sr.plugin = plugin;
        return sr;
    }
}
