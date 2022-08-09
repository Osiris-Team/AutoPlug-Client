/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

public class UtilsFile {

    /**
     * Removes unsupported chars like control chars
     * and specific printable chars like \ or / from the provided string and returns it. <br>
     * Works for all operating systems.
     */
    public String getValidFileName(String fileName) {
        return fileName.replaceAll("\\p{Cc}", "") // First remove control/not-printable chars
                .replaceAll("[/\\\\<>:\"'|*?]", ""); // Then remove invalid printable chars
    }
}
