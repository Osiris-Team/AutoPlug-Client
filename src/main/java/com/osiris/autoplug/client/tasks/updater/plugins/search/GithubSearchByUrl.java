/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.utils.StringComparator;
import com.osiris.autoplug.client.utils.UtilsVersion;
import com.osiris.autoplug.core.json.JsonTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GithubSearchByUrl {

    public SearchResult search(DetailedPlugin plugin) {
        String githubRepoName = plugin.getGithubRepoName();
        String githubAssetName = plugin.getGithubAssetName();

        Exception exception = null;
        byte resultCode = 0;
        String downloadUrl = null;
        String downloadType = ".jar";
        String latestVersion = null;
        try {
            JsonObject latestRelease = new JsonTools()
                    .getJsonObject("https://api.github.com/repos/" + githubRepoName + "/releases/latest");
            latestVersion = latestRelease.get("tag_name").getAsString();
            if (new UtilsVersion().compare(plugin.getVersion(), latestVersion)) {
                resultCode = 1;
                List<String> comparedNames = new ArrayList<>();
                for (JsonElement element :
                        latestRelease.getAsJsonArray("assets")) {
                    String name = element.getAsJsonObject().get("name").getAsString()
                            .replaceAll("\\d", "")
                            .replaceAll("[.]", "")
                            .replaceAll("[-]", ""); // Removes numbers, dots and hyphens
                    comparedNames.add(name);
                    if (StringComparator.similarity(name, githubAssetName)
                            >= 0.90) {
                        downloadUrl = element.getAsJsonObject().get("browser_download_url").getAsString();
                    }
                }
                if (downloadUrl == null)
                    throw new Exception("Failed to find asset with name similarity over 90%. Compared name '" + githubAssetName + "' with: " + Arrays.toString(comparedNames.toArray()));
            }
        } catch (Exception e) {
            exception = e;
            resultCode = 2;
        }

        SearchResult rs = new SearchResult(plugin, resultCode, latestVersion, downloadUrl, downloadType, null, null, false);
        rs.setException(exception);
        return rs;
    }
}
