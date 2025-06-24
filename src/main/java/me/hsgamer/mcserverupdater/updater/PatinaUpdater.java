package me.hsgamer.mcserverupdater.updater;

import me.hsgamer.mcserverupdater.api.GithubBranchUpdater;
import me.hsgamer.mcserverupdater.util.VersionQuery;

import java.util.regex.Pattern;

public class PatinaUpdater extends GithubBranchUpdater {
    public PatinaUpdater(VersionQuery versionQuery) {
        super(versionQuery, "PatinaMC/Patina");
    }

    @Override
    public String getBranch() {
        return "releases/" + version;
    }

    @Override
    public Pattern getFilePattern() {
        return Pattern.compile(".*\\.jar");
    }

    @Override
    public String getDefaultVersion() {
        return "1.17.1";
    }
}
