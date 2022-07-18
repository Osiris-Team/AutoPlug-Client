/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.utils.UtilsVersion;
import com.osiris.autoplug.core.json.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GithubSearch {

    public SearchResult search(String githubRepoName, String githubAssetName, String version) {


        Exception exception = null;
        byte resultCode = 0;
        String downloadUrl = null;
        String downloadType = ".jar";
        String latestVersion = null;
        String fileName = null;
        try {
            JsonObject latestRelease = Json.fromUrlAsObject("https://api.github.com/repos/" + githubRepoName + "/releases/latest");
            latestVersion = latestRelease.get("tag_name").getAsString();
            if (latestVersion != null)
                latestVersion = latestVersion.replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots
            if (new UtilsVersion().compare(version, latestVersion)) {
                resultCode = 1;
                // Contains JsonObjects sorted by their asset-names lengths, from smallest to longest.
                // The following does that sorting.
                List<JsonObject> sortedArtifactObjects = new ArrayList<>();
                for (JsonElement e :
                        latestRelease.getAsJsonArray("assets")) {
                    JsonObject obj = e.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    if (sortedArtifactObjects.size() == 0) sortedArtifactObjects.add(obj);
                    else {
                        int finalIndex = 0;
                        boolean isSmaller = false;
                        for (int i = 0; i < sortedArtifactObjects.size(); i++) {
                            String n = sortedArtifactObjects.get(i).get("name").getAsString();
                            if (name.length() < n.length()) {
                                isSmaller = true;
                                finalIndex = i;
                                break;
                            }
                        }
                        if (!isSmaller) sortedArtifactObjects.add(obj);
                        else sortedArtifactObjects.add(finalIndex, obj);
                    }
                }

                // Find asset-name containing our provided asset-name
                for (JsonObject obj : sortedArtifactObjects) {
                    String n = obj.get("name").getAsString();
                    if (n.contains(githubAssetName)) {
                        fileName = n;
                        downloadUrl = obj.get("browser_download_url").getAsString();
                        break;
                    }
                }

                if (downloadUrl == null) {
                    List<String> names = new ArrayList<>();
                    for (JsonObject obj :
                            sortedArtifactObjects) {
                        String n = obj.get("name").getAsString();
                        names.add(n);
                    }
                    throw new Exception("Failed to find an asset-name containing '" + githubAssetName + "' inside of " + Arrays.toString(names.toArray()));
                }

            }
        } catch (Exception e) {
            exception = e;
            resultCode = 2;
        }

        SearchResult rs = new SearchResult(null, resultCode, latestVersion, downloadUrl, downloadType, null, null, false);
        rs.setException(exception);
        rs.fileName = fileName;
        return rs;
    }
}
