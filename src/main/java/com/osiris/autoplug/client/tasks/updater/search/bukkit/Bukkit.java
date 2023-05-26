/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search.bukkit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.utils.StringComparator;
import com.osiris.autoplug.client.utils.UtilsVersion;
import com.osiris.jlib.logger.AL;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Returns download-url via ServerMods API (https://bukkit.gamepedia.com/ServerMods_API) <br>
 * Cannot be used, because not safe.
 */
@Deprecated
public class Bukkit {
    private final String REQUEST_URL_1 = "https://servermods.forgesvc.net/servermods/projects?search=";
    private final String REQUEST_URL_2 = "https://servermods.forgesvc.net/servermods/files?projectIds=";

    private String INPUT_PLUGIN_NAME;
    private String OUTPUT_PLUGIN_VERSION;


    public String search(String pl_name, String pl_version) {
        INPUT_PLUGIN_NAME = pl_name;

        //In url pl_name needs to be lowercase, else no result
        String pl_name_lowercase = pl_name.toLowerCase();
        AL.info("Searching for " + pl_name_lowercase);

        JsonElement element = get_json_element(REQUEST_URL_1, pl_name_lowercase);

        assert element != null; //kp was das sein soll
        if (element.isJsonNull()) {

            AL.info(" Json is null!");
            return "query_returns_array_no_author";
        } else {
            //Go through all elements
            int size = element.getAsJsonArray().size();
            String[] result_pl_name = new String[size];
            for (int i = 0; i < size; i++) {

                JsonObject result_json_object = element.getAsJsonArray().get(i).getAsJsonObject(); //Select Array Object of the response
                result_pl_name[i] = result_json_object.get("name").getAsString();

                //Remove any symbols and spaces to exactly compare both strings
                result_pl_name[i] = result_pl_name[i].replaceAll("[^a-zA-Z0-9]", "");
                pl_name = pl_name.replaceAll("[^a-zA-Z0-9]", "");

                double similarity = StringComparator.similarity(result_pl_name[i], pl_name);
                AL.info("Similarity between -> " + pl_name + " and " + result_pl_name[i] + " is: " + similarity);
                if (similarity > 0.8) {

                    JsonElement element2 = get_json_element(REQUEST_URL_2, result_json_object.get("id").getAsString());
                    JsonArray array = element2.getAsJsonArray();
                    //Gets the last (the latest) object in the array
                    JsonObject latest = array.get(array.size() - 1).getAsJsonObject();

                    //Compare versions
                    boolean resultVersion;
                    OUTPUT_PLUGIN_VERSION = latest.get("name").toString(); //API is just shit
                    AL.info("Comparing versions: " + OUTPUT_PLUGIN_VERSION + " with " + pl_version);
                    resultVersion = new UtilsVersion().isSecondBigger(pl_version, OUTPUT_PLUGIN_VERSION); // result = true
                    if (resultVersion) {
                        AL.info("New Version found for: " + INPUT_PLUGIN_NAME + " From -> " + pl_version + " to -> " + OUTPUT_PLUGIN_VERSION);
                        String final_url = latest.get("downloadUrl").toString();
                        AL.info("Pre-download url: " + final_url);
                        final_url = final_url.replaceAll("[\\\\]", ""); //removes all backslashes
                        AL.info(final_url);
                        AL.info("Final download url: " + final_url);
                        return final_url;

                    } else {
                        AL.info("Already up-to-date! " + INPUT_PLUGIN_NAME);
                        return "query_returns_array_no_update";
                    }


                }

            }


        }
        AL.info("No matches for: " + INPUT_PLUGIN_NAME);
        return "query_no_result";
    }

    private JsonElement get_json_element(String request_url, String search_input) {

        try {

            URL url = new URL(request_url + search_input);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.addRequestProperty("User-Agent", "Mozilla/5.0");
            InputStream inputStream = connection.getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);
            return JsonParser.parseReader(reader);

        } catch (IOException e) {
            AL.warn(e);
            return null;
        }

    }

}
