package io.github.projectunified.mcserverupdater.api;

import io.github.projectunified.mcserverupdater.UpdateBuilder;
import io.github.projectunified.mcserverupdater.api.checksum.FileDigestChecksum;
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
import java.security.MessageDigest;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public abstract class GithubReleaseUpdater implements InputStreamUpdater {
    protected final UpdateBuilder updateBuilder;
    protected final String version;
    protected final String build;
    protected final String releasesUrl;
    protected final String repo;
    private final String releaseAssetUrl;
    private final JSONObject fileObject;

    protected GithubReleaseUpdater(VersionQuery versionQuery, String repo) {
        this.repo = repo;
        String url = "https://api.github.com/repos/" + repo + "/";
        this.releasesUrl = url + "releases";
        this.releaseAssetUrl = url + "releases/%s/assets";
        this.updateBuilder = versionQuery.updateBuilder;
        this.version = versionQuery.isDefault ? getDefaultVersion() : versionQuery.version;
        this.build = getBuild();
        this.fileObject = getFileObject();
    }

    public abstract Pattern getArtifactPattern();

    public abstract String getDefaultVersion();

    public abstract JSONObject getReleaseObject();

    protected JSONObject getLatestRelease() {
        String url = releasesUrl + "?per_page=1";
        debug("Getting release from " + url);
        try {
            URLConnection connection = WebUtils.openConnection(url, updateBuilder);
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
            URLConnection connection = WebUtils.openConnection(url, updateBuilder);
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
                URLConnection connection = WebUtils.openConnection(pageUrl, updateBuilder);
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

    private JSONObject getFileObject() {
        String assetUrl = String.format(releaseAssetUrl, build);
        debug("Getting asset URL from " + assetUrl);
        try {
            URLConnection connection = WebUtils.openConnection(assetUrl, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONArray array = new JSONArray(new JSONTokener(inputStream));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String name = object.getString("name");
                if (getArtifactPattern().matcher(name).matches()) {
                    debug("Found asset object: " + name);
                    return object;
                }
            }
            return null;
        } catch (IOException e) {
            debug(e);
            return null;
        }
    }

    private String getFileUrl() {
        return this.fileObject == null ? null : this.fileObject.getString("browser_download_url");
    }

    private String[] getFileDigest() {
        if (fileObject == null) {
            return null;
        }
        if (fileObject.isNull("digest")) {
            return null;
        }
        String digest = fileObject.getString("digest");
        return digest.split(Pattern.quote(":"), 2);
    }

    @Override
    public Checksum getChecksumChecker() {
        String[] fileDigest = getFileDigest();
        if (fileDigest == null) {
            return new SimpleChecksum() {
                @Override
                public String getChecksum() {
                    return repo + "||" + build;
                }

                @Override
                public String getCurrentChecksum(File file) throws Exception {
                    return updateBuilder.checksumSupplier().get();
                }

                @Override
                public void setChecksum(File file) throws Exception {
                    updateBuilder.checksumConsumer().accept(getChecksum());
                }

                @Override
                public DebugConsumer getDebugConsumer() {
                    return updateBuilder.debugConsumer();
                }
            };
        } else {
            return new FileDigestChecksum() {
                @Override
                public MessageDigest getMessageDigest() throws Exception {
                    String digestType = fileDigest[0];
                    if (digestType.equals("sha256")) {
                        return MessageDigest.getInstance("SHA-256");
                    } else if (digestType.equals("md5")) {
                        return MessageDigest.getInstance("MD5");
                    }
                    return null;
                }

                @Override
                public String getChecksum() {
                    return fileDigest[1];
                }

                @Override
                public DebugConsumer getDebugConsumer() {
                    return updateBuilder.debugConsumer();
                }
            };
        }
    }

    @Override
    public InputStream getInputStream() {
        return WebUtils.getInputStreamOrNull(getFileUrl(), updateBuilder);
    }

    @Override
    public DebugConsumer getDebugConsumer() {
        return updateBuilder.debugConsumer();
    }
}
