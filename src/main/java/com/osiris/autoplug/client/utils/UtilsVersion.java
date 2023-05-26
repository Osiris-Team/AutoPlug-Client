/*
 * Copyright (c) 2021-2023 Osiris-Team.
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
     * Compares the first version with the second
     * version and returns true if the second version is
     * bigger than the first version.
     *
     * @param firstVersion
     * @param secondVersion
     * @return
     */
    public boolean isSecondBigger(@Nullable String firstVersion, @Nullable String secondVersion) {
        try {
            Objects.requireNonNull(firstVersion);
            Objects.requireNonNull(secondVersion);

            // First duplicate the strings so the original ones don't get altered
            String firstVersionDUPLICATE = firstVersion;
            String secondVersionDUPLICATE = secondVersion;

            // Remove left and right spaces
            firstVersionDUPLICATE = firstVersionDUPLICATE.trim();
            secondVersionDUPLICATE = secondVersionDUPLICATE.trim();

            if (!firstVersionDUPLICATE.contains(".") && !secondVersionDUPLICATE.contains(".")) {
                return Integer.parseInt(secondVersionDUPLICATE) > Integer.parseInt(firstVersionDUPLICATE);
            }

            // Remove everything except numbers and dots
            firstVersionDUPLICATE = firstVersionDUPLICATE.replaceAll("[^0-9.]", "");
            secondVersionDUPLICATE = secondVersionDUPLICATE.replaceAll("[^0-9.]", "");

            if (firstVersionDUPLICATE.isEmpty()) throw new Exception("Empty currentVersion string!");
            if (secondVersionDUPLICATE.isEmpty()) throw new Exception("Empty latestVersion string!");

            // If there are dots in the string we split it up
            String[] arrCurrent = firstVersionDUPLICATE.split("\\.");
            String[] arrLatest = secondVersionDUPLICATE.split("\\.");

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
