/*
 * Copyright (c) 2022-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

public class MinecraftMod {
    public String installationPath, modrinthId, curseforgeId, customDownloadURL;
    public boolean ignoreContentType;
    public String githubRepoName, githubAssetName;
    public String jenkinsProjectUrl, jenkinsArtifactName;
    public int jenkinsBuildId;
    public boolean forceLatest;
    private String name, author, version;

    public MinecraftMod(String installationPath, String name, String version,
                        String author, String modrinthId, String curseforgeId,
                        String customDownloadURL) {
        this.installationPath = installationPath;
        setName(name);
        setAuthor(author);
        setVersion(version);
        this.modrinthId = modrinthId;
        this.curseforgeId = curseforgeId;
        this.customDownloadURL = customDownloadURL;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null)
            this.name = name.replaceAll(":", ""); // Before passing over remove : numbers and dots
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
