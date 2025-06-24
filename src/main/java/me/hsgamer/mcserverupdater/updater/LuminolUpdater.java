package me.hsgamer.mcserverupdater.updater;

import me.hsgamer.mcserverupdater.api.GithubReleaseUpdater;
import me.hsgamer.mcserverupdater.util.VersionQuery;
import org.json.JSONObject;

import java.util.regex.Pattern;

public class LuminolUpdater extends GithubReleaseUpdater {
    public LuminolUpdater(VersionQuery versionQuery) {
        super(versionQuery, "LuminolMC/Luminol");
    }

    @Override
    public Pattern getArtifactPattern() {
        return Pattern.compile("luminol-.+-paperclip\\.jar");
    }

    @Override
    public String getDefaultVersion() {
        return "1.21.1";
    }

    @Override
    public JSONObject getReleaseObject() {
        return getReleaseByTagMatch(tag -> tag.startsWith(version));
    }
}
