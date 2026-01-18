package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.api.JenkinsUpdater;
import io.github.projectunified.mcserverupdater.util.VersionQuery;

import java.util.regex.Pattern;

public class CanvasUpdater extends JenkinsUpdater {
    public CanvasUpdater(VersionQuery versionQuery) {
        super(versionQuery, "https://jenkins.canvasmc.io/");
    }

    @Override
    public String[] getJob() {
        return new String[]{"Canvas"};
    }

    @Override
    public Pattern getArtifactRegex() {
        return Pattern.compile(".*\\.jar");
    }

    @Override
    public String getDefaultVersion() {
        return "1.21.4";
    }

    @Override
    protected String getBuild() {
        return getSuccessfulBuildByNameMatch(name -> name.contains(version));
    }
}
