package io.github.projectunified.mcserverupdater.util;

import io.github.projectunified.mcserverupdater.UpdateBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class WebUtils {
    private WebUtils() {
        // EMPTY
    }

    public static URLConnection openConnection(String url, UpdateBuilder updateBuilder) throws IOException {
        if (url == null) {
            throw new NullPointerException("url is null");
        }

        URLConnection connection = new URL(url).openConnection();
        String userAgent = updateBuilder.userAgent();
        if (userAgent != null && !userAgent.isEmpty()) {
            connection.setRequestProperty("User-Agent", userAgent);
        }
        return connection;
    }

    public static InputStream getInputStream(String url, UpdateBuilder updateBuilder) throws IOException {
        URLConnection connection = openConnection(url, updateBuilder);
        updateBuilder.debug("Getting input stream from " + url);
        return connection.getInputStream();
    }

    public static URLConnection openConnectionOrNull(String url, UpdateBuilder updateBuilder) {
        try {
            return openConnection(url, updateBuilder);
        } catch (Exception e) {
            updateBuilder.debugConsumer().consume("Failed to open connection to " + url, e);
            return null;
        }
    }

    public static InputStream getInputStreamOrNull(String url, UpdateBuilder updateBuilder) {
        try {
            return getInputStream(url, updateBuilder);
        } catch (Exception e) {
            updateBuilder.debugConsumer().consume("Failed to get input stream from " + url, e);
            return null;
        }
    }
}
