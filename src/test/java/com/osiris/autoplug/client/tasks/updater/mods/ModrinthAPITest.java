/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModrinthAPITest {
    ModrinthAPI api = new ModrinthAPI();

    @Test
    void searchUpdate() {
        MinecraftMod mod = new MinecraftMod("/", "FallingTree", "3.1.0", "RakSrinaNa",
                "fallingtree", null, null);
        SearchResult result = api.searchUpdate(mod, "1.18.1");
        assertEquals(1, result.getResultCode());
        assertNotNull(result.getDownloadUrl());
    }
}