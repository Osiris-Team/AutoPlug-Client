/*
 * Copyright (c) 2021-2022 Osiris-Team.
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
import com.osiris.autoplug.core.logger.AL;

public class SpigotSearchByAuthor {


    /**
     * This will search for the author and scan his resources for a matching Plugin
     */
    public SearchResult search(MinecraftPlugin plugin) {
        String plName = plugin.getName();
        String plAuthor = plugin.getAuthor();
        String plVersion = plugin.getVersion();

        Exception exception = null;
        try {
            AL.debug(this.getClass(), "[" + plugin.getName() + "] Searching for author " + plAuthor + "(" + plName + ")...");
            JsonArray jsonAuthors = new SpigetAPI().getAuthors(plAuthor);
            AL.debug(this.getClass(), "[" + plugin.getName() + "] Found " + jsonAuthors.size() + " similar authors...");

            for (int i = 0; i < jsonAuthors.size(); i++) {
                JsonObject jAuthor = jsonAuthors.get(i).getAsJsonObject();
                String jAuthorName = jAuthor.get("name").getAsString();

                double similarity = StringComparator.similarity(jAuthorName, plAuthor);
                AL.debug(this.getClass(), "[" + plugin.getName() + "] Similarity between -> " + plAuthor + " and " + jAuthorName + " is: " + similarity);

                if (similarity > 0.6) {
                    String jAuthorId = jAuthor.get("id").getAsString();
                    AL.debug(this.getClass(), "[" + plugin.getName() + "] Author matches! Continuing with " + jAuthorName + " ID: " + jAuthorId);
                    JsonArray jsonAuthorPlugins = new SpigetAPI().getAuthorResources(jAuthorId);
                    AL.debug(this.getClass(), "[" + plugin.getName() + "] Found " + jsonAuthorPlugins.size() + " resources of this author...");

                    for (int j = 0; j < jsonAuthorPlugins.size(); j++) {
                        JsonObject jPL = jsonAuthorPlugins.get(j).getAsJsonObject();
                        String jPLName = jPL.get("name").getAsString();
                        String jPLID = jPL.get("id").getAsString();
                        double similarity2 = StringComparator.similarity(jPLName, plName);
                        AL.debug(this.getClass(), "[" + plugin.getName() + "] Similarity between -> " + plName + " and " + jPLName + " is: " + similarity2);
                        if (similarity2 > 0.5) {
                            AL.debug(this.getClass(), "[" + plugin.getName() + "] Plugin found!: " + jPLName);
                            plugin.setSpigotId(Integer.parseInt(jPLID));
                            return new SpigotSearchById().search(plugin);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            exception = ex;
        }
        AL.debug(this.getClass(), "[" + plugin.getName() + "] No match found for " + plName + "!");
        SearchResult result;
        if (exception != null)
            result = new SearchResult(plugin, (byte) 2, null, null, null, null, null, false);
        else
            result = new SearchResult(plugin, (byte) 3, null, null, null, null, null, false);
        result.setException(exception);
        return result;
    }

}
