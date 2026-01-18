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
import java.util.function.Predicate;
import java.util.regex.Pattern;

public abstract class JenkinsUpdater implements SimpleChecksum, InputStreamUpdater {
    protected final UpdateBuilder updateBuilder;
    protected final String version;
    protected final String build;
    protected final String jenkinsUrl;

    protected JenkinsUpdater(VersionQuery versionQuery, String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl.endsWith("/") ? jenkinsUrl : jenkinsUrl + "/";
        this.updateBuilder = versionQuery.updateBuilder;
        this.version = versionQuery.isDefault ? getDefaultVersion() : versionQuery.version;
        this.build = getBuild();
        debug("Build: " + build);
    }

    public abstract String[] getJob();

    public abstract Pattern getArtifactRegex();

    public abstract String getDefaultVersion();

    protected String getBuild() {
        return getLatestSuccessfulBuild();
    }

    protected String getLatestSuccessfulBuild() {
        String url = getJobUrl();
        String api = url + "api/json";
        String treeUrl = api + "?tree=lastSuccessfulBuild[number]";
        debug("Getting build from " + treeUrl);
        try {
            URLConnection connection = WebUtils.openConnection(treeUrl, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONObject build = jsonObject.getJSONObject("lastSuccessfulBuild");
            return Integer.toString(build.getInt("number"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getBuildByPredicate(String treeQuery, Predicate<JSONObject> predicate) {
        String url = getJobUrl();
        String api = url + "api/json";
        String finalTreeQuery = treeQuery.equals("*") || treeQuery.contains("number")
                ? treeQuery
                : (treeQuery.isEmpty() ? "number" : "number," + treeQuery);
        String treeUrl = api + "?tree=builds[" + finalTreeQuery + "]";
        debug("Getting build from " + treeUrl);
        try {
            URLConnection connection = WebUtils.openConnection(treeUrl, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONArray builds = jsonObject.getJSONArray("builds");
            for (int i = 0; i < builds.length(); i++) {
                JSONObject build = builds.getJSONObject(i);
                if (predicate.test(build)) {
                    return Integer.toString(build.getInt("number"));
                }
            }
            throw new RuntimeException("Cannot find the build");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getSuccessfulBuildByNameMatch(Predicate<String> predicate) {
        return getBuildByPredicate("displayName,result", build -> {
            String name = build.getString("displayName");
            String result = build.getString("result");
            return result.equalsIgnoreCase("SUCCESS") && predicate.test(name);
        });
    }

    @Override
    public String getChecksum() {
        return String.join("||", version, build, String.join("_", getJob()), getJenkinsUrl());
    }

    @Override
    public void setChecksum(File file) throws Exception {
        updateBuilder.checksumConsumer().accept(getChecksum());
    }

    @Override
    public Checksum getChecksumChecker() {
        return this;
    }

    @Override
    public InputStream getInputStream() {
        String url = getArtifactUrl();
        debug("Downloading " + url);
        try {
            URLConnection connection = WebUtils.openConnection(url, updateBuilder);
            return connection.getInputStream();
        } catch (IOException e) {
            debug(e);
            return null;
        }
    }

    private String getJenkinsUrl() {
        return jenkinsUrl;
    }

    private String getJobUrl() {
        String[] job = getJob();
        StringBuilder builder = new StringBuilder();
        builder.append(jenkinsUrl);
        for (String s : job) {
            builder.append("job/").append(s).append("/");
        }
        return builder.toString();
    }

    private String getArtifactUrl() {
        Pattern artifactRegex = getArtifactRegex();
        String jobUrl = getJobUrl();
        String artifactListUrl = jobUrl + build + "/api/json?tree=artifacts[fileName,relativePath]";
        String artifact = "INVALID";
        debug("Getting artifact from " + artifactListUrl);
        try {
            URLConnection connection = WebUtils.openConnection(artifactListUrl, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONArray artifacts = jsonObject.getJSONArray("artifacts");
            for (int i = 0; i < artifacts.length(); i++) {
                JSONObject artifactObject = artifacts.getJSONObject(i);
                String fileName = artifactObject.getString("fileName");
                if (artifactRegex.matcher(fileName).matches()) {
                    artifact = artifactObject.getString("relativePath");
                    break;
                }
            }
        } catch (IOException e) {
            debug(e);
        }
        String artifactUrl = jobUrl + "%s/artifact/%s";
        String formattedArtifactUrl = String.format(artifactUrl, build, artifact);
        debug("Artifact URL: " + formattedArtifactUrl);
        return formattedArtifactUrl;
    }

    @Override
    public String getCurrentChecksum(File file) throws Exception {
        return updateBuilder.checksumSupplier().get();
    }

    @Override
    public DebugConsumer getDebugConsumer() {
        return updateBuilder.debugConsumer();
    }
}
