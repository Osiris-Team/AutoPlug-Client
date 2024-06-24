/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

<<<<<<< HEAD
=======
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
>>>>>>> 36158801efa9b494f6b6fbe443acaee837e9eb9b
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.jlib.json.Json;

<<<<<<< HEAD
=======
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

>>>>>>> 36158801efa9b494f6b6fbe443acaee837e9eb9b

public class CustomCheckURL {

    public CustomCheckURL(){}

<<<<<<< HEAD
    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

=======
>>>>>>> 36158801efa9b494f6b6fbe443acaee837e9eb9b
    public SearchResult doCustomCheck(String url, String currentVersion) {
        url = new UtilsURL().clean(url);
        Exception exception = null;
        String latest = null;
        String type = ".jar";
        String downloadUrl = null;
        SearchResult.Type code = SearchResult.Type.UP_TO_DATE;
        try {
<<<<<<< HEAD
            JsonObject release;
            try {
                release = Json.getAsJsonArray(url)
                        .get(0).getAsJsonObject();
            } catch (Exception e) {
                throw e;
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
=======
            JsonElement response = Json.get(url);
            List<String> latestVersions = new ArrayList<>();
            List<String> downloadUrls = new ArrayList<>();
            traverseJson("", response, (key, value) -> {
                String s1 = getLatestVersionIfValid(key, value);
                if (!s1.isEmpty()) latestVersions.add(s1);

                String s2 = getDownloadUrlIfValid(key, value);
                if (!s2.isEmpty()) downloadUrls.add(s2);
            });

            if (!latestVersions.isEmpty()) latest = latestVersions.get(0);
            if (!downloadUrls.isEmpty()) downloadUrl = downloadUrls.get(0);
>>>>>>> 36158801efa9b494f6b6fbe443acaee837e9eb9b

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
<<<<<<< HEAD
=======

    /**
     * Returns empty string if not valid.
     */
    private String getLatestVersionIfValid(String key, String value) {
        if (key.equals("version_number") || key.equals("version"))
            return value.replaceAll("[^0-9.]", "");
        else return "";
    }

    /**
     * Returns empty string if not valid.
     */
    private String getDownloadUrlIfValid(String key, String value) {
        if (key.equals("download_url") || key.equals("download") || key.equals("file") || key.equals("download_file"))
            return value;
        else return "";
    }

    public static void traverseJson(String key, JsonElement element, BiConsumer<String, String> code) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                traverseJson(entry.getKey(), entry.getValue(), code);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                traverseJson(key, item, code);
            }
        } else if (element.isJsonNull()) {
            code.accept(key, "");
        } else if (element.isJsonPrimitive()) {
            code.accept(key, element.getAsString());
        } else
            throw new IllegalArgumentException("Invalid JSON response format");
    }
>>>>>>> 36158801efa9b494f6b6fbe443acaee837e9eb9b
}
