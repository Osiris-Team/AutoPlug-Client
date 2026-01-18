package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.api.BibliothekUpdater;
import io.github.projectunified.mcserverupdater.util.VersionQuery;
import org.json.JSONObject;

import java.security.MessageDigest;

public class MultiPaperUpdater extends BibliothekUpdater {
    private final String downloadKey;

    public MultiPaperUpdater(VersionQuery versionQuery, String project, String downloadKey) {
        super(versionQuery, project);
        this.downloadKey = downloadKey;
    }

    public MultiPaperUpdater(VersionQuery versionQuery, String project) {
        this(versionQuery, project, "application");
    }

    @Override
    protected String getApiUrl() {
        return "https://api.multipaper.io/v2";
    }

    @Override
    protected String getChecksumAlgorithm() {
        return "sha256";
    }

    @Override
    protected String getDownloadKey() {
        return downloadKey;
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
