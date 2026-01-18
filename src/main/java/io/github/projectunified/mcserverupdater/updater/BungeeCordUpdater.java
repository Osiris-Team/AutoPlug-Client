package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.api.JenkinsUpdater;
import io.github.projectunified.mcserverupdater.util.VersionQuery;

import java.util.regex.Pattern;

public class BungeeCordUpdater extends JenkinsUpdater {
    public BungeeCordUpdater(VersionQuery versionQuery) {
        super(versionQuery, "https://ci.md-5.net/");
    }

    @Override
    public String[] getJob() {
        return new String[]{"BungeeCord"};
    }

    @Override
    public Pattern getArtifactRegex() {
        return Pattern.compile(Pattern.quote("BungeeCord.jar"));
    }

    @Override
    public String getDefaultVersion() {
        return "1.17.1";
    }
}
