/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class UtilsVersion {

    /**
     * Compares the current version with the latest
     * version and returns true if the latest version is
     * bigger than the current version.
     *
     * @param currentVersion
     * @param latestVersion
     * @return
     */
    public boolean compare(@Nullable String currentVersion, @Nullable String latestVersion) {
        try {
            Objects.requireNonNull(currentVersion);
            Objects.requireNonNull(latestVersion);

            // First duplicate the strings so the original ones don't get altered
            String currentVersionDUPLICATE = "" + currentVersion;
            String latestVersionDUPLICATE = "" + latestVersion;

            // Remove left and right spaces
            currentVersionDUPLICATE = currentVersionDUPLICATE.trim();
            latestVersionDUPLICATE = latestVersionDUPLICATE.trim();

            if (!currentVersionDUPLICATE.contains(".") && !latestVersionDUPLICATE.contains(".")) {
                return Integer.parseInt(latestVersionDUPLICATE) > Integer.parseInt(currentVersionDUPLICATE);
            }

            // Remove everything except numbers and dots
            currentVersionDUPLICATE = currentVersionDUPLICATE.replaceAll("[^0-9.]", "");
            latestVersionDUPLICATE = latestVersionDUPLICATE.replaceAll("[^0-9.]", "");

            if (currentVersionDUPLICATE.isEmpty()) throw new Exception("Empty currentVersion string!");
            if (latestVersionDUPLICATE.isEmpty()) throw new Exception("Empty latestVersion string!");

            // If there are dots in the string we split it up
            String[] arrCurrent = currentVersionDUPLICATE.split("\\.");
            String[] arrLatest = latestVersionDUPLICATE.split("\\.");

            // Latest version is shorter thus current version is newer.
            if (arrLatest.length == arrCurrent.length) {
                int latest, current;
                for (int i = 0; i < arrLatest.length; i++) {
                    latest = Integer.parseInt(arrLatest[i]);
                    current = Integer.parseInt(arrCurrent[i]);
                    if (latest == current) continue;
                    else return latest > current;
                }
                return false; // All are the same
            } else return arrLatest.length > arrCurrent.length;
        } catch (Exception e) {
            AL.warn(e);
            return false;
        }
    }

}
