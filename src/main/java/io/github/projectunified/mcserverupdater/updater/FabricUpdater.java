package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.UpdateBuilder;
import io.github.projectunified.mcserverupdater.api.Checksum;
import io.github.projectunified.mcserverupdater.api.DebugConsumer;
import io.github.projectunified.mcserverupdater.api.InputStreamUpdater;
import io.github.projectunified.mcserverupdater.api.checksum.SimpleChecksum;
import io.github.projectunified.mcserverupdater.util.VersionQuery;
import io.github.projectunified.mcserverupdater.util.WebUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.InputStream;
import java.net.URLConnection;

public class FabricUpdater implements InputStreamUpdater, SimpleChecksum {
    private static final String BASE_URL = "https://meta.fabricmc.net/v2/versions";
    private static final String GAME_URL = BASE_URL + "/game";
    private static final String LOADER_URL = BASE_URL + "/loader";
    private static final String DOWNLOAD_URL = LOADER_URL + "/%s/%s/%s/server/jar";
    private static final String INSTALLER_URL = BASE_URL + "/installer";
    private final UpdateBuilder updateBuilder;
    private final String version;
    private final String build;
    private final boolean isStable;

    public FabricUpdater(VersionQuery versionQuery, boolean isStable) {
        this.isStable = isStable;
        this.updateBuilder = versionQuery.updateBuilder;
        this.version = versionQuery.isDefault ? getLatestGameVersion() : versionQuery.version;
        this.build = getBuild();
    }

    private String getBuild() {
        String loaderVersion = getLatestLoaderVersion();
        updateBuilder.debug("Latest loader version: " + loaderVersion);
        if (loaderVersion == null) {
            throw new IllegalStateException("Cannot get the latest loader version");
        }
        String installerVersion = getLatestInstallerVersion();
        updateBuilder.debug("Latest installer version: " + installerVersion);
        if (installerVersion == null) {
            throw new IllegalStateException("Cannot get the latest installer version");
        }
        return loaderVersion + ";" + installerVersion;
    }

    private String getLatestVersion(String url) {
        updateBuilder.debug("Getting latest version from " + url);
        try {
            URLConnection connection = WebUtils.openConnection(url, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONArray jsonArray = new JSONArray(new JSONTokener(inputStream));
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if (isStable && !jsonObject.getBoolean("stable")) {
                    continue;
                }
                return jsonObject.getString("version");
            }
            return null;
        } catch (Exception e) {
            debug("Failed to get latest version from " + url, e);
            return null;
        }
    }

    private String getLatestGameVersion() {
        return getLatestVersion(GAME_URL);
    }

    private String getLatestLoaderVersion() {
        return getLatestVersion(LOADER_URL);
    }

    private String getLatestInstallerVersion() {
        return getLatestVersion(INSTALLER_URL);
    }

    private String getLatestDownloadUrl(String serverVersion, String loaderVersion, String installerVersion) {
        return String.format(DOWNLOAD_URL, serverVersion, loaderVersion, installerVersion);
    }

    @Override
    public InputStream getInputStream() {
        String[] split = build.split(";");
        if (split.length != 2) {
            return null;
        }
        String loaderVersion = split[0];
        String installerVersion = split[1];
        String url = getLatestDownloadUrl(version, loaderVersion, installerVersion);
        return WebUtils.getInputStreamOrNull(url, updateBuilder);
    }

    @Override
    public String getChecksum() {
        return "fabric-" + version + "-" + build;
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
    public DebugConsumer getDebugConsumer() {
        return updateBuilder.debugConsumer();
    }
}
