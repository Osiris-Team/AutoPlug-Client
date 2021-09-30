package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonArray;
import com.osiris.autoplug.core.json.JsonTools;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import com.osiris.autoplug.core.json.exceptions.WrongJsonTypeException;
import com.osiris.autoplug.core.logger.AL;

import java.io.IOException;

public class PurpurDownloadsAPI {
    private String baseUrl = "https://api.pl3x.net/v2";

    public int getLatestBuildId(String name, String mc_version) throws WrongJsonTypeException, IOException, HttpErrorException {
        int result = 0;
        final String address = baseUrl+"/" + name + "/" + mc_version+"/latest";
        result = new JsonTools().getJsonObject(address).getAsJsonObject().get("build").getAsInt();
        AL.debug(this.getClass(), "build-id=" + result);
        return result;
    }

}
