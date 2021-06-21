/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins.search.bukkit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.DetailedPlugin;
import com.osiris.autoplug.client.tasks.updater.plugins.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsVersion;
import com.osiris.autoplug.core.json.JsonTools;
import com.osiris.autoplug.core.logger.AL;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BukkitSearchById {

    public SearchResult search(DetailedPlugin plugin) {
        AL.debug(this.getClass(), "[" + plugin.getName() + "] Performing bukkit search by id");
        int bukkitId = plugin.getBukkitId();

        String url = "https://api.curseforge.com/servermods/files?projectIds=" + bukkitId;

        Exception exception = null;
        JsonArray versions = null;
        JsonObject json = null;
        String latest = null;
        String downloadUrl = null;
        String downloadType = "unknown";
        try {
            versions = new JsonTools().getJsonArray(url);
            json = versions.get(versions.size() - 1).getAsJsonObject();
            latest = json.get("name").getAsString();
            downloadUrl = json.get("downloadUrl").getAsString();
            Matcher m = Pattern.compile("[.][^.]+$")
                    .matcher(json.get("fileName").getAsString());
            if (m.find()) {
                downloadType = m.group(0);
            } else
                throw new Exception("[" + plugin.getName() + "] Couldn't find a downloadType in fileName: " + json.get("fileName").getAsString());
        } catch (Exception e) {
            exception = e;
        }

        byte code = 0;
        if (downloadUrl == null || latest == null) code = 2;
        if (new UtilsVersion().compare(plugin.getVersion(), latest)) code = 1;

        SearchResult result = new SearchResult(plugin, code, latest, downloadUrl, downloadType, null, "" + bukkitId);
        result.setException(exception);
        return result;
    }

}
