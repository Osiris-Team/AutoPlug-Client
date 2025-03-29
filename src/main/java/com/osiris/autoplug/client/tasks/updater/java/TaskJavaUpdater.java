/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.jlib.logger.AL;
import org.apache.commons.io.FileUtils;
import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Searches for updates and installs them is AUTOMATIC profile is selected.
 */
public class TaskJavaUpdater extends BThread {
    private UpdaterConfig updaterConfig;

    public TaskJavaUpdater(String name, BThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        updaterConfig = new UpdaterConfig();
        if (!updaterConfig.java_updater.asBoolean()) {
            skip();
            return;
        }
        if (Server.isRunning()) throw new Exception("Cannot perform update while server is running!");

        if (!updaterConfig.java_updater.asBoolean()) {
            skip();
            return;
        }

        setStatus("Searching for updates...");

        // First set the details we need
        // Start by setting the operating systems architecture type
        AdoptV3API.OperatingSystemArchitectureType osArchitectureType = null;
        String actualOsArchitecture = System.getProperty("os.arch").toLowerCase();
        for (AdoptV3API.OperatingSystemArchitectureType type :
                AdoptV3API.OperatingSystemArchitectureType.values()) {
            if (actualOsArchitecture.equals(type.toString().toLowerCase())) // Not comparing the actual names because the enum has more stuff matching one name
                osArchitectureType = type;
        }
        if (osArchitectureType == null) {
            // Do another check.
            // On windows it can be harder to detect the right architecture that's why we do the stuff below:
            // Source: https://stackoverflow.com/questions/4748673/how-can-i-check-the-bitness-of-my-os-using-java-j2se-not-os-arch/5940770#5940770
            String arch = System.getenv("PROCESSOR_ARCHITECTURE");
            String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");
            boolean is64 = arch != null && arch.endsWith("64")
                    || wow64Arch != null && wow64Arch.endsWith("64"); // Otherwise its 32bit
            if (is64)
                osArchitectureType = AdoptV3API.OperatingSystemArchitectureType.X64;
            else
                osArchitectureType = AdoptV3API.OperatingSystemArchitectureType.X32;
            AL.debug(this.getClass(), "The current operating systems architecture '" + actualOsArchitecture +
                    "' was not found in the architectures list '" + Arrays.toString(AdoptV3API.OperatingSystemArchitectureType.values()) + "'." +
                    " Defaulting to '" + osArchitectureType + "'.");
        }
        AL.debug(this.getClass(), "Determined '" + osArchitectureType.name() + "' as operating systems architecture.");

        // Set the operating systems type
        AdoptV3API.OperatingSystemType osType;
        String actualOsType = System.getProperty("os.name").toLowerCase();
        if (actualOsType.contains("alpine"))
            osType = AdoptV3API.OperatingSystemType.ALPINE_LINUX;
        if (actualOsType.contains("win"))
            osType = AdoptV3API.OperatingSystemType.WINDOWS;
        else if (actualOsType.contains("mac"))
            osType = AdoptV3API.OperatingSystemType.MAC;
        else if (actualOsType.contains("aix"))
            osType = AdoptV3API.OperatingSystemType.AIX;
        else if (actualOsType.contains("nix")
                || actualOsType.contains("nux"))
            osType = AdoptV3API.OperatingSystemType.LINUX;
        else if (actualOsType.contains("sunos"))
            osType = AdoptV3API.OperatingSystemType.SOLARIS;
        else {
            osType = AdoptV3API.OperatingSystemType.LINUX;
            AL.debug(this.getClass(), "The current operating system '" + actualOsType + "' was not found in the supported operating systems list." +
                    " Defaulting to '" + AdoptV3API.OperatingSystemType.LINUX.name() + "'.");
        }
        AL.debug(this.getClass(), "Determined '" + osType.name() + "' as operating system.");

        boolean isLargeHeapSize = updaterConfig.java_updater_large_heap.asBoolean();
        String javaVersion = updaterConfig.java_updater_version.asString();
        int currentBuildId = 0;
        if (updaterConfig.java_updater_build_id.asString() != null)
            currentBuildId = updaterConfig.java_updater_build_id.asInt();
        AdoptV3API.ImageType imageType = AdoptV3API.ImageType.JDK;
        // Using JRE here instead breaks the endpoint below somehow and returns 404
        // when onlyLTS is disabled. That's why we must use JDK currently.
        // TODO Hopefully this is temporary and can be fixed soon.

        AtomicReference<JsonObject> refJsonLatestRelease = new AtomicReference<>(null);
        boolean isOnlyLTS = true;
        new AdoptV3API().getReleases(
                osArchitectureType,
                isLargeHeapSize,
                imageType,
                true,
                isOnlyLTS, // Changing this to false makes the api return even fewer versions, which is pretty weird.
                osType,
                50,
                AdoptV3API.VendorProjectType.JDK,
                AdoptV3API.ReleaseType.GENERAL_AVAILABILITY,
                jsonReleases -> { // The below gets executed now, in the current thread
                    for (JsonElement e :
                            jsonReleases.getAsJsonArray("versions")) {
                        JsonObject o = e.getAsJsonObject();
                        String version = o.get("major").getAsString();
                        if (version.equals(javaVersion)) {
                            refJsonLatestRelease.set(o);
                            return false; // Should continue? No, since we found it!
                        }
                    }
                    return true;
                }
        );
        JsonObject jsonLatestRelease = refJsonLatestRelease.get();
        if (jsonLatestRelease == null) {
            isOnlyLTS = false;
            // Do the above search again, but this time with onlyLTS=false
            // Note that this seems to exclude all LTS releases, thats why the above
            // is still necessary.
            new AdoptV3API().getReleases(
                    osArchitectureType,
                    isLargeHeapSize,
                    imageType,
                    true,
                    isOnlyLTS,
                    osType,
                    50,
                    AdoptV3API.VendorProjectType.JDK,
                    AdoptV3API.ReleaseType.GENERAL_AVAILABILITY,
                    jsonReleases -> { // The below gets executed now, in the current thread
                        for (JsonElement e :
                                jsonReleases.getAsJsonArray("versions")) {
                            JsonObject o = e.getAsJsonObject();
                            String version = o.get("major").getAsString();
                            if (version.equals(javaVersion)) {
                                refJsonLatestRelease.set(o);
                                return false; // Should continue? No, since we found it!
                            }
                        }
                        return true;
                    }
            );
        }

        jsonLatestRelease = refJsonLatestRelease.get();
        if (jsonLatestRelease == null)
            throw new Exception("Couldn't find a matching major version to '" + javaVersion + "'.");

        int latestBuildId = jsonLatestRelease.get("build").getAsInt();
        if (latestBuildId <= currentBuildId) {
            setStatus("Your Java installation is on the latest version!");
            return;
        }

        // Simply the version string like: 11.0.0+28 for example // Not a typo ^-^
        String versionString = jsonLatestRelease.get("semver").toString().replace("\"", ""); // Returns with apostrophes ""

        JsonArray jsonVersionDetails = new AdoptV3API().getVersionInformation(
                versionString,
                osArchitectureType,
                isLargeHeapSize,
                imageType,
                true,
                isOnlyLTS,
                osType,
                20,
                AdoptV3API.VendorProjectType.JDK,
                AdoptV3API.ReleaseType.GENERAL_AVAILABILITY);

        String checksum = jsonVersionDetails.get(0).getAsJsonObject().getAsJsonArray("binaries")
                .get(0).getAsJsonObject().get("package").getAsJsonObject().get("checksum").getAsString();

        // The release name that can be used to retrieve the download link
        String releaseName = jsonVersionDetails.get(0).getAsJsonObject().get("release_name").getAsString();
        String downloadURL = new AdoptV3API().getDownloadUrl(
                releaseName,
                osType,
                osArchitectureType,
                imageType,
                true,
                isLargeHeapSize,
                AdoptV3API.VendorProjectType.JDK
        );

        AL.debug(this.getClass(), "Update found " + currentBuildId + " -> " + latestBuildId);
        String profile = updaterConfig.java_updater_profile.asString();

//        if (profile.equals("NOTIFY")) {
//            setStatus("Update found (" + currentBuildId + " -> " + latestBuildId + ")!");
//        } else if (profile.equals("MANUAL")) {
//            setStatus("Update found (" + currentBuildId + " -> " + latestBuildId + "), started download!");
//
//            // Download the file
//            // Typically the file is a .tar.gz file for linux or .zip file for windows
//            // We enter a .file extension, cause that gets replaced with either .tar.gz or .zip by the download task
//            File cache_dest = new File(GD.WORKING_DIR + "/autoplug/downloads/" + imageType + "-" + versionString + ".file");
//            TaskJavaDownload download = new TaskJavaDownload("JavaDownloader", getManager(), downloadURL, cache_dest, osType);
//            download.start();
//
//            while (true) {
//                Thread.sleep(500); // Wait until download is finished
//                if (download.isFinished()) {
//                    if (download.isSuccess()) {
//                        setStatus("Java update downloaded. Checking hash...");
//                        if (download.compareWithSHA256(checksum)) {
//                            setStatus("Java update downloaded successfully.");
//                            setSuccess(true);
//                        } else {
//                            setStatus("Downloaded Java update is broken. Nothing changed!");
//                            setSuccess(false);
//                        }
//
//                    } else {
//                        setStatus("Java update failed!");
//                        setSuccess(false);
//                    }
//                    break;
//                }
//            }
//        } else {
//            setStatus("Update found (" + currentBuildId + " -> " + latestBuildId + "), started download!");
//
//            File final_dir_dest = new File(GD.WORKING_DIR + "/autoplug/system/jre");
//            File cache_dest = new File(GD.WORKING_DIR + "/autoplug/downloads/" + imageType + "-" + versionString + ".file");
//            TaskJavaDownload download = new TaskJavaDownload("JavaDownloader", getManager(), downloadURL, cache_dest, osType);
//            download.start();
//
//            while (true) {
//                Thread.sleep(500);
//                if (download.isFinished()) {
//                    if (download.isSuccess()) {
//                        setStatus("Java update downloaded. Checking hash...");
//                        if (download.compareWithSHA256(checksum)) {
//                            setStatus("Java update downloaded. Removing old installation...");
//                            if (final_dir_dest.exists()) {
//                                File[] files = final_dir_dest.listFiles();
//                                if (files != null)
//                                    for (File file :
//                                            files) {
//                                        if (file.isDirectory())
//                                            FileUtils.deleteDirectory(file);
//                                        else
//                                            file.delete();
//                                    }
//                            }
//                            final_dir_dest.mkdirs();
//
//                            Archiver archiver;
//                            if (download.isTar())
//                                archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP);
//                            else // A zip
//                                archiver = ArchiverFactory.createArchiver(ArchiveFormat.ZIP);
//
//                            archiver.extract(download.getNewCacheDest(), final_dir_dest);
//                            setStatus("Java update was installed successfully (" + currentBuildId + " -> " + latestBuildId + ")!");
//                            updaterConfig.java_updater_build_id.setValues(String.valueOf(latestBuildId));
//                            updaterConfig.save();
//                            finish(true);
//                        } else {
//                            setStatus("Downloaded Java update is broken. Nothing changed!");
//                            finish(false);
//                        }
//
//                    } else {
//                        setStatus("Java update failed!");
//                        finish(false);
//                    }
//                    break;
//                }
//            }
//        }

        // Use strategy pattern instead of if-else chain
        JavaUpdateStrategy strategy;
        switch (profile) {
            case "NOTIFY":
                strategy = new NotifyUpdateStrategy();
                break;
            case "MANUAL":
                strategy = new ManualUpdateStrategy();
                break;
            default:
                strategy = new AutomaticUpdateStrategy();
        }

        // File path required by Automatic strategy
        File finalDirDest = new File(GD.WORKING_DIR + "/autoplug/system/jre");
        File cacheDest = new File(GD.WORKING_DIR + "/autoplug/downloads/" + imageType + "-" + versionString + ".file");
        TaskJavaDownload download = new TaskJavaDownload("JavaDownloader", getManager(), downloadURL, cacheDest, osType);

        // Delegates behavior to strategy class
        strategy.performUpdate(this,
                String.valueOf(currentBuildId),
                String.valueOf(latestBuildId),
                versionString,
                downloadURL,
                checksum,
                download,
                finalDirDest,
                imageType
        );
    }
}
