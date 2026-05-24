/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.tasks.updater.search.SearchResult;

interface ModDownloadTask {
    void start();

    boolean isAlive();

    String getPlName();

    SearchResult getSearchResult();

    boolean isDownloadSuccessful();

    boolean isInstallSuccessful();
}
