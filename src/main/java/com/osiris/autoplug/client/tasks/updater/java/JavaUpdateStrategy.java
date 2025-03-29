package com.osiris.autoplug.client.tasks.updater.java;

import java.io.File;
import org.rauschig.jarchivelib.*;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BThread;
import com.osiris.jlib.logger.AL;
import org.apache.commons.io.FileUtils;

interface JavaUpdateStrategy {
    void performUpdate(BThread thread, String currentBuildId, String latestBuildId,
                       String versionString, String downloadURL, String checksum,
                       TaskJavaDownload download, File finalDest, AdoptV3API.ImageType imageType);
}

class NotifyUpdateStrategy implements JavaUpdateStrategy {
    @Override
    public void performUpdate(BThread thread, String currentBuildId, String latestBuildId,
                              String versionString, String downloadURL, String checksum,
                              TaskJavaDownload download, File finalDest, AdoptV3API.ImageType imageType) {
        thread.setStatus("Update found (" + currentBuildId + " -> " + latestBuildId + ")!");
    }
}

class ManualUpdateStrategy implements JavaUpdateStrategy {
    @Override
    public void performUpdate(BThread thread, String currentBuildId, String latestBuildId,
                              String versionString, String downloadURL, String checksum,
                              TaskJavaDownload download, File finalDest, AdoptV3API.ImageType imageType) {
        try {
            thread.setStatus("Update found (" + currentBuildId + " -> " + latestBuildId + "), started download!");
            download.start();
            while (!download.isFinished()) {
                Thread.sleep(500);
            }
            if (download.isSuccess()) {
                thread.setStatus("Java update downloaded. Checking hash...");
                if (download.compareWithSHA256(checksum)) {
                    thread.setStatus("Java update downloaded successfully.");
                    thread.setSuccess(true);
                } else {
                    thread.setStatus("Downloaded Java update is broken. Nothing changed!");
                    thread.setSuccess(false);
                }
            } else {
                thread.setStatus("Java update failed!");
                thread.setSuccess(false);
            }
        } catch (Exception e) {
            thread.setStatus("Update failed: " + e.getMessage());
            thread.setSuccess(false);
        }
    }
}

class AutomaticUpdateStrategy implements JavaUpdateStrategy {
    @Override
    public void performUpdate(BThread thread, String currentBuildId, String latestBuildId,
                              String versionString, String downloadURL, String checksum,
                              TaskJavaDownload download, File finalDest, AdoptV3API.ImageType imageType) {
        try {
            thread.setStatus("Update found (" + currentBuildId + " -> " + latestBuildId + "), started download!");
            download.start();
            while (!download.isFinished()) {
                Thread.sleep(500);
            }

            if (download.isSuccess()) {
                thread.setStatus("Java update downloaded. Checking hash...");
                if (download.compareWithSHA256(checksum)) {
                    thread.setStatus("Java update downloaded. Removing old installation...");
                    if (finalDest.exists()) {
                        File[] files = finalDest.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (file.isDirectory())
                                    FileUtils.deleteDirectory(file);
                                else
                                    file.delete();
                            }
                        }
                    }
                    finalDest.mkdirs();

                    Archiver archiver = download.isTar() ?
                            ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP) :
                            ArchiverFactory.createArchiver(ArchiveFormat.ZIP);
                    archiver.extract(download.getNewCacheDest(), finalDest);
                    thread.setStatus("Java update installed successfully!");
                    thread.setSuccess(true);
                } else {
                    thread.setStatus("Downloaded Java update is broken. Nothing changed!");
                    thread.setSuccess(false);
                }
            } else {
                thread.setStatus("Java update failed!");
                thread.setSuccess(false);
            }
        } catch (Exception e) {
            thread.setStatus("Update failed: " + e.getMessage());
            thread.setSuccess(false);
        }
    }
}
