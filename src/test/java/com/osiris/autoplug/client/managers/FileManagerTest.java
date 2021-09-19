/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.managers;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileManagerTest {

    /**
     * Get files from the .github directory. Fail if no files returned.
     * Should return an empty file array because there are no files in depth 1 of /.github folder.
     * @throws Exception
     */
    @Test
    void getFilesFrom() throws Exception{
        List<File> files = new FileManager().getFilesFrom(System.getProperty("user.dir")+"/.github");
        assertTrue(files.isEmpty());
    }

    /**
     * Get folders from the .github directory. Fail if no folders returned.
     * @throws Exception
     */
    @Test
    void getFoldersFrom() throws Exception {
        List<File> folders = new FileManager().getFoldersFrom(System.getProperty("user.dir")+"/.github");
        assertFalse(folders.isEmpty());
    }
}