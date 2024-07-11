/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.google.gson.*;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.jlib.json.Json;
import java.util.ArrayList;


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
                    latest = traverseJsonArray(response.getAsJsonArray(), (byte) 0);
                    downloadUrl = traverseJsonArray(response.getAsJsonArray(), (byte) 1);
                } catch (Exception e) {
                    throw e;
                }
            } else if (response.isJsonObject()) {
                try {
                    latest = traverseJsonObject(response.getAsJsonObject(), (byte) 0);
                    downloadUrl = traverseJsonObject(response.getAsJsonObject(), (byte) 1);
                } catch (Exception e) {
                    throw e;
                }
            } else {
                throw new IllegalArgumentException("Invalid JSON response format");
            }

            String[] pluginVersionComponents = currentVersion.split("\\.");
            String[] latestVersionComponents = latest.split("\\.");

            for (int i = 0; i < Math.min(pluginVersionComponents.length, latestVersionComponents.length); i++) {
                 long pluginComponent = Long.parseLong(pluginVersionComponents[i]);
                 long latestComponent = Long.parseLong(latestVersionComponents[i]);

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

    private String getLatestVersionFromRe(JsonObject release){

            String[] versionNaming = {"version_number", "version"};
            String latest = null;

            for (String naming : versionNaming) {
                if (release.has(naming)) {
                    String version = release.get(naming).getAsString().replaceAll("[^0-9.]", "");
                    if (!version.isEmpty()) {
                        latest = version;
                        break;
                    }
                }
            }
            return latest;
    }

    private String getDownloadFromRe(JsonObject release){

            String[] downloadNaming = {"download_url", "download", "file", "download_file"};
            String downloadUrl = null;

            for (String naming : downloadNaming) {
                if (release.has(naming)) {
                    String durl = release.get(naming).getAsString();
                    if (!durl.isEmpty()) {
                        downloadUrl = durl;
                        break;
                    }
                }
            }
            return downloadUrl;
    }

    private String traverseJsonArray(JsonArray jsonArray, byte lookingFor) {
        String r = null;
        for (JsonElement element : jsonArray) {
            if (element.isJsonObject()) {
                r = traverseJsonObject(element.getAsJsonObject(),lookingFor);
            } else if (element.isJsonArray()) {
                r = traverseJsonArray(element.getAsJsonArray(),lookingFor);
            }
            if (r != null)
            break;
        }
        return r;
    }

    private String traverseJsonObject(JsonObject jsonObject, byte lookingFor) {
        String r = null;
        for (String key : jsonObject.keySet()) {
            JsonElement element = jsonObject.get(key);
            if (element.isJsonObject()) {
                r = traverseJsonObject(element.getAsJsonObject(),lookingFor);
            } else if (element.isJsonArray()) {
                r = traverseJsonArray(element.getAsJsonArray(),lookingFor);
            }
            if (r != null)
            break;
        }

        if (lookingFor == 0) {
            r = getLatestVersionFromRe(jsonObject.getAsJsonObject());
        } else if (lookingFor == 1) {
            r = getDownloadFromRe(jsonObject.getAsJsonObject());
        }
        return r;
    }
}
