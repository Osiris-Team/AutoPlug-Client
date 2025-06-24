package me.hsgamer.mcserverupdater.updater;

import me.hsgamer.hscore.web.UserAgent;
import me.hsgamer.hscore.web.WebUtils;
import me.hsgamer.mcserverupdater.api.JenkinsUpdater;
import me.hsgamer.mcserverupdater.util.VersionQuery;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PufferfishUpdater extends JenkinsUpdater {
    private static final Pattern NORMAL_JOB_REGEX = Pattern.compile("Pufferfish-(\\d\\.\\d+)");
    private static final Pattern PURPUR_JOB_REGEX = Pattern.compile("Pufferfish-Purpur-(\\d\\.\\d+)");
    private static final Pattern PLUS_JOB_REGEX = Pattern.compile("PufferfishPlus-(\\d\\.\\d+)");
    private static final Pattern PLUS_PURPUR_JOB_REGEX = Pattern.compile("PufferfishPlus-(\\d\\.\\d+)-Purpur");
    private static final Pattern VERSION_REGEX = Pattern.compile("(\\d\\.\\d+(\\.\\d+)?)((-purpur|-plus)+)?");

    private List<Version> versions;

    public PufferfishUpdater(VersionQuery versionQuery) {
        super(versionQuery, "https://ci.pufferfish.host/");
    }

    private void loadVersions() {
        String jobsUrl = jenkinsUrl + "api/json?tree=jobs[name]";
        debug("Getting jobs from " + jobsUrl);
        try {
            URLConnection connection = UserAgent.CHROME.assignToConnection(WebUtils.createConnection(jobsUrl));
            InputStream inputStream = connection.getInputStream();
            JSONObject jsonObject = new JSONObject(new JSONTokener(inputStream));
            JSONArray jobsArray = jsonObject.getJSONArray("jobs");
            for (int i = 0; i < jobsArray.length(); i++) {
                JSONObject job = jobsArray.getJSONObject(i);
                String jobName = job.getString("name");

                Matcher normalMatcher = NORMAL_JOB_REGEX.matcher(jobName);
                if (normalMatcher.matches()) {
                    String majorVersion = normalMatcher.group(1);
                    versions.add(new Version(jobName, majorVersion, false, false));
                    continue;
                }

                Matcher purpurMatcher = PURPUR_JOB_REGEX.matcher(jobName);
                if (purpurMatcher.matches()) {
                    String majorVersion = purpurMatcher.group(1);
                    versions.add(new Version(jobName, majorVersion, false, true));
                    continue;
                }

                Matcher plusMatcher = PLUS_JOB_REGEX.matcher(jobName);
                if (plusMatcher.matches()) {
                    String majorVersion = plusMatcher.group(1);
                    versions.add(new Version(jobName, majorVersion, true, false));
                    continue;
                }

                Matcher plusPurpurMatcher = PLUS_PURPUR_JOB_REGEX.matcher(jobName);
                if (plusPurpurMatcher.matches()) {
                    String majorVersion = plusPurpurMatcher.group(1);
                    versions.add(new Version(jobName, majorVersion, true, true));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Version> getVersion(String version) {
        version = version.toLowerCase();
        Matcher matcher = VERSION_REGEX.matcher(version);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String matchVersion = matcher.group(1);
        String flag = matcher.group(3);
        boolean isPlus = flag != null && flag.contains("plus");
        boolean isPurpur = flag != null && flag.contains("purpur");

        return versions.stream()
                .filter(v -> matchVersion.startsWith(v.majorVersion))
                .filter(v -> v.isPlus == isPlus)
                .filter(v -> v.isPurpur == isPurpur)
                .findFirst();
    }

    @Override
    public String[] getJob() {
        if (versions == null) {
            versions = new ArrayList<>();
            loadVersions();
        }
        return getVersion(version)
                .map(v -> new String[]{v.job})
                .orElseGet(() -> new String[]{"INVALID"});
    }

    @Override
    public Pattern getArtifactRegex() {
        return Pattern.compile(".*\\.jar");
    }

    @Override
    public String getDefaultVersion() {
        return "1.20.4";
    }

    private static final class Version {
        public final String job;
        public final String majorVersion;
        public final boolean isPlus;
        public final boolean isPurpur;

        private Version(String job, String majorVersion, boolean isPlus, boolean isPurpur) {
            this.job = job;
            this.majorVersion = majorVersion;
            this.isPlus = isPlus;
            this.isPurpur = isPurpur;
        }

        @Override
        public String toString() {
            return "Version{" +
                    "job='" + job + '\'' +
                    ", majorVersion='" + majorVersion + '\'' +
                    ", isPlus=" + isPlus +
                    ", isPurpur=" + isPurpur +
                    '}';
        }
    }
}
