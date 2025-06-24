package me.hsgamer.mcserverupdater.updater;

import me.hsgamer.mcserverupdater.api.BibliothekUpdater;
import me.hsgamer.mcserverupdater.util.VersionQuery;
import org.json.JSONObject;

import java.security.MessageDigest;

public class PandaSpigotUpdater extends BibliothekUpdater {
    public PandaSpigotUpdater(VersionQuery versionQuery, String project) {
        super(versionQuery, project);
    }

    @Override
    protected String getApiUrl() {
        return "https://downloads.hpfxd.com/v2";
    }

    @Override
    protected String getChecksumAlgorithm() {
        return "sha256";
    }

    @Override
    protected String getDownloadKey() {
        return "paperclip";
    }

    @Override
    protected String getDownloadName(JSONObject downloadObject) {
        return "paperclip";
    }

    @Override
    public MessageDigest getMessageDigest() throws Exception {
        return MessageDigest.getInstance("SHA-256");
    }
}
