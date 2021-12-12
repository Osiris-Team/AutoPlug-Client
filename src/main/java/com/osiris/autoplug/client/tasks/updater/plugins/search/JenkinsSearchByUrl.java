/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.utils.StringComparator;
import com.osiris.autoplug.core.json.JsonTools;

public class JenkinsSearchByUrl {

    public SearchResult search(DetailedPlugin plugin) {
        String project_url = plugin.getJenkinsProjectUrl();
        String artifact_name = plugin.getJenkinsArtifactName();
        int build_id = plugin.getJenkinsBuildId();
        double minimumSimilarity = 0.90;

        Exception exception = null;
        byte resultCode = 0;
        String download_url = null;
        String downloadType = ".jar";
        String latestVersion = null;
        int latest_build_id = 0;
        try {
            JsonTools json_tools = new JsonTools();
            JsonObject json_project = json_tools.getJsonObject(project_url + "/api/json");
            JsonObject json_last_successful_build = json_project.get("lastSuccessfulBuild").getAsJsonObject();
            latest_build_id = json_last_successful_build.get("number").getAsInt();
            latestVersion = "" + latest_build_id;
            if (latest_build_id > build_id) {
                resultCode = 1;
                String buildUrl = json_last_successful_build.get("url").getAsString() + "/api/json";
                JsonArray arrayArtifacts = json_tools.getJsonObject(buildUrl).getAsJsonArray("artifacts");
                String onlineArtifactFileName = null;
                for (JsonElement e :
                        arrayArtifacts) {
                    if (e.getAsJsonObject().get("fileName").getAsString().equals(artifact_name)) {
                        onlineArtifactFileName = e.getAsJsonObject().get("fileName").getAsString();
                        download_url = project_url + "/" + latest_build_id + "/artifact/" + e.getAsJsonObject().get("relativePath").getAsString();
                        break;
                    }
                }

                if (download_url == null)
                    for (JsonElement e :
                            arrayArtifacts) {
                        String name = e.getAsJsonObject().get("fileName").getAsString()
                                .replaceAll("\\d", "")
                                .replaceAll("[.]", "")
                                .replaceAll("[-]", ""); // Removes numbers, dots and hyphens
                        if (StringComparator.similarity(name, artifact_name) > minimumSimilarity) {
                            onlineArtifactFileName = e.getAsJsonObject().get("fileName").getAsString();
                            download_url = project_url + "/" + latest_build_id + "/artifact/" + e.getAsJsonObject().get("relativePath").getAsString();
                            break;
                        }
                    }

                if (download_url == null) {
                    throw new Exception("Failed to find an equal or similar artifact-name '" + artifact_name + "' inside of '" + arrayArtifacts + "'!");
                }
            }
        } catch (Exception e) {
            exception = e;
            resultCode = 2;
        }

        SearchResult rs = new SearchResult(plugin, resultCode, latestVersion, download_url, downloadType, null, null, false);
        rs.setException(exception);
        rs.jenkinsId = latest_build_id;
        return rs;
    }

}
