package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.api.BibliothekUpdater;
import io.github.projectunified.mcserverupdater.util.VersionQuery;
import org.json.JSONObject;

import java.security.MessageDigest;

public class LeavesUpdater extends BibliothekUpdater {
    public LeavesUpdater(VersionQuery versionQuery, String project) {
        super(versionQuery, project);
    }

    @Override
    protected String getApiUrl() {
        return "https://api.leavesmc.org/v2";
    }

    @Override
    protected String getChecksumAlgorithm() {
        return "sha256";
    }

    @Override
    protected String getDownloadKey() {
        return "application";
    }

    @Override
    protected String getDownloadName(JSONObject downloadObject) {
        return downloadObject.getString("name");
    }

    @Override
    public MessageDigest getMessageDigest() throws Exception {
        return MessageDigest.getInstance("SHA-256");
    }
}
