/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.mods;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.UT;
import com.osiris.autoplug.client.tasks.updater.search.SearchResult;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModrinthAPITest {
    ModrinthAPI api = new ModrinthAPI();

    @Test
    void aaa() throws MalformedURLException, UnsupportedEncodingException {
        System.out.println(URLEncoder.encode("https://api.modrinth.com/v2/project/fallingtree/version?loaders=[\"forge\"]&game_versions=[\"1.18.1\"]", StandardCharsets.UTF_8.name()));
    }

    @Test
    void searchUpdate() throws Exception {
        UT.initLogger();
        Server.isFabric = true;
        File path = new File(System.getProperty("user.dir") + "/test/FallingTree-1.18.2-3.3.0.jar");
        MinecraftMod mod = new MinecraftMod(path.getAbsolutePath(), "FallingTree", "3.3.0", "RakSrinaNa",
                "fallingtree", null, null);
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