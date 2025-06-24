package me.hsgamer.mcserverupdater.updater;

import me.hsgamer.mcserverupdater.api.GithubReleaseUpdater;
import me.hsgamer.mcserverupdater.util.VersionQuery;
import org.json.JSONObject;

import java.util.regex.Pattern;

public class LeafUpdater extends GithubReleaseUpdater {
    public LeafUpdater(VersionQuery versionQuery) {
        super(versionQuery, "Winds-Studio/Leaf");
    }

    @Override
    public Pattern getArtifactPattern() {
        return Pattern.compile("leaf-[\\d.]+(-(\\d+|mojmap))?\\.jar");
    }

    @Override
    public String getDefaultVersion() {
        return "1.21.1";
    }

    @Override
    public JSONObject getReleaseObject() {
        return getReleaseByTag("ver-" + version);
    }
}
