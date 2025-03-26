package com.osiris.autoplug.client.tasks.updater.plugins;

/**
 * Holds source metadata used during plugin update processes.
 * This class encapsulates all external source information like GitHub, Jenkins, and Modrinth IDs.
 */
public class PluginSourceInfo {
    private String githubRepoName;
    private String githubAssetName;
    private String jenkinsProjectUrl;
    private String jenkinsArtifactName;
    private int jenkinsBuildId;
    private String modrinthId;

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

    public String getModrinthId() {
        return modrinthId;
    }

    public void setModrinthId(String modrinthId) {
        this.modrinthId = modrinthId;
    }
}
