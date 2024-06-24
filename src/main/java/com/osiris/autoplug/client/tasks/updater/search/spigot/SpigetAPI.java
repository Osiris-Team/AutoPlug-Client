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
import com.osiris.jlib.json.Json;
import com.osiris.jlib.json.exceptions.HttpErrorException;

import java.util.ArrayList;
import java.util.List;

/**
 * This class deals with Spigets REST-API and provides useful methods for easy access. <br>
 * Spiget is used, since spigotmc.org has no official API available.
 */
public class SpigetAPI {
    private final String LINK_API = "https://api.spiget.org/v2/";

    private final String LINK_AUTHORS = "https://api.spiget.org/v2/authors/";
    private final String LINK_RESOURCES = "https://api.spiget.org/v2/resources?size=";
    private final String LINK_RESOURCES_NEW = "https://api.spiget.org/v2/resources/new?size=";

    private final String LINK_SEARCH_RESOURCES = "https://api.spiget.org/v2/search/resources/";
    private final String LINK_SEARCH_AUTHORS = "https://api.spiget.org/v2/search/authors/";

    private final String SORT_DESC_DOWNLOADS = "?sort=-downloads";
    private final String SORT_DESC_UPDATED = "&sort=-updateDate";
    private final String SORT_DESC_LIKES = "&sort=-likes";
    private final String SORT_BY_RATINGS = "&sort=-rating";


    /**
     * Get a JsonArray containing spigot plugins
     * with a similar name to queryPlName.
     */
    public JsonArray getPlugins(String queryPlName) throws Exception {
        try {
            return Json.getAsJsonArray(LINK_SEARCH_RESOURCES + queryPlName + SORT_DESC_DOWNLOADS);
        } catch (HttpErrorException e) {
            if (e.getHttpErrorCode() != 404)
                throw e;
            // Only catch this exception and only ignore this exception if code 404,
            // because it just means that it couldn't find results from that query.
        }
        return new JsonArray(); // Returns an empty array
    }

    /**
     * Get a JsonArray containing spigot authors
     * with a similar name to queryAuthorName.
     */
    public JsonArray getAuthors(String queryAuthorName) throws Exception {
        try {
            return Json.getAsJsonArray(LINK_SEARCH_AUTHORS + queryAuthorName);
        } catch (HttpErrorException e) {
            if (e.getHttpErrorCode() != 404)
                throw e;
            // Only catch and ignore this exception if code 404,
            // because it just means that it couldn't find results from that query.
        }
        return new JsonArray(); // Returns an empty array
    }

    /**
     * Get a JsonObject containing author details.
     */
    public JsonObject getAuthorDetails(String authorId) throws Exception {
        if (authorId == null || authorId.equals("0"))
            throw new Exception("AuthorID is either null or equals '0'!"); // TODO ISSUE OPEN HERE: https://github.com/SpiGetOrg/Spiget/issues/32

        try {
            return Json.getAsObject(LINK_AUTHORS + authorId);
        } catch (HttpErrorException e) {
            if (e.getHttpErrorCode() != 404)
                throw e;
            // Only catch and ignore this exception if code 404,
            // because it just means that it couldn't find results from that query.
        }
        return null;
    }

    /**
     * Get a JsonArray containing author resources.
     */
    public JsonArray getAuthorResources(String authorId) throws Exception {
        try {
            return Json.getAsJsonArray(LINK_AUTHORS + authorId + "/resources?size=100&sort=-downloads");
            // Limit the max size to 100 and sort by most downloads to increase the chance of a match.
        } catch (HttpErrorException e) {
            if (e.getHttpErrorCode() != 404)
                throw e;
            // Only catch and ignore this exception if code 404,
            // because it just means that it couldn't find results from that query.
        }
        return new JsonArray(); // Returns an empty array
    }

    public JsonObject getVersionDetails(String pluginId, String versionId) throws Exception {
        try {
            return Json.getAsObject(LINK_API + "resources/" + pluginId + "/versions/" + versionId);
        } catch (HttpErrorException e) {
            if (e.getHttpErrorCode() != 404)
                throw e;
            // Only catch and ignore this exception if code 404,
            // because it just means that it couldn't find results from that query.
        }
        return null;
    }

    /**
     * Get the latest version from this spigot plugin id.
     */
    public String getLatestVersion(String spigotId) throws Exception {
        return Json.getAsJsonArray("https://api.spiget.org/v2/resources/" + spigotId +
                "/versions?size=1&sort=-releaseDate").get(0).getAsJsonObject().get("name").getAsString();
    }

    /**
     * Gets the JsonObjects inside a JsonArray and puts them into a
     * new list.
     *
     * @param url
     * @return
     * @throws Exception
     */
    private List<JsonObject> getJsonObjects(String url) throws Exception {
        List<JsonObject> objectList = new ArrayList<>();
        JsonArray ja = null;
        try {
            ja = Json.getAsJsonArray(url);
        } catch (HttpErrorException e) {
            if (e.getHttpErrorCode() != 404)
                throw e;
            // Only catch and ignore this exception if code 404,
            // because it just means that it couldn't find results from that query.
        }
        if (ja != null)
            for (int i = 0; i < ja.size(); i++) {
                JsonObject jo = ja.get(i).getAsJsonObject();
                objectList.add(jo);
            }
        return objectList;
    }

    /**
     * Gets a list of plugins sorted by user query without size limit and sorted by downloads.
     *
     * @param query The plugins name Spiget should search for.
     */
    public List<JsonObject> getPluginsByUserQuery(String query) throws Exception {
        return getJsonObjects(LINK_SEARCH_RESOURCES + query + SORT_DESC_DOWNLOADS); //Here instead of & we use ?
    }

    /**
     * Gets a list of plugins sorted by their last update date.
     *
     * @param size The maximum size of plugins this list should return.
     */
    public List<JsonObject> getPluginsByLatestUpdate(int size) throws Exception {
        return getJsonObjects(LINK_RESOURCES + size + SORT_DESC_UPDATED);
    }

    /**
     * Gets a list of newest plugins.
     *
     * @param size The maximum size of plugins this list should return.
     */
    public List<JsonObject> getPluginsByNewReleases(int size) throws Exception {
        return getJsonObjects(LINK_RESOURCES_NEW + size);
    }

    /**
     * Gets a list of plugins sorted by their download count.
     *
     * @param size The maximum size of plugins this list should return.
     */
    public List<JsonObject> getPluginsByMostDownloaded(int size) throws Exception {
        return getJsonObjects(LINK_RESOURCES + size + SORT_DESC_DOWNLOADS);
    }

    /**
     * Gets a list of plugins sorted by their likes count.
     *
     * @param size The maximum size of plugins this list should return.
     */
    public List<JsonObject> getPluginsByMostLiked(int size) throws Exception {
        return getJsonObjects(LINK_RESOURCES + size + SORT_DESC_LIKES);
    }

    /**
     * Gets a list of plugins sorted by their ratings count.
     *
     * @param size The maximum size of plugins this list should return.
     */
    public List<JsonObject> getPluginsByMostRated(int size) throws Exception {
        return getJsonObjects(LINK_RESOURCES + size + SORT_BY_RATINGS);
    }

}
