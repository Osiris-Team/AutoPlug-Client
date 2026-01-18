package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.UpdateBuilder;
import io.github.projectunified.mcserverupdater.api.Checksum;
import io.github.projectunified.mcserverupdater.api.DebugConsumer;
import io.github.projectunified.mcserverupdater.api.InputStreamUpdater;
import io.github.projectunified.mcserverupdater.api.checksum.FileDigestChecksum;
import io.github.projectunified.mcserverupdater.util.VersionQuery;
import io.github.projectunified.mcserverupdater.util.WebUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.MessageDigest;

public class MohistUpdater implements InputStreamUpdater, FileDigestChecksum {
    private final UpdateBuilder updateBuilder;
    private final String version;
    private final String projectUrl;
    private final String buildUrl;
    private final String downloadUrl;

    public MohistUpdater(VersionQuery versionQuery, String project) {
        this.updateBuilder = versionQuery.updateBuilder;
        projectUrl = String.format("https://api.mohistmc.com/project/%s", project);
        String versionUrl = projectUrl + "/%s";
        buildUrl = versionUrl + "/builds/latest";
        downloadUrl = buildUrl + "/download";

        version = versionQuery.isDefault ? getDefaultVersion() : versionQuery.version;
    }

    private String getDefaultVersion() {
        updateBuilder.debug("Getting default version from " + projectUrl);
        try {
            URLConnection connection = WebUtils.openConnection(projectUrl, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONArray jsonObject = new JSONArray(new JSONTokener(inputStream));
            JSONObject versionObject = jsonObject.getJSONObject(0);
            return versionObject.getString("name");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JSONObject getDownload() throws IOException {
        String formattedUrl = String.format(buildUrl, version);
        updateBuilder.debug("Getting download from " + formattedUrl);
        URLConnection connection = WebUtils.openConnection(formattedUrl, updateBuilder);
        InputStream inputStream = connection.getInputStream();
        return new JSONObject(new JSONTokener(inputStream));
    }

    @Override
    public String getChecksum() {
        try {
            JSONObject download = getDownload();
            return download.getString("file_sha256");
        } catch (Exception e) {
            debug("Failed to get checksum", e);
            return null;
        }
    }

    @Override
    public MessageDigest getMessageDigest() throws Exception {
        return MessageDigest.getInstance("SHA-256");
    }

    @Override
    public InputStream getInputStream() {
        return WebUtils.getInputStreamOrNull(String.format(downloadUrl, version), updateBuilder);
    }

    @Override
    public Checksum getChecksumChecker() {
        return this;
    }

    @Override
    public DebugConsumer getDebugConsumer() {
        return updateBuilder.debugConsumer();
    }
}
