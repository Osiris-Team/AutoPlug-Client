/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.core.json.JsonTools;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import com.osiris.autoplug.core.json.exceptions.WrongJsonTypeException;

import java.io.IOException;

public class FabricDownloadsAPI {
    private final String baseUrl = "https://meta.fabricmc.net/v2/versions";

    public JsonObject getLatestLoader() throws WrongJsonTypeException, IOException, HttpErrorException {
        final String address = baseUrl + "/loader?limit=1";
        JsonObject latest = new JsonObject();
        for (JsonElement element : new JsonTools().getJsonArray(address)) {
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
        final String address = baseUrl + "/installer?limit=1";
        JsonObject latest = new JsonObject();
        for (JsonElement element : new JsonTools().getJsonArray(address)) {
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
        return baseUrl + "/loader/" + serverVersion + "/" + loaderVersion + "/" + installerVersion + "/server/jar";
    }
}
