/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

public class MinecraftPlugin {
    private boolean isPremium;
    private String configPath;
    private String installationPath;
    private String name;
    private String version;
    private String author;
    private int spigotId;
    private int bukkitId;
    private boolean ignoreContentType;
    private String customDownloadURL;
    private String githubRepoName;
    private String githubAssetName;
    private String jenkinsProjectUrl;
    private String jenkinsArtifactName;
    private int jenkinsBuildId;

    public MinecraftPlugin(String installationPath, String name, String version, String author, int spigotId, int bukkitId, String customDownloadURL) {
        this.setInstallationPath(installationPath);
        this.setName(name);
        this.version = version;
        this.author = author;
        this.spigotId = spigotId;
        this.bukkitId = bukkitId;
        this.customDownloadURL = customDownloadURL;
    }

    public String getJenkinsProjectUrl() {
        return jenkinsProjectUrl;
    }

    public void setJenkinsProjectUrl(String jenkinsProjectUrl) {
        this.jenkinsProjectUrl = jenkinsProjectUrl;
    }

    public String getJenkinsArtifactName() {
        return jenkinsArtifactName;
    }

    public void setJenkinsArtifactName(String jenkinsArtifactName) {
        this.jenkinsArtifactName = jenkinsArtifactName;
    }

    public int getJenkinsBuildId() {
        return jenkinsBuildId;
    }

    public void setJenkinsBuildId(int jenkinsBuildId) {
        this.jenkinsBuildId = jenkinsBuildId;
    }

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public boolean isIgnoreContentType() {
        return ignoreContentType;
    }

    /**
     * Example: Osiris-Team/AutoPlug-Client
     *
     * @return
     */
    public String getGithubRepoName() {
        return githubRepoName;
    }

    public void setGithubRepoName(String githubRepoName) {
        this.githubRepoName = githubRepoName;
    }

    public String getGithubAssetName() {
        return githubAssetName;
    }

    public void setGithubAssetName(String githubAssetName) {
        this.githubAssetName = githubAssetName;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public void setPremium(boolean premium) {
        isPremium = premium;
    }

    public String getInstallationPath() {
        return installationPath;
    }

    public void setInstallationPath(String installationPath) {
        this.installationPath = installationPath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getSpigotId() {
        return spigotId;
    }

    public void setSpigotId(int spigotId) {
        this.spigotId = spigotId;
    }

    public int getBukkitId() {
        return bukkitId;
    }

    public void setBukkitId(int bukkitId) {
        this.bukkitId = bukkitId;
    }

    public boolean getIgnoreContentType() {
        return ignoreContentType;
    }

    public void setIgnoreContentType(boolean ignoreContentType) {
        this.ignoreContentType = ignoreContentType;
    }

    public String getCustomDownloadURL() {
        return customDownloadURL;
    }

    public void setCustomDownloadURL(String customDownloadURL) {
        this.customDownloadURL = customDownloadURL;
    }

    public String toPrintString() {
        return "name='" + name + "' version='" + version + "' author='" + author + "' path='" + installationPath + "'";
    }
}
