/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.logger.AL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JenkinsSearch {

    public SearchResult search(String project_url, String providedArtifactName, int build_id) {
        Exception exception = null;
        SearchResult.Type resultType = SearchResult.Type.UP_TO_DATE;
        String download_url = null;
        String downloadType = ".jar";
        String latestVersion = null;
        int latest_build_id = 0;
        String fileName = null;

        try {
            JsonObject json_project = Json.getAsObject(project_url + (project_url.endsWith("/") ? "" : "/") + "api/json");
            JsonObject json_last_successful_build = json_project.get("lastSuccessfulBuild").getAsJsonObject();
            latest_build_id = json_last_successful_build.get("number").getAsInt();
            latestVersion = String.valueOf(latest_build_id);
            if (latest_build_id > build_id)
                resultType = SearchResult.Type.UPDATE_AVAILABLE;

            String buildUrl = json_last_successful_build.get("url").getAsString();
            if (!buildUrl.endsWith("api/json"))
                buildUrl = buildUrl + (buildUrl.endsWith("/") ? "" : "/") + "api/json";
            JsonArray arrayArtifacts = Json.getAsObject(buildUrl).getAsJsonArray("artifacts");

            // Contains JsonObjects sorted by their artifact names lengths, from smallest to longest.
            // The following does that sorting.
            List<JsonObject> sortedArtifactObjects = new ArrayList<>();
            for (JsonElement e :
                    arrayArtifacts) {
                JsonObject obj = e.getAsJsonObject();
                String name = obj.get("fileName").getAsString();
                if (sortedArtifactObjects.size() == 0) sortedArtifactObjects.add(obj);
                else {
                    int finalIndex = 0;
                    boolean isSmaller = false;
                    for (int i = 0; i < sortedArtifactObjects.size(); i++) {
                        String n = sortedArtifactObjects.get(i).get("fileName").getAsString();
                        if (name.length() < n.length()) {
                            isSmaller = true;
                            finalIndex = i;
                            break;
                        }
                    }
                    if (!isSmaller) sortedArtifactObjects.add(obj);
                    else sortedArtifactObjects.add(finalIndex, obj);
                }
            }

            // Find artifact-name containing our provided artifact-name
            for (JsonObject obj : sortedArtifactObjects) {
                String n = obj.get("fileName").getAsString();
                if (n.contains(providedArtifactName)) {
                    fileName = n;
                    download_url = project_url + "/" + latest_build_id + "/artifact/" + obj.get("relativePath").getAsString();
                    if (fileName.contains("."))
                        downloadType = fileName.substring(fileName.lastIndexOf("."));
                    break;
                }
            }

            if (download_url == null) {
                List<String> names = new ArrayList<>();
                for (JsonObject obj :
                        sortedArtifactObjects) {
                    String n = obj.get("fileName").getAsString();
                    names.add(n);
                }
                throw new Exception("Failed to find an artifact-name containing '" + providedArtifactName + "' inside of '" + Arrays.toString(names.toArray()) + "'!");
            }
        } catch (Exception e) {
            exception = e;
            resultType = SearchResult.Type.API_ERROR;
            AL.debug(this.getClass(), "Jenkins API failed, attempting HTML fallback: " + e.getMessage());
            return searchHtmlFallback(project_url, providedArtifactName, build_id, exception);
        }

        SearchResult rs = new SearchResult(null, resultType, latestVersion, download_url, downloadType, null, null, false);
        rs.setException(exception);
        rs.jenkinsId = latest_build_id;
        rs.fileName = fileName;
        return rs;
    }

    public SearchResult searchHtmlFallback(String project_url,
                                           String providedArtifactName,
                                           int build_id,
                                           Exception apiException)
    {
        int latest_build_id = 0;
        String latestVersion = null;
        String download_url = null;
        String downloadType = ".jar";
        String fileName = null;
        SearchResult.Type resultType = SearchResult.Type.UP_TO_DATE;

        try
        {
            URL url = new URL(project_url);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "AutoPlug Client - https://autoplug.one");
            conn.setRequestProperty("Accept", "text/html");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            Document doc = Jsoup.parse(conn.getInputStream(), "UTF-8", project_url);
            conn.disconnect();

            /*
             * Parse artifact links directly.
             *
             * Example:
             * /job/Citizens2/4180/artifact/dist/target/Citizens-2.0.42-b4180.jar
             */
            Elements artifactLinks = doc.select("a[href*=\"/artifact/\"]");

            Pattern artifactPattern = Pattern.compile(
                    "/job/[^/]+/(\\d+)/artifact/(.+)"
            );

            for (Element link : artifactLinks)
            {
                String href = link.attr("href");
                String text = link.text().trim();

                Matcher matcher = artifactPattern.matcher(href);

                if (!matcher.find())
                    continue;

                int parsedBuildId = Integer.parseInt(matcher.group(1));

                // Keep newest build
                if (parsedBuildId > latest_build_id)
                {
                    latest_build_id = parsedBuildId;
                }

                boolean matchesRequested =
                        providedArtifactName == null
                                || providedArtifactName.isEmpty()
                                || text.contains(providedArtifactName);

                boolean isJar = text.endsWith(".jar");

                if (matchesRequested || (download_url == null && isJar))
                {
                    fileName = text;

                    if (fileName.contains("."))
                    {
                        downloadType = fileName.substring(fileName.lastIndexOf("."));
                    }

                    download_url = href.startsWith("http")
                            ? href
                            : new URL(new URL(project_url), href).toString();

                    // Prefer exact match immediately
                    if (matchesRequested)
                    {
                        break;
                    }
                }
            }

            /*
             * Secondary fallback:
             * Parse permalink text like:
             * "Letzter erfolgreicher Build (#4180)"
             */
            if (latest_build_id == 0)
            {
                Elements permalinkLinks =
                        doc.select("ul.permalinks-list a.permalink-link");

                Pattern buildPattern = Pattern.compile("#(\\d+)");

                for (Element link : permalinkLinks)
                {
                    Matcher matcher = buildPattern.matcher(link.text());

                    if (matcher.find())
                    {
                        latest_build_id = Integer.parseInt(matcher.group(1));
                        break;
                    }
                }
            }

            if (latest_build_id == 0)
            {
                throw new Exception("Could not determine latest build ID");
            }

            if (download_url == null)
            {
                throw new Exception("Could not find artifact download URL");
            }

            latestVersion = String.valueOf(latest_build_id);

            if (latest_build_id > build_id)
            {
                resultType = SearchResult.Type.UPDATE_AVAILABLE;
            }

            AL.debug(this.getClass(),
                    "HTML fallback succeeded: build="
                            + latest_build_id
                            + " file="
                            + fileName);

        }
        catch (Exception e)
        {
            AL.debug(this.getClass(),
                    "HTML fallback also failed: "
                            + e.getMessage());

            SearchResult rs = new SearchResult(
                    null,
                    SearchResult.Type.API_ERROR,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
            );

            rs.setException(apiException);
            return rs;
        }

        SearchResult rs = new SearchResult(
                null,
                resultType,
                latestVersion,
                download_url,
                downloadType,
                null,
                null,
                false
        );

        rs.jenkinsId = latest_build_id;
        rs.fileName = fileName;

        return rs;
    }

}
