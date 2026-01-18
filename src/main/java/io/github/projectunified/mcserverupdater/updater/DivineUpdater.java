package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.api.GithubReleaseUpdater;
import io.github.projectunified.mcserverupdater.util.VersionQuery;
import org.json.JSONObject;

import java.util.regex.Pattern;

public class DivineUpdater extends GithubReleaseUpdater {
    public DivineUpdater(VersionQuery versionQuery) {
        super(versionQuery, "BX-Team/DivineMC");
    }

    @Override
    public Pattern getArtifactPattern() {
        return Pattern.compile(".*\\.jar");
    }

    @Override
    public String getDefaultVersion() {
        return "1.21.4";
    }

    @Override
    public JSONObject getReleaseObject() {
        return getReleaseByTagMatch(tag -> tag.startsWith(version));
    }
}
