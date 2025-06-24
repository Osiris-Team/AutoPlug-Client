package me.hsgamer.mcserverupdater.updater;

import me.hsgamer.hscore.logger.common.Logger;
import me.hsgamer.hscore.web.UserAgent;
import me.hsgamer.hscore.web.WebUtils;
import me.hsgamer.mcserverupdater.UpdateBuilder;
import me.hsgamer.mcserverupdater.api.FileDigestChecksum;
import me.hsgamer.mcserverupdater.api.UrlInputStreamUpdater;
import me.hsgamer.mcserverupdater.util.VersionQuery;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.MessageDigest;

public class MohistUpdater implements UrlInputStreamUpdater, FileDigestChecksum {
    private final UpdateBuilder updateBuilder;
    private final String version;
    private final String build;
    private final String projectUrl;
    private final String buildUrl;
    private final String downloadUrl;

    public MohistUpdater(VersionQuery versionQuery, String project) {
        this.updateBuilder = versionQuery.updateBuilder;
        projectUrl = String.format("https://mohistmc.com/api/v2/projects/%s", project);
        String versionUrl = projectUrl + "/%s";
        buildUrl = versionUrl + "/builds";
        downloadUrl = buildUrl + "/%s/download";

        version = versionQuery.isDefault ? getDefaultVersion() : versionQuery.version;
        build = getBuild();
    }

    private String getDefaultVersion() {
        updateBuilder.debug("Getting default version from " + projectUrl);
        try {
            URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(projectUrl));
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONArray builds = jsonObject.getJSONArray("versions");
            return builds.getString(builds.length() - 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getBuild() {
        String formattedUrl = String.format(buildUrl, version);
        updateBuilder.debug("Getting latest build from " + formattedUrl);
        try {
            URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(formattedUrl));
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONArray builds = jsonObject.getJSONArray("builds");
            int lastIndex = builds.length() - 1;
            return Integer.toString(builds.getJSONObject(lastIndex).getInt("number"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JSONObject getDownload() throws IOException {
        String formattedUrl = String.format(buildUrl, version);
        updateBuilder.debug("Getting download from " + formattedUrl);
        URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(formattedUrl));
        InputStream inputStream = connection.getInputStream();
        JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
        JSONArray builds = jsonObject.getJSONArray("builds");
        return builds.getJSONObject(builds.length() - 1); // Return the last build's JSONObject
    }

    @Override
    public String getChecksum() {
        try {
            JSONObject download = getDownload();
            return download.getString("fileMd5");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public MessageDigest getMessageDigest() throws Exception {
        return MessageDigest.getInstance("MD5");
    }

    @Override
    public String getFileUrl() {
        return String.format(downloadUrl, version, build);
    }

    @Override
    public Logger getLogger() {
        return updateBuilder.logger();
    }
}
