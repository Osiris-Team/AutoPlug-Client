/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.core.json.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JenkinsSearch {

    public SearchResult search(String project_url, String providedArtifactName, int build_id) {
        Exception exception = null;
        byte resultCode = 0;
        String download_url = null;
        String downloadType = ".jar";
        String latestVersion = null;
        int latest_build_id = 0;
        String fileName = null;
        try {
            JsonObject json_project = Json.fromUrlAsObject(project_url + "/api/json");
            JsonObject json_last_successful_build = json_project.get("lastSuccessfulBuild").getAsJsonObject();
            latest_build_id = json_last_successful_build.get("number").getAsInt();
            latestVersion = "" + latest_build_id;
            if (latest_build_id > build_id) {
                resultCode = 1;
                String buildUrl = json_last_successful_build.get("url").getAsString() + "/api/json";
                JsonArray arrayArtifacts = Json.fromUrlAsObject(buildUrl).getAsJsonArray("artifacts");

                // Contains JsonObjects sorted by their artifact names lengths, from smallest to longest.
                // The following does that sorting.
                List<JsonObject> sortedArtifactObjects = new ArrayList<>();
                for (JsonElement e :
                        arrayArtifacts) {
                    JsonObject obj = e.getAsJsonObject();
                    String name = obj.get("fileName").getAsString();
                    if (sortedArtifactObjects.size() == 0) sortedArtifactObjects.add(obj);
                    else {
                        int finalIndex = 0;
                        boolean isSmaller = false;
                        for (int i = 0; i < sortedArtifactObjects.size(); i++) {
                            String n = sortedArtifactObjects.get(i).get("fileName").getAsString();
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

                // Find artifact-name containing our provided artifact-name
                for (JsonObject obj : sortedArtifactObjects) {
                    String n = obj.get("fileName").getAsString();
                    if (n.contains(providedArtifactName)) {
                        fileName = n;
                        download_url = project_url + "/" + latest_build_id + "/artifact/" + obj.get("relativePath").getAsString();
                        break;
                    }
                }

                if (download_url == null) {
                    List<String> names = new ArrayList<>();
                    for (JsonObject obj :
                            sortedArtifactObjects) {
                        String n = obj.get("fileName").getAsString();
                        names.add(n);
                    }
                    throw new Exception("Failed to find an artifact-name containing '" + providedArtifactName + "' inside of '" + Arrays.toString(names.toArray()) + "'!");
                }
            }
        } catch (Exception e) {
            exception = e;
            resultCode = 2;
        }

        SearchResult rs = new SearchResult(null, resultCode, latestVersion, download_url, downloadType, null, null, false);
        rs.setException(exception);
        rs.jenkinsId = latest_build_id;
        rs.fileName = fileName;
        return rs;
    }

}
