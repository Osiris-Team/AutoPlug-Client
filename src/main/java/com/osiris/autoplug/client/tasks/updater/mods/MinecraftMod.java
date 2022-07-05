/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

public class MinecraftMod {
    public String installationPath, name, modrinthId, curseforgeId, customDownloadURL;
    public boolean ignoreContentType;
    public String githubRepoName, githubAssetName;
    public String jenkinsProjectUrl, jenkinsArtifactName;
    public int jenkinsBuildId;
    private String author, version;

    public MinecraftMod(String installationPath, String name, String version,
                        String author, String modrinthId, String curseforgeId,
                        String customDownloadURL) {
        this.installationPath = installationPath;
        this.name = name;
        setAuthor(author);
        setVersion(version);
        this.modrinthId = modrinthId;
        this.curseforgeId = curseforgeId;
        this.customDownloadURL = customDownloadURL;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        if (version != null)
            this.version = version.replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        if (author != null)
            this.author = author.replaceAll("[^\\w]", ""); // Before passing over remove everything except chars and numbers
    }
}
