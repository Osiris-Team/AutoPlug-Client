/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonArray;
import com.osiris.autoplug.core.json.JsonTools;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import com.osiris.autoplug.core.json.exceptions.WrongJsonTypeException;
import com.osiris.autoplug.core.logger.AL;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class PaperDownloadsAPI {
    String baseUrl = "https://papermc.io/api/v2/projects/";

    @Nullable
    public String getLatestBuildHash(String name, String mc_version, int latest_build_id) throws WrongJsonTypeException, IOException, HttpErrorException {
        String result = null;
        final String url = baseUrl + name + "/versions/" + mc_version + "/builds/" + latest_build_id;
        AL.debug(this.getClass(), url);
        result = new JsonTools().getJsonObject(url).getAsJsonObject("downloads").getAsJsonObject("application").get("sha256").getAsString();
        AL.debug(this.getClass(), "Got from paper-api: build-sha256=" + result);
        return result;
    }

    @Nullable
    public String getLatestBuildFileName(String name, String mc_version, int latest_build_id) throws WrongJsonTypeException, IOException, HttpErrorException {
        String result = null;
        final String url = baseUrl + name + "/versions/" + mc_version + "/builds/" + latest_build_id;
        AL.debug(this.getClass(), url);
        result = new JsonTools().getJsonObject(url).getAsJsonObject("downloads").getAsJsonObject("application").get("name").getAsString();
        AL.debug(this.getClass(), "Got from paper-api: build-name=" + result);
        return result;
    }

    public int getLatestBuildId(String name, String mc_version) throws WrongJsonTypeException, IOException, HttpErrorException {
        int result = 0;
        final String url = baseUrl + name + "/versions/" + mc_version;
        AL.debug(this.getClass(), url);
        JsonArray builds = new JsonTools().getJsonObject(url).getAsJsonArray("builds");
        // Gets the last value in the array (latest). Example: size is 10 but an array starts at 0, that's why we do 10-1=9 to get the last value in the array.
        result = builds.get(builds.size() - 1).getAsInt();
        AL.debug(this.getClass(), "Got from paper-api: build-id=" + result);
        return result;
    }

    public String getDownloadUrl(String serverSoftware, String serverVersion, int latest_build_id, String build_name) {
        String url = baseUrl + serverSoftware + "/versions/" + serverVersion + "/builds/" + latest_build_id + "/downloads/" + build_name;
        AL.debug(this.getClass(), url);
        return url;
    }
}
