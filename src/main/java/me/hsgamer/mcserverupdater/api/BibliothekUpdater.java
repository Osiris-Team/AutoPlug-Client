package me.hsgamer.mcserverupdater.api;

import me.hsgamer.hscore.logger.common.Logger;
import me.hsgamer.hscore.web.UserAgent;
import me.hsgamer.hscore.web.WebUtils;
import me.hsgamer.mcserverupdater.UpdateBuilder;
import me.hsgamer.mcserverupdater.util.VersionQuery;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

public abstract class BibliothekUpdater implements UrlInputStreamUpdater, FileDigestChecksum {
    private final UpdateBuilder updateBuilder;
    private final String version;
    private final String build;
    private final String projectUrl;
    private final String versionUrl;
    private final String buildUrl;
    private final String downloadUrl;

    protected BibliothekUpdater(VersionQuery versionQuery, String project) {
        this.updateBuilder = versionQuery.updateBuilder;
        String apiUrl = getApiUrl();
        projectUrl = apiUrl + "/projects/" + project;
        versionUrl = projectUrl + "/versions/%s";
        buildUrl = versionUrl + "/builds/%s";
        downloadUrl = buildUrl + "/downloads/%s";

        version = versionQuery.isDefault ? getDefaultVersion() : versionQuery.version;
        build = getBuild();
    }

    protected abstract String getApiUrl();

    protected abstract String getChecksumAlgorithm();

    protected abstract String getDownloadKey();

    protected abstract String getDownloadName(JSONObject downloadObject);

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
        String formattedUrl = String.format(versionUrl, version);
        updateBuilder.debug("Getting latest build from " + formattedUrl);
        try {
            URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(formattedUrl));
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONArray builds = jsonObject.getJSONArray("builds");
            return Integer.toString(builds.getInt(builds.length() - 1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JSONObject getDownload() throws IOException {
        String formattedUrl = String.format(buildUrl, version, build);
        updateBuilder.debug("Getting download from " + formattedUrl);
        URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(formattedUrl));
        InputStream inputStream = connection.getInputStream();
        JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
        JSONObject downloads = jsonObject.getJSONObject("downloads");
        return downloads.getJSONObject(getDownloadKey());
    }

    @Override
    public final String getChecksum() {
        try {
            JSONObject application = getDownload();
            return application.getString(getChecksumAlgorithm());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public final String getFileUrl() {
        String downloadName;
        try {
            JSONObject application = getDownload();
            downloadName = getDownloadName(application);
        } catch (Exception e) {
            debug(e);
            return null;
        }
        return String.format(downloadUrl, version, build, downloadName);
    }

    @Override
    public final Logger getLogger() {
        return updateBuilder.logger();
    }
}
