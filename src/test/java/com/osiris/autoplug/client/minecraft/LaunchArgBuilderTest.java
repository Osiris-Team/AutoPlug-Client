/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 tests.
 */

package com.osiris.autoplug.client.minecraft;

import com.osiris.autoplug.client.minecraft.launcher.LaunchArgBuilder;
import com.osiris.autoplug.client.minecraft.launcher.MinecraftLauncher;
import com.osiris.autoplug.client.minecraft.launcher.MojangManifest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LaunchArgBuilderTest {

    private static final String JSON = "{"
            + "\"id\": \"1.21.1\","
            + "\"mainClass\": \"net.minecraft.client.main.Main\","
            + "\"assetIndex\": {\"id\": \"17\", \"url\": \"https://example.com/17.json\"},"
            + "\"downloads\": {\"client\": {\"url\": \"https://example.com/c.jar\", \"sha1\": \"00\", \"size\": 10}},"
            + "\"arguments\": {"
            + "  \"jvm\": ["
            + "    \"-cp\", \"${classpath}\""
            + "  ],"
            + "  \"game\": ["
            + "    \"--username\", \"${auth_player_name}\","
            + "    \"--version\", \"${version_name}\","
            + "    \"--gameDir\", \"${game_directory}\","
            + "    \"--assetsDir\", \"${assets_root}\","
            + "    \"--assetIndex\", \"${assets_index_name}\","
            + "    \"--uuid\", \"${auth_uuid}\","
            + "    \"--accessToken\", \"${auth_access_token}\""
            + "  ]"
            + "}"
            + "}";

    @Test
    public void testBuildOfflineArguments() {
        MojangManifest.VersionDetail detail = MojangManifest.parseVersionJson(JSON);
        File nativesDir = new File("/tmp/mc/natives");
        File gameDir = new File("/tmp/mc/game");
        File assetsDir = new File("/tmp/mc/assets");
        List<File> cp = List.of(new File("/tmp/mc/lib.jar"), new File("/tmp/mc/client.jar"));

        String playerName = "Steve";
        String uuid = MinecraftLauncher.offlineUUID(playerName);
        assertNotNull(uuid);

        LaunchArgBuilder builder = new LaunchArgBuilder(
                detail, nativesDir, cp, gameDir, assetsDir,
                playerName, uuid, "null", "release",
                List.of("-Dextra=true"), 3072);

        List<String> args = builder.build();
        assertNotNull(args);

        // Memory
        assertTrue(args.contains("-Xmx3072m"));
        assertTrue(args.contains("-Xms256m"));

        // Natives
        assertTrue(args.contains("-Djava.library.path=" + nativesDir.getAbsolutePath()));

        // Extra JVM args
        assertTrue(args.contains("-Dextra=true"));

        // Main class
        assertTrue(args.contains("net.minecraft.client.main.Main"));

        // Game args
        int userIdx = args.indexOf("--username");
        assertTrue(userIdx >= 0 && userIdx + 1 < args.size());
        assertEquals("Steve", args.get(userIdx + 1));

        int uuidIdx = args.indexOf("--uuid");
        assertTrue(uuidIdx >= 0 && uuidIdx + 1 < args.size());
        assertEquals(uuid, args.get(uuidIdx + 1));

        int tokenIdx = args.indexOf("--accessToken");
        assertTrue(tokenIdx >= 0 && tokenIdx + 1 < args.size());
        assertEquals("null", args.get(tokenIdx + 1));
    }
}
