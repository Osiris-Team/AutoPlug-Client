package com.osiris.autoplug.client.tasks.updater.search;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


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

    public SearchResult doCustomCheck(MinecraftPlugin plugin) {

        String url = plugin.getCustomCheckURL();
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
                try {
                    release = Json.getAsJsonObject(url);
                } catch (Exeption ex){
                    throw ex;
                }
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

            String[] pluginVersionComponents = plugin.getVersion().split("\\.");
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
        
        if (downloadUrl == null && plugin.customDownloadURL == null)
            code = 2;
        SearchResult result = new SearchResult(null, code, latest, downloadUrl, type, null, null, false);
        result.setException(exception);
        return result;
    }
}
