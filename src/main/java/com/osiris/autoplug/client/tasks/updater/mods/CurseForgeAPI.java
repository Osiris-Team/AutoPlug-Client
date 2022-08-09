/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.autoplug.client.utils.UtilsVersion;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.sort.QuickSort;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CurseForgeAPI {
    private final String baseUrl = "https://api.curseforge.com/v1";

    /**
     * Requires curseforgeId not null.
     */
    public SearchResult searchUpdate(MinecraftMod mod, String mcVersion, boolean checkNameForModLoader) {
        boolean isIdNumber = isInt(mod.curseforgeId);
        String url;
        Exception exception = null;
        String latest = null;
        String type = ".jar";
        String downloadUrl = null;
        byte code = 0;
        String modInfo = mod.name + "/" + (Server.isFabric ? "fabric" : "forge");
        try {
            if (!isIdNumber) { // Determine project id, since we only got slug
                try {
                    JsonObject json = JsonParser.parseString(sendCurseforgePost(getCurseforgeMurmurHash(Paths.get(mod.installationPath)))).getAsJsonObject();
                    mod.curseforgeId = json.getAsJsonObject("data").get("exactMatches").getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
                } catch (Exception e) {
                    throw new Exception("Failed to determine curseforge-id!", e);
                }
            }
            if (mod.curseforgeId == null) throw new Exception("Failed to determine curseforge-id!");
            modInfo += "/" + mod.curseforgeId;
            url = baseUrl + "/mods/" + mod.curseforgeId + "/files";
            url = new UtilsURL().clean(url);
            AL.debug(this.getClass(), modInfo + " fetch details from: " + url);
            JsonArray arr;
            try {
                arr = new CurseForgeJson().getJsonObject(url).get("data").getAsJsonArray();
            } catch (Exception e) {
                if (!isInt(mod.curseforgeId)) { // Try another url, with slug replaced _ with -
                    url = baseUrl + "/mods/" + mod.curseforgeId.replace("_", "-") + "/files";
                    url = new UtilsURL().clean(url);
                    AL.debug(this.getClass(), modInfo + " fetch details from: " + url);
                    arr = new CurseForgeJson().getJsonObject(url).get("data").getAsJsonArray();
                } else
                    throw e;
            }
            // Compares this object with the specified object for order.
            // Returns a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified objec
            new QuickSort().sortJsonArray(arr, (thisEl, otherEl) -> {
                int thisId = thisEl.el.getAsJsonObject().get("id").getAsInt();
                int otherId = otherEl.el.getAsJsonObject().get("id").getAsInt();
                return Integer.compare(thisId, otherId);
            });
            JsonObject release = null;
            for (int i = arr.size() - 1; i >= 0; i--) {
                JsonObject tempRelease = arr.get(i).getAsJsonObject();
                boolean isVersionCompatible = false, isModLoaderCompatible = false;
                for (JsonElement el : tempRelease.get("gameVersions").getAsJsonArray()) {
                    if (el.getAsString().equals(mcVersion)) {
                        isVersionCompatible = true;
                        break;
                    }
                }

                // If the release has no fabric or forge tag, then we expect only forge support.
                if (Server.isFabric) { // FABRIC
                    for (JsonElement el : tempRelease.get("gameVersions").getAsJsonArray()) { // check if game versions contain fabric
                        if (StringUtils.containsIgnoreCase(el.getAsString(), "fabric")) {
                            isModLoaderCompatible = true;
                            break;
                        }
                    }
                    if (checkNameForModLoader && !isModLoaderCompatible) // check if name contains fabric
                        if (StringUtils.containsIgnoreCase(
                                tempRelease.get("fileName").getAsString(),
                                "fabric")) {
                            isModLoaderCompatible = true;
                        }
                } else { // FORGE
                    isModLoaderCompatible = true; // since no fabric/forge tag == forge is supported,
                    // we only need to check if it has no fabric tag
                    for (JsonElement el : tempRelease.get("gameVersions").getAsJsonArray()) {
                        if (StringUtils.containsIgnoreCase(el.getAsString(), "fabric")) {
                            isModLoaderCompatible = false;
                            break;
                        }
                    }
                }

                if (isVersionCompatible && isModLoaderCompatible) {
                    release = tempRelease;
                    break;
                }
            }
            if (release == null)
                throw new Exception("Failed to find a single release of this mod for mc version " + mcVersion);
            try {
                latest = release.get("fileName").getAsString().replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots
            } catch (Exception e) {
                throw new Exception("Failed to determine latest mod version!", e);
            }
            if (new UtilsVersion().compare(mod.getVersion(), latest))
                code = 1;
            downloadUrl = release.get("downloadUrl").getAsString();
            try {
                String fileName = release.get("fileName").getAsString();
                type = fileName.substring(fileName.lastIndexOf("."));
            } catch (Exception e) {
            }
        } catch (Exception e) {
            exception = e;
            code = 2;
        }
        SearchResult result = new SearchResult(null, code, latest, downloadUrl, type, null, null, false);
        result.mod = mod;
        result.setException(exception);
        return result;
    }


    // UTIL METHODS:


    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String sendCurseforgePost(String murmurHash) throws Exception {
        String body = "{\n" +
                "  \"fingerprints\": [\n" +
                "    " + murmurHash + "\n" +
                "  ]\n" +
                "}";

        HttpURLConnection urlConn;
        URL mUrl = new URL(baseUrl + "/fingerprints");
        urlConn = (HttpURLConnection) mUrl.openConnection();
        urlConn.setDoOutput(true);
        urlConn.addRequestProperty("Accept", "application/json");
        urlConn.addRequestProperty("x-api-key", new CurseForgeJson().key);
        urlConn.addRequestProperty("Content-Type", "application/json");
        urlConn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        StringBuilder content;
        BufferedReader br = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
        String line;
        content = new StringBuilder();
        while ((line = br.readLine()) != null) {
            content.append(line);
        }
        urlConn.disconnect();

        return content.toString();
    }

    private String getCurseforgeMurmurHash(Path file) throws IOException {
        final int m = 0x5bd1e995;
        final int r = 24;
        long k = 0x0L;
        int seed = 1;
        int shift = 0x0;

        // get file size
        long flength = Files.size(file);

        // convert file to byte array
        byte[] byteFile = Files.readAllBytes(file);

        long length = 0;
        char b;
        // get good bytes from file
        for (int i = 0; i < flength; i++) {
            b = (char) byteFile[i];

            if (b == 0x9 || b == 0xa || b == 0xd || b == 0x20) {
                continue;
            }

            length += 1;
        }
        long h = (seed ^ length);

        for (int i = 0; i < flength; i++) {
            b = (char) byteFile[i];

            if (b == 0x9 || b == 0xa || b == 0xd || b == 0x20) {
                continue;
            }

            if (b > 255) {
                while (b > 255) {
                    b -= 255;
                }
            }

            k = k | ((long) b << shift);

            shift = shift + 0x8;

            if (shift == 0x20) {
                h = 0x00000000FFFFFFFFL & h;

                k = k * m;
                k = 0x00000000FFFFFFFFL & k;

                k = k ^ (k >> r);
                k = 0x00000000FFFFFFFFL & k;

                k = k * m;
                k = 0x00000000FFFFFFFFL & k;

                h = h * m;
                h = 0x00000000FFFFFFFFL & h;

                h = h ^ k;
                h = 0x00000000FFFFFFFFL & h;

                k = 0x0;
                shift = 0x0;
            }
        }

        if (shift > 0) {
            h = h ^ k;
            h = 0x00000000FFFFFFFFL & h;

            h = h * m;
            h = 0x00000000FFFFFFFFL & h;
        }

        h = h ^ (h >> 13);
        h = 0x00000000FFFFFFFFL & h;

        h = h * m;
        h = 0x00000000FFFFFFFFL & h;

        h = h ^ (h >> 15);
        h = 0x00000000FFFFFFFFL & h;

        return String.valueOf(h);
    }

}
