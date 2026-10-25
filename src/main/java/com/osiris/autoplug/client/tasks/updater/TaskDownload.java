/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater;

import com.osiris.autoplug.client.utils.UtilsCrypto;
import com.osiris.autoplug.client.launcher.DownloadProgress;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.jlib.logger.AL;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public class TaskDownload extends BThread {
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
    public TaskDownload(String name, BThreadManager manager, String url, File dest) {
        this(name, manager, url, dest, false, (String[]) null);
    }

    public TaskDownload(String name, BThreadManager manager, String url, File dest, boolean ignoreContentType, String... allowedSubContentTypes) {
        this(name, manager);
        this.url = url;
        this.dest = dest;
        this.ignoreContentType = ignoreContentType;
        this.allowedSubContentTypes = allowedSubContentTypes;
    }

    private TaskDownload(String name, BThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();

        final String fileName = dest.getName();
        final String source = DownloadProgress.sourceUrl(url);
        setStatus("Downloading " + fileName + " from " + source);

        Request request = new Request.Builder().url(url)
                .header("User-Agent", "AutoPlug Client/" + new Random().nextInt() + " - https://autoplug.one")
                .build();

        try (DownloadProgress.Transfer transfer = DownloadProgress.begin("Downloading " + fileName, url);
             Response response = new OkHttpClient.Builder().followRedirects(true).build().newCall(request).execute()) {
            transfer.redirect(response.request().url().toString());
            if (response.code() != 200)
                throw new Exception("Download of '" + dest.getName() + "' failed! Code: " + response.code() + " Message: " + response.message() + " Url: " + source);

            ResponseBody body = response.body();
            if (body == null)
                throw new Exception("Download of '" + dest.getName() + "' failed because of null response body!");
            validateContentType(dest.getName(), body.contentType(), ignoreContentType, allowedSubContentTypes);
            long completeFileSize = body.contentLength();
            setMax(Math.max(1, completeFileSize));

            long downloadedFileSize = 0;
            try (BufferedInputStream in = new BufferedInputStream(body.byteStream());
                 BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(dest), 65536)) {
                byte[] data = new byte[65536];
                int x;
                while ((x = in.read(data)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
                    downloadedFileSize += x;
                    String amount = downloadedFileSize / (1024 * 1024) + "mb"
                            + (completeFileSize >= 0 ? "/" + completeFileSize / (1024 * 1024) + "mb" : "");
                    setStatus("Downloading " + fileName + " (" + amount + ") from " + source);
                    if (completeFileSize > 0) setNow(downloadedFileSize);
                    bout.write(data, 0, x);
                    transfer.update(downloadedFileSize, completeFileSize);
                }
            }
            setStatus("Downloaded " + fileName + " from " + source);
            transfer.complete(downloadedFileSize, completeFileSize);
        }
    }

    /**
     * Only use this method after finishing the download.
     * It will get the hash for the newly downloaded file and
     * compare it with the given hash.
     *
     * @return true if the hashes match
     */
    public boolean compareWithMD5(String expectedHash) {
        expectedHash = expectedHash.trim();
        final String myHash = UtilsCrypto.fastMD5(dest).trim();
        boolean result = myHash.equals(expectedHash);
        AL.debug(this.getClass(), "Comparing hashes (MD5). Is equal? " +
                result + " Excepted: \"" + expectedHash + "\" Actual: \"" + myHash + "\"");
        return result;
    }

    /**
     * Only use this method after finishing the download.
     * It will get the hash for the newly downloaded file and
     * compare it with the given hash.
     *
     * @return true if the hashes match
     */
    public boolean compareWithSHA256(String expectedHash) {
        expectedHash = expectedHash.trim().toLowerCase();
        final String myHash = UtilsCrypto.fastSHA256(dest).trim().toLowerCase();
        boolean result = myHash.equals(expectedHash);
        AL.debug(this.getClass(), "Comparing hashes (SHA-256). Is equal? " +
                result + " Excepted: \"" + expectedHash + "\" Actual: \"" + myHash + "\"");
        return result;
    }

    static void validateContentType(String fileName, okhttp3.MediaType contentType, boolean ignoreContentType, String... allowedSubContentTypes) throws Exception {
        if (ignoreContentType) {
            return;
        }
        if (contentType == null) {
            throw new Exception("Download of '" + fileName + "' failed due to null content type!");
        }

        String type = contentType.type();
        String subtype = contentType.subtype();

        if ("application".equals(type)) {
            if ("java-archive".equals(subtype) || "jar".equals(subtype) || "octet-stream".equals(subtype)) {
                return;
            }
            if (allowedSubContentTypes == null || !Arrays.asList(allowedSubContentTypes).contains(subtype)) {
                throw new Exception("Download of '" + fileName + "' failed because of invalid sub-content type: " + subtype);
            }
            return;
        }

        if (fileName != null
                && fileName.toLowerCase(Locale.ROOT).endsWith(".jar")
                && "text".equals(type)
                && "plain".equals(subtype)) {
            return;
        }

        throw new Exception("Download of '" + fileName + "' failed because of invalid content type: " + type);
    }

}
