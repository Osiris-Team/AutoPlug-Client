/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.client.utils.UtilsURL;
import com.osiris.autoplug.core.json.JsonTools;
import com.osiris.autoplug.core.logger.AL;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;


public class ModrinthAPI {
    private final String baseUrl = "https://api.modrinth.com/v2";

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public SearchResult searchUpdate(MinecraftMod mod, String mcVersion) {
        if (mod.modrinthId == null && !isInt(mod.curseforgeId)) mod.modrinthId = mod.curseforgeId; // Slug
        String url = baseUrl + "/project/" + mod.modrinthId + "/version?loaders=[\"" +
                (Server.isFabric ? "fabric" : "forge") + "\"]&game_versions=[\"" + mcVersion + "\"]";
        url = new UtilsURL().clean(url);
        Exception exception = null;
        String latest = null;
        String type = ".jar";
        String downloadUrl = null;
        byte code = 0;
        try {
            if (mod.modrinthId == null)
                throw new Exception("Modrinth-id is null!"); // Modrinth id can be slug or actual id
            mod.fileDate = new JsonTools().getJsonObject("https://api.modrinth.com/api/v1/version_file/" + getSHA1(Paths.get(mod.installationPath)) + "?algorithm=sha1")
                    .get("date_published").getAsString();

            AL.debug(this.getClass(), url);
            JsonObject release = new JsonTools().getJsonArray(url)
                    .get(0).getAsJsonObject();
            latest = release.get("version_number").getAsString();
            FileTime latestDate = FileTime.from(Instant.parse(release.get("date_published").getAsString()));
            FileTime currentDate = FileTime.from(Instant.parse(mod.fileDate));
            if (latestDate.compareTo(currentDate) > 0) {
                code = 1;
            }
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
        result.mod = mod;
        result.setException(exception);
        return result;
    }

    private String getSHA1(Path file) {
        // ex. --> String shString = getSHA1(Path.of("config/renammd.jar"));

        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] dataBytes = new byte[1024];

            int nread;
            while ((nread = is.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }

            byte[] mdbytes = md.digest();

            //convert the byte to hex format
            StringBuilder sb = new StringBuilder();
            for (byte mdbyte : mdbytes) {
                sb.append(Integer.toString((mdbyte & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString().toLowerCase();
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
