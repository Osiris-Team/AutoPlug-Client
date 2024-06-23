/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.jlib.json.Json;


public class CustomCheckURL {

    public CustomCheckURL(){}

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public SearchResult doCustomCheck(String url, String currentVersion) {
        url = new UtilsURL().clean(url);
        Exception exception = null;
        String latest = null;
        String type = ".jar";
        String downloadUrl = null;
        SearchResult.Type code = SearchResult.Type.UP_TO_DATE;
        try {
            JsonElement response = Json.get(url);
            JsonObject release;

            if (response.isJsonArray()) {
                try {
                    release = response.getAsJsonArray().get(0).getAsJsonObject();
                } catch (Exception e) {
                    throw e;
                }
            } else if (response.isJsonObject()) {
                try {
                    release = response.getAsJsonObject();
                } catch (Exception e) {
                    throw e;
                }
            } else {
                throw new IllegalArgumentException("Invalid JSON response format");
            }

            String[] versionNaming = {"version_number", "version"};

            for (String naming : versionNaming) {
                if (release.has(naming)) {
                    String version = release.get(naming).getAsString().replaceAll("[^0-9.]", "");
                    if (!version.isEmpty()) {
                        latest = version;
                        break;
                    }
                }
            }

            String[] downloadNaming = {"download_url", "download", "file", "download_file"};

            for (String naming : downloadNaming) {
                if (release.has(naming)) {
                    String durl = release.get(naming).getAsString();
                    if (!durl.isEmpty()) {
                        downloadUrl = durl;
                        break;
                    }
                }
            }

            String[] pluginVersionComponents = currentVersion.split("\\.");
            String[] latestVersionComponents = latest.split("\\.");

            for (int i = 0; i < Math.min(pluginVersionComponents.length, latestVersionComponents.length); i++) {
                 int pluginComponent = Integer.parseInt(pluginVersionComponents[i]);
                 int latestComponent = Integer.parseInt(latestVersionComponents[i]);

                 if (pluginComponent < latestComponent) {
        // plugin.getVersion() is smaller than latest
                     code = SearchResult.Type.UPDATE_AVAILABLE;
                     break;
                 } else if (pluginComponent > latestComponent) {
        // plugin.getVersion() is greater than latest
                     break;
                 }
             }
            
        } catch (Exception e) {
            exception = e;
            code = SearchResult.Type.API_ERROR;
        }

        if (downloadUrl == null && url == null)
            code = SearchResult.Type.API_ERROR;
        SearchResult result = new SearchResult(null, code, latest, downloadUrl, type, null, null, false);
        result.setException(exception);
        return result;
    }
}
