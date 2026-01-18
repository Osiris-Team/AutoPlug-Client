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

import java.io.InputStream;
import java.net.URLConnection;
import java.security.MessageDigest;

public class SpongeUpdater implements InputStreamUpdater, FileDigestChecksum {
    private final UpdateBuilder updateBuilder;
    private final String version;
    private final String build;
    private final String artifactUrl;
    private final String versionUrl;
    private final String buildUrl;
    private final boolean isRecommended;

    public SpongeUpdater(VersionQuery versionQuery, Type type, boolean isRecommended) {
        this.updateBuilder = versionQuery.updateBuilder;
        this.isRecommended = isRecommended;
        String baseUrl = "https://dl-api-new.spongepowered.org/api/v2/groups/org.spongepowered/artifacts/";
        this.artifactUrl = baseUrl + type.name;
        versionUrl = artifactUrl + "/versions";
        buildUrl = versionUrl + "/%s";
        this.version = versionQuery.isDefault ? getDefaultVersion() : versionQuery.version;
        this.build = getBuild();
    }

    private String getDefaultVersion() {
        updateBuilder.debug("Get default version from " + artifactUrl);
        try {
            URLConnection connection = WebUtils.openConnection(artifactUrl, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONObject tagsObject = jsonObject.getJSONObject("tags");
            JSONArray versions = tagsObject.getJSONArray("minecraft");
            return versions.getString(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getBuild() {
        String url = getQueryReadyFetchUrl(versionUrl) + "&limit=1&tags=,minecraft:" + version;
        updateBuilder.debug("Get latest build from " + url);
        try {
            URLConnection connection = WebUtils.openConnection(url, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONObject artifacts = jsonObject.getJSONObject("artifacts");
            String[] builds = JSONObject.getNames(artifacts);
            if (builds == null || builds.length == 0) {
                return null;
            }
            return builds[0];
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getQueryReadyFetchUrl(String url) {
        return url + "?" + (isRecommended ? "recommended=true" : "");
    }

    private JSONObject getJarInfo(String build) throws Exception {
        String url = String.format(buildUrl, build);
        updateBuilder.debug("Fetching " + url);
        URLConnection connection = WebUtils.openConnection(url, updateBuilder);
        InputStream inputStream = connection.getInputStream();
        JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
        JSONArray assets = jsonObject.getJSONArray("assets");
        JSONObject jarInfo = null;
        boolean hasUniversal = false;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String extension = asset.getString("extension");
            String classifier = asset.getString("classifier");
            if (classifier == null || extension == null || !extension.equalsIgnoreCase("jar")) continue;
            if (classifier.equalsIgnoreCase("universal")) {
                hasUniversal = true;
                jarInfo = asset;
            } else if (classifier.trim().isEmpty() && !hasUniversal) {
                jarInfo = asset;
            }
        }
        return jarInfo;
    }

    @Override
    public MessageDigest getMessageDigest() throws Exception {
        return MessageDigest.getInstance("MD5");
    }

    @Override
    public InputStream getInputStream() {
        try {
            JSONObject jarInfo = getJarInfo(build);
            if (jarInfo == null) {
                return null;
            }
            String downloadUrl = jarInfo.getString("downloadUrl");
            return WebUtils.getInputStreamOrNull(downloadUrl, updateBuilder);
        } catch (Exception e) {
            debug("Failed to get input stream for Sponge download", e);
            return null;
        }
    }

    @Override
    public String getChecksum() {
        try {
            JSONObject jarInfo = getJarInfo(build);
            if (jarInfo == null) {
                return null;
            }
            return jarInfo.getString("md5");
        } catch (Exception e) {
            debug("Failed to get checksum for Sponge download", e);
            return null;
        }
    }

    @Override
    public Checksum getChecksumChecker() {
        return this;
    }

    @Override
    public DebugConsumer getDebugConsumer() {
        return updateBuilder.debugConsumer();
    }

    public enum Type {
        SPONGE_VANILLA("spongevanilla"),
        SPONGE_FORGE("spongeforge"),
        SPONGE_NEO("spongeneo");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
