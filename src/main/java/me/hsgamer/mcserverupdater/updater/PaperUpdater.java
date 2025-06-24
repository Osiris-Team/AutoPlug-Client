package me.hsgamer.mcserverupdater.updater;

import me.hsgamer.mcserverupdater.api.BibliothekUpdater;
import me.hsgamer.mcserverupdater.util.VersionQuery;
import org.json.JSONObject;

import java.security.MessageDigest;

public class PaperUpdater extends BibliothekUpdater {
    public PaperUpdater(VersionQuery versionQuery, String project) {
        super(versionQuery, project);
    }

    @Override
    protected String getApiUrl() {
        return "https://api.papermc.io/v2";
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
