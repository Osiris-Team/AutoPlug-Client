package me.hsgamer.mcserverupdater.api;

import me.hsgamer.hscore.logger.common.Logger;
import me.hsgamer.hscore.web.UserAgent;
import me.hsgamer.hscore.web.WebUtils;
import me.hsgamer.mcserverupdater.UpdateBuilder;
import me.hsgamer.mcserverupdater.util.VersionQuery;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public abstract class GithubReleaseUpdater implements SimpleChecksum, UrlInputStreamUpdater {
    protected final UpdateBuilder updateBuilder;
    protected final String version;
    protected final String build;
    protected final String releasesUrl;
    protected final String repo;
    private final String releaseAssetUrl;

    protected GithubReleaseUpdater(VersionQuery versionQuery, String repo) {
        this.repo = repo;
        String url = "https://api.github.com/repos/" + repo + "/";
        this.releasesUrl = url + "releases";
        this.releaseAssetUrl = url + "releases/%s/assets";
        this.updateBuilder = versionQuery.updateBuilder;
        this.version = versionQuery.isDefault ? getDefaultVersion() : versionQuery.version;
        this.build = getBuild();
    }

    public abstract Pattern getArtifactPattern();

    public abstract String getDefaultVersion();

    public abstract JSONObject getReleaseObject();

    protected JSONObject getLatestRelease() {
        String url = releasesUrl + "?per_page=1";
        debug("Getting release from " + url);
        try {
            URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(url));
            InputStream inputStream = connection.getInputStream();
            JSONArray array = new JSONArray(new JSONTokener(inputStream));
            return array.getJSONObject(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected JSONObject getReleaseByTag(String tag) {
        String url = String.format(releasesUrl + "/tags/%s", tag);
        debug("Getting release from " + url);
        try {
            URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(url));
            InputStream inputStream = connection.getInputStream();
            return new JSONObject(new JSONTokener(inputStream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected JSONObject getReleaseByPredicate(Predicate<JSONObject> predicate) {
        int page = 1;
        while (true) {
            String pageUrl = releasesUrl + "?per_page=100&page=" + page;
            debug("Getting release from " + pageUrl);
            try {
                URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(pageUrl));
                InputStream inputStream = connection.getInputStream();
                JSONArray array = new JSONArray(new JSONTokener(inputStream));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.getJSONObject(i);
                    if (predicate.test(object)) {
                        return object;
                    }
                }
                if (array.length() < 100) {
                    break;
                }
                page++;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Cannot find the release");
    }

    protected JSONObject getReleaseByTagMatch(Predicate<String> tagPredicate) {
        return getReleaseByPredicate(object -> tagPredicate.test(object.getString("tag_name")));
    }

    private String getBuild() {
        JSONObject object = getReleaseObject();
        if (object == null) {
            throw new RuntimeException("Cannot get release ID");
        }
        String id = Objects.toString(object.get("id"), null);
        debug("Found release ID: " + id);
        return id;
    }

    @Override
    public String getFileUrl() {
        String assetUrl = String.format(releaseAssetUrl, build);
        debug("Getting asset URL from " + assetUrl);
        try {
            URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(assetUrl));
            InputStream inputStream = connection.getInputStream();
            JSONArray array = new JSONArray(new JSONTokener(inputStream));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String name = object.getString("name");
                if (getArtifactPattern().matcher(name).matches()) {
                    String url = object.getString("browser_download_url");
                    debug("Found asset URL: " + url);
                    return url;
                }
            }
            return null;
        } catch (IOException e) {
            debug(e);
            return null;
        }
    }

    @Override
    public String getChecksum() {
        return repo + "||" + build;
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
    public Logger getLogger() {
        return updateBuilder.logger();
    }
}
