/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater;

import com.osiris.autoplug.client.utils.UtilsCrypto;
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
import java.util.Random;

public class TaskDownloadBase extends BThread {
    private String url;
    private File destinationFile;
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
    public TaskDownloadBase(String name, BThreadManager manager, String url, File dest) {
        this(name, manager, url, dest, false, (String[]) null);
    }

    public TaskDownloadBase(String name, BThreadManager manager, String url, File dest, boolean ignoreContentType, String... allowedSubContentTypes) {
        this(name, manager);
        this.url = url;
        this.destinationFile = dest;
        this.ignoreContentType = ignoreContentType;
        this.allowedSubContentTypes = allowedSubContentTypes;
    }

    private TaskDownloadBase(String name, BThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();

        final String fileName = destinationFile.getName();
        setStatus("Downloading " + fileName + "... (0mb/0mb)");
        AL.debug(this.getClass(), "Downloading " + fileName + " from: " + url);

        Request request = new Request.Builder().url(url)
                .header("User-Agent", "AutoPlug Client/" + new Random().nextInt() + " - https://autoplug.one")
                .build();

        Response response = new OkHttpClient.Builder().followRedirects(true).build().newCall(request).execute();
        ResponseBody body = null;
        try {
            if (response.code() != 200)
                throw new Exception("Download of '" + destinationFile.getName() + "' failed! Code: " + response.code() + " Message: " + response.message() + " Url: " + url);

            body = response.body();
            if (body == null)
                throw new Exception("Download of '" + destinationFile.getName() + "' failed because of null response body!");
            else if (!ignoreContentType && body.contentType() == null)
                throw new Exception("Download of '" + destinationFile.getName() + "' failed due to null content type!");
            else if (!ignoreContentType && !body.contentType().type().equals("application"))
                throw new Exception("Download of '" + destinationFile.getName() + "' failed because of invalid content type: " + body.contentType().type());
            else if (!ignoreContentType && !body.contentType().subtype().equals("java-archive")
                    && !body.contentType().subtype().equals("jar")
                    && !body.contentType().subtype().equals("octet-stream")) {
                if (allowedSubContentTypes == null)
                    throw new Exception("Download of '" + destinationFile.getName() + "' failed because of invalid sub-content type: " + body.contentType().subtype());
                if (!Arrays.asList(allowedSubContentTypes).contains(body.contentType().subtype()))
                    throw new Exception("Download of '" + destinationFile.getName() + "' failed because of invalid sub-content type: " + body.contentType().subtype());
            }

            long completeFileSize = body.contentLength();
            setMax(completeFileSize);

            BufferedInputStream in = new BufferedInputStream(body.byteStream());
            FileOutputStream fos = new FileOutputStream(destinationFile);
            BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
            byte[] data = new byte[1024];
            long downloadedFileSize = 0;
            int byteRead = 0;
            while ((byteRead = in.read(data, 0, 1024)) >= 0) {
                downloadedFileSize += byteRead;

                setStatus("Downloading " + fileName + "... (" + downloadedFileSize / (1024 * 1024) + "mb/" + completeFileSize / (1024 * 1024) + "mb)");
                setNow(downloadedFileSize);

                bout.write(data, 0, byteRead);
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

    /**
     * Only use this method after finishing the download.
     * It will get the hash for the newly downloaded file and
     * compare it with the given hash.
     *
     * @return true if the hashes match
     */
    public boolean compareWithMD5(String expectedHash) {
        expectedHash = expectedHash.trim();
        final String myHash = UtilsCrypto.fastMD5(destinationFile).trim();
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
        final String myHash = UtilsCrypto.fastSHA256(destinationFile).trim().toLowerCase();
        boolean result = myHash.equals(expectedHash);
        AL.debug(this.getClass(), "Comparing hashes (SHA-256). Is equal? " +
                result + " Excepted: \"" + expectedHash + "\" Actual: \"" + myHash + "\"");
        return result;
    }
}
