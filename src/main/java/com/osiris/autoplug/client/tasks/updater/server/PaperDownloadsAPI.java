/*
 * Copyright (c) 2021 Osiris-Team.
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

    @Nullable
    public String getLatestBuildHash(String name, String mc_version, int latest_build_id) throws WrongJsonTypeException, IOException, HttpErrorException {
        String result = null;
        final String address = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version + "/builds/" + latest_build_id;
        result = new JsonTools().getJsonObject(address).getAsJsonObject("downloads").getAsJsonObject("application").get("sha256").getAsString();
        AL.debug(this.getClass(), "Got from paper-api: build-sha256=" + result);
        return result;
    }

    @Nullable
    public String getLatestBuildFileName(String name, String mc_version, int latest_build_id) throws WrongJsonTypeException, IOException, HttpErrorException {
        String result = null;
        final String address = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version + "/builds/" + latest_build_id;
        result = new JsonTools().getJsonObject(address).getAsJsonObject("downloads").getAsJsonObject("application").get("name").getAsString();
        AL.debug(this.getClass(), "Got from paper-api: build-name=" + result);
        return result;
    }

    public int getLatestBuildId(String name, String mc_version) throws WrongJsonTypeException, IOException, HttpErrorException {
        int result = 0;
        final String address = "https://papermc.io/api/v2/projects/" + name + "/versions/" + mc_version;
        JsonArray builds = new JsonTools().getJsonObject(address).getAsJsonArray("builds");
        // Gets the last value in the array (latest). Example: size is 10 but an array starts at 0, that's why we do 10-1=9 to get the last value in the array.
        result = builds.get(builds.size() - 1).getAsInt();
        AL.debug(this.getClass(), "Got from paper-api: build-id=" + result);
        return result;
    }

}
