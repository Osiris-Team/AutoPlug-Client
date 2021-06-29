/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * See {@link #TaskPluginDownload(String, BetterThreadManager, String, String, String, String, File, File)} for details.
 */
public class TaskPluginDownload extends BetterThread {
    private final String plName;
    private final String plLatestVersion;
    private final String url;
    private final String profile;
    private final File finalDest;
    private final File deleteDest;
    private File downloadDest;
    private boolean isDownloadSuccessful;
    private boolean isInstallSuccessful;

    public TaskPluginDownload(String name, BetterThreadManager manager,
                              String plName, String plLatestVersion,
                              String url, String profile, File finalDest) {
        this(name, manager, plName, plLatestVersion, url, profile, finalDest, null);
    }

    /**
     * Performs a plugin installation/download according to the users profile.
     *
     * @param name            this processes name.
     * @param manager         the parent process manager.
     * @param plName          plugin name.
     * @param plLatestVersion plugins latest version.
     * @param url             the download-url.
     * @param profile         the users plugin updater profile. NOTIFY, MANUAL or AUTOMATIC.
     * @param finalDest       the final download destination.
     * @param deleteDest      the file that should be deleted on a successful download. If null nothing gets deleted.
     */
    public TaskPluginDownload(String name, BetterThreadManager manager,
                              String plName, String plLatestVersion,
                              String url, String profile, File finalDest, File deleteDest) {
        super(name, manager);
        this.plName = plName;
        this.plLatestVersion = plLatestVersion;
        this.url = url;
        this.profile = profile;
        this.finalDest = finalDest;
        this.deleteDest = deleteDest;
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();

        this.setAutoFinish(false); // Disable so we can display the finish message

        if (profile.equals("NOTIFY")) {
            setStatus("Your profile doesn't allow downloads! Profile: " + profile);
            finish(false);
            return;
        } else if (profile.equals("MANUAL")) {
            download();
            isDownloadSuccessful = true;
        } else {
            download();
            isDownloadSuccessful = true;
            if (finalDest.exists()) finalDest.delete();
            finalDest.createNewFile();
            if (deleteDest != null && deleteDest.exists()) deleteDest.delete();
            FileUtils.copyFile(downloadDest, finalDest);
            isInstallSuccessful = true;
            setStatus("Installed update for " + plName + " successfully!");
        }


        finish(true);
    }

    public void download() throws Exception {
        GD.WORKING_DIR = new File(System.getProperty("user.dir"));
        File dir = new File(GD.WORKING_DIR + "/autoplug-downloads");
        if (!dir.exists()) dir.mkdirs();

        downloadDest = new File(GD.WORKING_DIR + "/autoplug-downloads/" + plName + "-[" + plLatestVersion + "].jar");
        if (downloadDest.exists()) downloadDest.delete();
        downloadDest.createNewFile();

        final String fileName = downloadDest.getName();
        setStatus("Downloading " + fileName + "... (0kb/0kb)");
        AL.debug(this.getClass(), "Downloading " + fileName + " from: " + url);

        Request request = new Request.Builder().url(url)
                .header("User-Agent", "AutoPlug Client/" + new Random().nextInt() + " - https://autoplug.online")
                .build();
        Response response = new OkHttpClient().newCall(request).execute();
        ResponseBody body = null;
        try {
            if (response.code() != 200)
                throw new Exception("Download error for " + plName + " code: " + response.code() + " message: " + response.message() + " url: " + url);

            body = response.body();
            if (body == null)
                throw new Exception("Download failed because of null response body!");
            else if (body.contentType() == null)
                throw new Exception("Download failed because of null content type!");
            else if (!body.contentType().type().equals("application"))
                throw new Exception("Download failed because of invalid content type: " + body.contentType().type());
            else if (!body.contentType().subtype().equals("java-archive")
                    && !body.contentType().subtype().equals("jar")
                    && !body.contentType().subtype().equals("octet-stream"))
                throw new Exception("Download failed because of invalid sub-content type: " + body.contentType().subtype());

            long completeFileSize = body.contentLength();
            setMax(completeFileSize);

            BufferedInputStream in = new BufferedInputStream(body.byteStream());
            FileOutputStream fos = new FileOutputStream(downloadDest);
            BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
            byte[] data = new byte[1024];
            long downloadedFileSize = 0;
            int x = 0;
            while ((x = in.read(data, 0, 1024)) >= 0) {
                downloadedFileSize += x;

                setStatus("Downloading " + fileName + "... (" + downloadedFileSize / 1024 + "kb/" + completeFileSize / 1024 + "kb)");
                setNow(downloadedFileSize);

                bout.write(data, 0, x);
            }

            setStatus("Downloaded " + fileName + " (" + downloadedFileSize / 1024 + "kb/" + completeFileSize / 1024 + "kb)");
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

    public String getPlName() {
        return plName;
    }

    public String getPlLatestVersion() {
        return plLatestVersion;
    }

    public String getUrl() {
        return url;
    }

    public String getProfile() {
        return profile;
    }

    public File getFinalDest() {
        return finalDest;
    }

    public File getDeleteDest() {
        return deleteDest;
    }

    public File getDownloadDest() {
        return downloadDest;
    }

    public boolean isDownloadSuccessful() {
        return isDownloadSuccessful;
    }

    public boolean isInstallSuccessful() {
        return isInstallSuccessful;
    }
}
