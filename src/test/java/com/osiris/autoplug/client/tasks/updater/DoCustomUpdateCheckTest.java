/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater;

import com.osiris.autoplug.client.tasks.updater.plugins.MinecraftPlugin;
import com.osiris.autoplug.client.tasks.updater.search.CustomCheckURL;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TestCustomUpdateCheck {
    @Test
    void test() {
      MinecraftPlugin pl = new MinecraftPlugin("./plugins/", "Chunky", "0.0.0", "pop4959", 0, 0, null);
      pl.customCheckURL = "https://api.modrinth.com/v2/project/chunky/version";
      pl.customDownloadURL = "https://cdn.modrinth.com/data/fALzjamp/versions/dPliWter/Chunky-1.4.16.jar";
        SearchResult sr = new CustomCheckURL().doCustomCheck(pl.customCheckURL, pl.getVersion());
        assertSame(SearchResult.Type.UPDATE_AVAILABLE, sr.type);
    }
}
