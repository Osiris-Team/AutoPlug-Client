package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.api.BibliothekUpdater;
import io.github.projectunified.mcserverupdater.util.VersionQuery;
import org.json.JSONObject;

import java.security.MessageDigest;

public class GeyserUpdater extends BibliothekUpdater {
    public GeyserUpdater(VersionQuery versionQuery) {
        super(versionQuery, "geyser");
    }

    @Override
    protected String getApiUrl() {
        return "https://download.geysermc.org/v2";
    }

    @Override
    protected String getChecksumAlgorithm() {
        return "sha256";
    }

    @Override
    protected String getDownloadKey() {
        return "standalone";
    }

    @Override
    protected String getDownloadName(JSONObject downloadObject) {
        return "standalone";
    }

    @Override
    public MessageDigest getMessageDigest() throws Exception {
        return MessageDigest.getInstance("SHA-256");
    }
}
