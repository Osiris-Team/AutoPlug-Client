/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search.spigot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.StringComparator;
import com.osiris.jlib.logger.AL;

import java.util.ArrayList;
import java.util.List;

public class SpigotSearchByName {

    /**
     * This will search by the Plugins name for matching resources
     *
     * @return an Array containing the download-url or error-code in 0
     * and the latest version in 1
     */
    public SearchResult search(MinecraftPlugin plugin) {
        String plName = plugin.getName();
        String plAuthor = plugin.getAuthor();
        String plVersion = plugin.getVersion();
        Exception exception = null;
        List<MinecraftPlugin> similarPlugins = new ArrayList<>();
        try {
            AL.debug(this.getClass(), "[" + plugin.getName() + "] Searching for plugin " + plName + "(" + plAuthor + ")...");
            JsonArray queryPlugins = new SpigetAPI().getPlugins(plName);
            AL.debug(this.getClass(), "[" + plugin.getName() + "] Found " + queryPlugins.size() + " similar plugins!");

            for (int i = 0; i < queryPlugins.size(); i++) {

                JsonObject jsonPlugin = queryPlugins.get(i).getAsJsonObject();
                JsonObject jsonAuthor = null;
                try {
                    jsonAuthor = new SpigetAPI().getAuthorDetails(jsonPlugin.get("author").getAsJsonObject().get("id").getAsString());
                } catch (Exception ignore) {
                }

                if (jsonAuthor != null) {
                    String queryAuthor = jsonAuthor.get("name").getAsString();

                    //Remove any symbols and spaces to exactly compare both strings, but keep numbers
                    queryAuthor = queryAuthor.replaceAll("[^a-zA-Z]", "");
                    plAuthor = plAuthor.replaceAll("[^a-zA-Z]", "");
                    int plId = Integer.parseInt(jsonPlugin.get("id").getAsString());

                    similarPlugins.add(new MinecraftPlugin(null, plName, plVersion, queryAuthor, plId, 0, null, false));

                    double similarity = StringComparator.similarity(queryAuthor, plAuthor);
                    AL.debug(this.getClass(), "[" + plugin.getName() + "] Similarity between -> " + plAuthor + " and " + queryAuthor + " is: " + similarity);
                    if (similarity > 0.5) {
                        AL.debug(this.getClass(), "[" + plugin.getName() + "] Found plugin " + plName + " with matching author: " + queryAuthor + ")");
                        plugin.setSpigotId(plId);
                        return new SpigotSearchById().search(plugin);
                    }
                }

            }
        } catch (Exception e) {
            exception = e;
        }
        AL.debug(this.getClass(), "[" + plugin.getName() + "] No match found for " + plName + "!");
        SearchResult result;
        if (exception != null)
            result = new SearchResult(plugin, SearchResult.Type.API_ERROR, null, null, null, null, null, false);
        else
            result = new SearchResult(plugin, SearchResult.Type.RESOURCE_NOT_FOUND, null, null, null, null, null, false);
        result.similarPlugins = similarPlugins;
        result.setException(exception);
        return result;
    }

}
