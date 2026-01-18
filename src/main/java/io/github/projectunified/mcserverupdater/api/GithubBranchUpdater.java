package io.github.projectunified.mcserverupdater.api;

import io.github.projectunified.mcserverupdater.UpdateBuilder;
import io.github.projectunified.mcserverupdater.api.checksum.SimpleChecksum;
import io.github.projectunified.mcserverupdater.util.VersionQuery;
import io.github.projectunified.mcserverupdater.util.WebUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.regex.Pattern;

public abstract class GithubBranchUpdater implements SimpleChecksum, InputStreamUpdater {
    protected final UpdateBuilder updateBuilder;
    protected final String version;
    protected final String build;
    private final String refLatestCommitUrl;
    private final String downloadUrl;
    private final String filesUrl;

    protected GithubBranchUpdater(VersionQuery versionQuery, String repo) {
        String apiUrl = "https://api.github.com/repos/" + repo + "/";
        this.refLatestCommitUrl = apiUrl + "commits/heads/%s";
        this.downloadUrl = "https://github.com/" + repo + "/raw/%s/%s";
        this.filesUrl = apiUrl + "git/trees/%s?recursive=true";
        this.updateBuilder = versionQuery.updateBuilder;
        this.version = versionQuery.isDefault ? getDefaultVersion() : versionQuery.version;
        this.build = getBuild();
    }

    public abstract String getBranch();

    public abstract Pattern getFilePattern();

    public abstract String getDefaultVersion();

    private String getBuild() {
        String url = String.format(refLatestCommitUrl, getBranch());
        debug("Getting latest build from " + url);
        try {
            URLConnection connection = WebUtils.openConnection(url, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            String sha = jsonObject.getString("sha");
            debug("Found latest build: " + sha);
            return sha;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getFile() {
        String url = String.format(filesUrl, build);
        debug("Getting files from " + url);
        try {
            URLConnection connection = WebUtils.openConnection(url, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONObject object = new JSONObject(new JSONTokener(inputStream));
            JSONArray array = object.getJSONArray("tree");
            Pattern pattern = getFilePattern();
            for (int i = 0; i < array.length(); i++) {
                JSONObject file = array.getJSONObject(i);
                String path = file.getString("path");
                String type = file.getString("type");
                if (type.equalsIgnoreCase("blob") && pattern.matcher(path).matches()) {
                    debug("Found file: " + path);
                    return path;
                }
            }
        } catch (IOException e) {
            debug(e);
        }
        return null;
    }

    @Override
    public String getChecksum() {
        return getBuild();
    }

    @Override
    public void setChecksum(File file) throws Exception {
        updateBuilder.checksumConsumer().accept(getChecksum());
    }

    @Override
    public String getCurrentChecksum(File file) throws Exception {
        return updateBuilder.checksumSupplier().get();
    }

    @Override
    public Checksum getChecksumChecker() {
        return this;
    }

    @Override
    public InputStream getInputStream() {
        String build = getBuild();
        if (build == null) {
            return null;
        }
        String file = getFile();
        if (file == null) {
            return null;
        }
        String url = String.format(downloadUrl, build, file);
        return WebUtils.getInputStreamOrNull(url, updateBuilder);
    }

    @Override
    public DebugConsumer getDebugConsumer() {
        return updateBuilder.debugConsumer();
    }
}
