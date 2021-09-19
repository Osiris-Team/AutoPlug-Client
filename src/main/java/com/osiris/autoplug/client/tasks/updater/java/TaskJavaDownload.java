/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.java;

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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskJavaDownload extends BetterThread {
    private final String url;
    private final AdoptV3API.OperatingSystemType osType;
    private final File dest;
    private File newDest;
    private boolean isTar;

    /**
     * Downloads a file from an url to the cache first and then
     * to its final destination.
     *
     * @param name    This processes name.
     * @param manager the parent process manager.
     * @param url     the download-url.
     * @param dest    the downloads final destination. Note that the file name must end with '.file', because
     *                the actual file type gets set when there is download information available.
     */
    public TaskJavaDownload(String name, BetterThreadManager manager, String url, File dest, AdoptV3API.OperatingSystemType osType) {
        super(name, manager);
        this.url = url;
        this.dest = dest;
        this.osType = osType;
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();

        String fileName = dest.getName();
        setStatus("Downloading " + fileName + "... (0mb/0mb)");
        AL.debug(this.getClass(), "Downloading " + fileName + " from: " + url);

        Request request = new Request.Builder().url(url)
                .header("User-Agent", "AutoPlug Client/" + new Random().nextInt() + " - https://autoplug.online")
                .build();
        Response response = new OkHttpClient().newCall(request).execute();
        ResponseBody body = null;
        try {
            if (response.code() != 200)
                throw new Exception("Download of '" + fileName + "' failed! Code: " + response.code() + " Message: " + response.message() + " Url: " + url);

            body = response.body();
            if (body == null)
                throw new Exception("Download of '" + fileName + "' failed because of null response body!");
            else if (body.contentType() == null)
                throw new Exception("Download of '" + fileName + "' failed because of null content type!");
            else if (!body.contentType().type().equals("application"))
                throw new Exception("Download of '" + fileName + "' failed because of invalid content type: " + body.contentType().type());
            else if (!body.contentType().subtype().equals("java-archive")
                    && !body.contentType().subtype().equals("jar")
                    && !body.contentType().subtype().equals("octet-stream")
                    && !body.contentType().subtype().equals("x-gtar") // ADDITIONS FOR JAVA DOWNLOADS
                    && !body.contentType().subtype().equals("zip"))
                throw new Exception("Download of '" + fileName + "' failed because of invalid sub-content type: " + body.contentType().subtype());

            // Set the file name
            if (body.contentType().subtype().equals("x-gtar")) {
                isTar = true;
                fileName = fileName.replace(".file", ".tar.gz");
            } else {
                // In this case we check the response header for file information
                // Example: (content-disposition, attachment; filename=OpenJDK15U-jre_x86-32_windows_hotspot_15.0.2_7.zip)
                String contentDispo = response.headers().get("content-disposition");
                if (contentDispo == null)
                    throw new Exception("Failed to determine download file type!");

                if (contentDispo.contains(".tar.gz")) {
                    isTar = true;
                    fileName = fileName.replace(".file", ".tar.gz");
                } else {
                    Pattern p = Pattern.compile("[.][^.]+$"); // Returns the file extension with dot. example.txt -> .txt
                    Matcher m = p.matcher(contentDispo);
                    if (m.find()) {
                        String fileExtension = m.group();
                        fileName = fileName.replace(".file", fileExtension);
                    } else
                        throw new Exception("Failed to determine download file type! Download-Url: " + contentDispo);
                }
            }


            // We need to at least create the cache dest to then rename it
            if (dest.exists()) dest.delete();
            dest.getParentFile().mkdirs();
            dest.createNewFile();

            // The actual file with the correct file extension
            newDest = new File(dest.getParentFile().getAbsolutePath() + "/" + fileName);
            if (newDest.exists()) newDest.delete();
            newDest.getParentFile().mkdirs();
            newDest.createNewFile();

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

            Files.copy(dest.toPath(), newDest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            if (body != null) body.close();
            response.close();
            throw e;
        }
    }

    /**
     * Retrieve this once the task finished to get a correct result.
     */
    public boolean isTar() {
        return isTar;
    }

    public File getNewCacheDest() {
        return newDest;
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
