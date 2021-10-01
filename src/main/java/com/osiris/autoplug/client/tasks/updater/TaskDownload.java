/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

public class TaskDownload extends BetterThread {
    private String url;
    private File dest;
    private boolean ignoreContentType;
    private String[] allowedSubContentTypes;

    /**
     * Downloads a file from an url to the cache first and then
     * to its final destination.
     *
     * @param name    This processes name.
     * @param manager the parent process manager.
     * @param url     the download-url.
     * @param dest    the downloads final destination.
     */
    public TaskDownload(String name, BetterThreadManager manager, String url, File dest) {
        this(name, manager, url, dest, false, (String[]) null);
    }

    public TaskDownload(String name, BetterThreadManager manager, String url, File dest, boolean ignoreContentType, String... allowedSubContentTypes) {
        this(name, manager);
        this.url = url;
        this.dest = dest;
        this.ignoreContentType = ignoreContentType;
        this.allowedSubContentTypes = allowedSubContentTypes;
    }

    private TaskDownload(String name, BetterThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();

        final String fileName = dest.getName();
        setStatus("Downloading " + fileName + "... (0mb/0mb)");
        AL.debug(this.getClass(), "Downloading " + fileName + " from: " + url);

        Request request = new Request.Builder().url(url)
                .header("User-Agent", "AutoPlug Client/" + new Random().nextInt() + " - https://autoplug.online")
                .build();

        Response response = new OkHttpClient.Builder().followRedirects(true).build().newCall(request).execute();
        ResponseBody body = null;
        try {
            if (response.code() != 200)
                throw new Exception("Download of '" + dest.getName() + "' failed! Code: " + response.code() + " Message: " + response.message() + " Url: " + url);

            body = response.body();
            if (body == null)
                throw new Exception("Download of '" + dest.getName() + "' failed because of null response body!");
            else if (!ignoreContentType && body.contentType() == null)
                throw new Exception("Download of '" + dest.getName() + "' failed due to null content type!");
            else if (!ignoreContentType && !body.contentType().type().equals("application"))
                throw new Exception("Download of '" + dest.getName() + "' failed because of invalid content type: " + body.contentType().type());
            else if (!ignoreContentType && !body.contentType().subtype().equals("java-archive")
                    && !body.contentType().subtype().equals("jar")
                    && !body.contentType().subtype().equals("octet-stream")) {
                if (allowedSubContentTypes == null)
                    throw new Exception("Download of '" + dest.getName() + "' failed because of invalid sub-content type: " + body.contentType().subtype());
                if (!Arrays.asList(allowedSubContentTypes).contains(body.contentType().subtype()))
                    throw new Exception("Download of '" + dest.getName() + "' failed because of invalid sub-content type: " + body.contentType().subtype());
            }

            long completeFileSize = body.contentLength();
            setMax(completeFileSize);

            BufferedInputStream in = new BufferedInputStream(body.byteStream());
            FileOutputStream fos = new FileOutputStream(dest);
            BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
            byte[] data = new byte[1024];
            long downloadedFileSize = 0;
            int x = 0;
            while ((x = in.read(data, 0, 1024)) >= 0) {
                downloadedFileSize += x;

                setStatus("Downloading " + fileName + "... (" + downloadedFileSize / (1024 * 1024) + "mb/" + completeFileSize / (1024 * 1024) + "mb)");
                setNow(downloadedFileSize);

                bout.write(data, 0, x);
            }

            setStatus("Downloaded " + fileName + " (" + downloadedFileSize / (1024 * 1024) + "mb/" + completeFileSize / (1024 * 1024) + "mb)");
            bout.close();
            in.close();
            body.close();
            response.close();
        } catch (Exception e) {
            if (body != null) body.close();
            response.close();
            throw e;
        }
    }

    public boolean compareWithMD5(String expectedMD5) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(Files.readAllBytes(dest.toPath()));
        byte[] digest = md.digest();
        return bytesToHex(digest).equalsIgnoreCase(expectedMD5);
    }

    /**
     * Only use this method after finishing the download.
     * It will get the hash for the newly downloaded file and
     * compare it with the given hash.
     *
     * @param sha256
     * @return true if the hashes match
     */
    public boolean compareWithSHA256(String sha256) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(
                    FileUtils.readFileToByteArray(dest));
            final String hashResult = bytesToHex(encodedhash);
            AL.debug(this.getClass(), "Comparing hashes (SHA-256):");
            AL.debug(this.getClass(), "Input-Hash: " + sha256);
            AL.debug(this.getClass(), "File-Hash: " + hashResult);
            return hashResult.equals(sha256);
        } catch (Exception e) {
            getWarnings().add(new BetterWarning(this, e));
            return false;
        }

    }

    @NotNull
    private String bytesToHex(@NotNull byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

}
