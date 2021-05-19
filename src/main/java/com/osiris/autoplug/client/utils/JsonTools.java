/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class JsonTools {

    /**
     * Returns the json-element. This can be a json-array or a json-object.
     *
     * @param url The url which leads to the json file.
     * @return JsonElement
     * @throws Exception When status code other than 200.
     */
    private JsonElement getJsonElement(String url) throws Exception {

        //Requests and connections
        final URL url1 = new URL(url);
        final HttpURLConnection con = (HttpURLConnection) url1.openConnection();
        con.addRequestProperty("User-Agent", "Mozilla/5.0");
        InputStream in;
        InputStreamReader inr;

        if (con.getResponseCode() == 200) {
            in = con.getInputStream();
            inr = new InputStreamReader(in);
            return JsonParser.parseReader(inr);
        } else {
            throw new Exception("Couldn't get the json file from: " + url + " Status code: " + con.getResponseCode() + " Message: " + con.getResponseMessage());
        }

    }

    /**
     * Turns a JsonArray with its objects into a list.
     *
     * @param url The url where to find the json file.
     * @return A list with JsonObjects or null if there was a error with the url.
     */
    public List<JsonObject> getJsonArrayAsList(String url) throws Exception {
        List<JsonObject> objectList = new ArrayList<>();
        JsonElement element = getJsonElement(url);
        if (element != null && element.isJsonArray()) {
            final JsonArray ja = element.getAsJsonArray();
            for (int i = 0; i < ja.size(); i++) {
                JsonObject jo = ja.get(i).getAsJsonObject();
                objectList.add(jo);
            }
            return objectList;
        } else {
            throw new Exception("Its not a json array! Check it out -> " + url);
        }
    }

    /**
     * Gets a single JsonObject.
     *
     * @param url The url where to find the json file.
     * @return A JsonObject or null if there was a error with the url.
     */
    public JsonObject getJsonObject(String url) throws Exception {
        JsonElement element = getJsonElement(url);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        } else {
            throw new Exception("Its not a json object! Check it out -> " + url);
        }
    }

}
