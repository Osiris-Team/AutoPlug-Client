/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.core.json.Json;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import com.osiris.autoplug.core.json.exceptions.WrongJsonTypeException;
import com.osiris.autoplug.core.logger.AL;

import java.io.IOException;

public class FabricDownloadsAPI {
    private final String baseUrl = "https://meta.fabricmc.net/v2/versions";

    public JsonObject getLatestLoader() throws WrongJsonTypeException, IOException, HttpErrorException {
        final String url = baseUrl + "/loader?limit=1";
        AL.debug(this.getClass(), url);
        JsonObject latest = new JsonObject();
        JsonArray array = Json.fromUrlAsJsonArray(url);
        AL.debug(this.getClass(), "Got from URL:\t" + array.toString());
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.get("stable").getAsBoolean()) {
                String version = obj.get("version").getAsString();
                latest.addProperty("version", version);
                latest.addProperty("majorID", version.split("\\.")[0]);
                latest.addProperty("minorID", version.split("\\.")[1]);
                latest.addProperty("buildID", version.split("\\.")[2]);
                break;
            }
        }
        return latest;
    }

    public JsonObject getLatestInstaller(String serverVersion) throws WrongJsonTypeException, IOException, HttpErrorException {
        final String url = baseUrl + "/installer?limit=1";
        AL.debug(this.getClass(), url);
        JsonObject latest = new JsonObject();
        JsonArray array = Json.fromUrlAsJsonArray(url);
        AL.debug(this.getClass(), "Got from URL:\t" + array.toString());
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.get("stable").getAsBoolean()) {
                String version = obj.get("version").getAsString();
                latest.addProperty("version", version);
                latest.addProperty("majorID", version.split("\\.")[0]);
                latest.addProperty("minorID", version.split("\\.")[1]);
                latest.addProperty("buildID", version.split("\\.")[2]);
                break;
            }
        }
        return latest;
    }

    public String getLatestDownloadUrl(String serverVersion, String loaderVersion, String installerVersion) {
        final String url = baseUrl + "/loader/" + serverVersion + "/" + loaderVersion + "/" + installerVersion + "/server/jar";
        AL.debug(this.getClass(), url);
        return url;
    }
}
