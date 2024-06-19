package com.osiris.autoplug.client.tasks.updater.search;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.logger.AL;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public class CustomUpdateCheck {

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SearchResult checkUpdate(MinecraftPlugin plugin, String mcVersion) {

        String url = baseUrl + "/project/" + id + "/version?loaders=[\"" + loader + "\"]&game_versions=[\"" + mcVersion + "\"]";
        url = new UtilsURL().clean(url);
        Exception exception = null;
        String latest = null;
        String type = ".jar";
        String downloadUrl = null;
        byte code = 0;
        try {
            if (id == null)
                throw new Exception("Modrinth-id is null!"); // Modrinth id can be slug or actual id

            AL.debug(this.getClass(), url);
            JsonObject release;
            try {
                release = Json.getAsJsonArray(url)
                        .get(0).getAsJsonObject();
            } catch (Exception e) {
                if (!isInt(id)) { // Try another url, with slug replaced _ with -
                    url = baseUrl + "/project/" + id.replace("_", "-")
                            + "/version?loaders=[\"" +
                            loader + "\"]" + (forceLatest ? "" : "&game_versions=[\"" + mcVersion + "\"]");
                    AL.debug(this.getClass(), url);
                    release = Json.getAsJsonArray(url)
                            .get(0).getAsJsonObject();
                } else
                    throw e;
            }

            latest = release.get("version_number").getAsString().replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots
            if (new File(installPath).lastModified() < Instant.parse(release.get("date_published").getAsString()).toEpochMilli())
                code = 1;
            JsonObject releaseDownload = release.getAsJsonArray("files").get(0).getAsJsonObject();
            downloadUrl = releaseDownload.get("url").getAsString();
            try {
                String fileName = releaseDownload.get("filename").getAsString();
                type = fileName.substring(fileName.lastIndexOf("."));
            } catch (Exception e) {
            }
        } catch (Exception e) {
            exception = e;
            code = 2;
        }
        SearchResult result = new SearchResult(null, code, latest, downloadUrl, type, null, null, false);
        result.setException(exception);
        return result;
    }
}