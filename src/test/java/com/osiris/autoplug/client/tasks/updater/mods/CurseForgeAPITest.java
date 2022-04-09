/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.UT;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CurseForgeAPITest {
    CurseForgeAPI api = new CurseForgeAPI();

    @Test
    void searchUpdate() throws Exception {
        UT.initLogger();
        File path = new File(System.getProperty("user.dir") + "/test/MouseTweaks-forge-mc1.18-2.21.jar");
        MinecraftMod mod = new MinecraftMod(path.getAbsolutePath(), "MouseTweaks", "2.21", "Ivan Molodetskikh (YaLTeR)",
                null, "mousetweaks", null);
        SearchResult result = api.searchUpdate(mod, "1.18.2");
        if (result.exception != null) {
            if (result.exception instanceof HttpErrorException)
                System.err.println(((HttpErrorException) result.exception).getHttpErrorMessage());
            throw result.exception;
        }
        assertEquals(1, result.getResultCode());
        assertNotNull(result.getDownloadUrl());
    }
}