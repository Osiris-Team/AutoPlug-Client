/*
 * Copyright (c) 2022-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class UtilsFile {

    public String getFileName(String s) {
        return new File(s).getName();
    }

    /**
     * Removes unsupported chars like control chars
     * and specific printable chars like \ or / from the provided string and returns it. <br>
     * Works for all operating systems.
     */
    public String getValidFileName(String fileName) {
        return fileName.replaceAll("\\p{Cc}", "") // First remove control/not-printable chars
                .replaceAll("[/\\\\<>:\"'|*?]", ""); // Then remove invalid printable chars
    }

    public void copyDirectoryContent(File sourceDir, File targetDir) throws IOException {
        targetDir.mkdirs();
        for (File sourceFile : sourceDir.listFiles()) {
            File targetFile = new File(targetDir + "/" + sourceFile.getName());
            targetFile.createNewFile();
            Files.copy(sourceFile.toPath(), targetFile.toPath());
        }
    }

    public File renameFile(File file, String newName) {
        File newFile = new File(file.getParentFile() + "/" + newName);
        if (newFile.exists()) newFile.delete(); // Replace existing
        file.renameTo(newFile);
        file.delete(); // Delete old
        return newFile;
    }
}
