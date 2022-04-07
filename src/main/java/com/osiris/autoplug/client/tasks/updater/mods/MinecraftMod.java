/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

public class MinecraftMod {
    public String installationPath, name, version, author, modrinthId, curseforgeId, customDownloadURL;
    public boolean ignoreContentType;
    public String githubRepoName, githubAssetName;
    public String jenkinsProjectUrl, jenkinsArtifactName;
    public int jenkinsBuildId;
    public String fileDate;

    public MinecraftMod(String installationPath, String name, String version,
                        String author, String modrinthId, String curseforgeId,
                        String customDownloadURL) {
        this.installationPath = installationPath;
        this.name = (name);
        this.version = version;
        this.author = author;
        this.modrinthId = modrinthId;
        this.curseforgeId = curseforgeId;
        this.customDownloadURL = customDownloadURL;
    }
}
