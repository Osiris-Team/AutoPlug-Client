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
        byte code = 0;
        try {
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

            String[] pluginVersionComponents = currentVersion.split("\\.");
            String[] latestVersionComponents = latest.split("\\.");

            for (int i = 0; i < Math.min(pluginVersionComponents.length, latestVersionComponents.length); i++) {
                 int pluginComponent = Integer.parseInt(pluginVersionComponents[i]);
                 int latestComponent = Integer.parseInt(latestVersionComponents[i]);

                 if (pluginComponent < latestComponent) {
        // plugin.getVersion() is smaller than latest
                     code = 1;
                     break;
                 } else if (pluginComponent > latestComponent) {
        // plugin.getVersion() is greater than latest
                     break;
                 }
             }
            
        } catch (Exception e) {
            exception = e;
            code = 2;
        }

        if (downloadUrl == null && url == null)
            code = 2;
        SearchResult result = new SearchResult(null, code, latest, downloadUrl, type, null, null, false);
        result.setException(exception);
        return result;
    }
}
