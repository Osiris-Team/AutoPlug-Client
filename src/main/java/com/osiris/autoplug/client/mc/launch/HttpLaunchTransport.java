package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** HTTPS transport with bounded JSON responses, timeouts, streaming files and cancellation. */
public final class HttpLaunchTransport implements LaunchTransport {
    @Override
    public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body,
                           Cancellation cancellation) throws IOException {
        HttpURLConnection connection = connect(uri, cancellation);
        try (AutoCloseable ignored = cancellation.onCancel(connection::disconnect)) {
            connection.setRequestMethod(method);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int status = connection.getResponseCode();
            InputStream response = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (response == null) throw new IOException("HTTP request failed (" + status + ")");
            try (InputStream input = response; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    cancellation.check();
                    if (output.size() + length > 8 * 1024 * 1024) throw new IOException("JSON response too large");
                    output.write(buffer, 0, length);
                }
                JsonObject result = JsonParser.parseString(new String(output.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (status < 200 || status >= 300) {
                    // OAuth polling errors must be interpreted by the caller; never log response bodies/tokens.
                    if (result.has("error")) return result;
                    throw new IOException("HTTP request failed (" + status + ")");
                }
                return result;
            }
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Unable to read JSON response", error);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public void download(URI uri, Path destination, Cancellation cancellation) throws IOException {
        HttpURLConnection connection = connect(uri, cancellation);
        try (AutoCloseable ignored = cancellation.onCancel(connection::disconnect)) {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Artifact download failed (" + status + ")");
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    cancellation.check();
                    output.write(buffer, 0, length);
                }
            }
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Artifact download interrupted", error);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection connect(URI uri, Cancellation cancellation) throws IOException {
        cancellation.check();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IOException("Minecraft downloads and authentication require HTTPS");
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "AutoPlug-Native-Launcher/10.2.0");
        return connection;
    }
}
