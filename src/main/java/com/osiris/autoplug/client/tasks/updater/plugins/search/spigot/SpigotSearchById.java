/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins.search.spigot;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsVersion;
import com.osiris.autoplug.core.json.JsonTools;

public class SpigotSearchById {

    public SearchResult search(DetailedPlugin plugin) {
        int spigotId = plugin.getSpigotId();
        Exception exception = null;

        String url = "https://api.spiget.org/v2/resources/" + spigotId +
                "/versions?size=1&sort=-releaseDate";

        // Get the latest version
        String latest = null;
        try {
            latest = new JsonTools().getJsonArray(url).get(0).getAsJsonObject().get("name").getAsString();
        } catch (Exception e) {
            exception = new Exception("[" + plugin.getName() + "] Failed to get latest version string for id: " + spigotId + "(" + e.getMessage() + ") via url: " + url);
        }

        // Get the file type and downloadUrl
        String url1 = "https://api.spiget.org/v2/resources/" + spigotId;
        String type = null;
        String downloadUrl = null;
        try {
            JsonObject json = new JsonTools().getJsonObject(url1).getAsJsonObject("file");
            type = json.get("type").getAsString();
            downloadUrl = "https://www.spigotmc.org/" + json.get("url").getAsString();
        } catch (Exception e) {
            exception = new Exception("[" + plugin.getName() + "] Failed to get file type string for id: " + spigotId + "(" + e.getMessage() + ") via url: " + url1);
        }

        // If not external download over the spiget api
        if (type != null && !type.equals("external")) {
            downloadUrl = "https://api.spiget.org/v2/resources/" + spigotId + "/download";
        }

        byte code = 0;
        if (downloadUrl == null || latest == null) code = 2;
        if (new UtilsVersion().compare(plugin.getVersion(), latest)) code = 1;

        SearchResult result = new SearchResult(plugin, code, latest, downloadUrl, type, "" + spigotId, null);
        result.setException(exception);
        return result;
    }
}
