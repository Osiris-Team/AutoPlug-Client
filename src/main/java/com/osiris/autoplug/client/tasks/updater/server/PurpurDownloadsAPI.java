/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonObject;
import com.osiris.autoplug.core.json.JsonTools;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import com.osiris.autoplug.core.json.exceptions.WrongJsonTypeException;
import com.osiris.autoplug.core.logger.AL;

import java.io.IOException;

public class PurpurDownloadsAPI {
    private final String baseUrl = "https://api.pl3x.net/v2";

    public JsonObject getLatestBuild(String serverSoftware, String serverVersion) throws WrongJsonTypeException, IOException, HttpErrorException {
        final String url = baseUrl + "/" + serverSoftware + "/" + serverVersion + "/latest";
        AL.debug(this.getClass(), url);
        return new JsonTools().getJsonObject(url).getAsJsonObject();
    }

    public String getLatestDownloadUrl(String serverSoftware, String serverVersion) {
        final String url = baseUrl + "/" + serverSoftware + "/" + serverVersion + "/latest/download";
        AL.debug(this.getClass(), url);
        return url;
    }
}
