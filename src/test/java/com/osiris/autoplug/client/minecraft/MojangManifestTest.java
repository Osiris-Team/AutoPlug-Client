/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 tests.
 */

package com.osiris.autoplug.client.minecraft;

import com.osiris.autoplug.client.minecraft.launcher.MojangManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MojangManifestTest {

    private static final String SAMPLE_VERSION_JSON = "{"
            + "\"id\": \"1.21.1\","
            + "\"mainClass\": \"net.minecraft.client.main.Main\","
            + "\"javaVersion\": {\"majorVersion\": 21},"
            + "\"assetIndex\": {\"id\": \"17\", \"url\": \"https://example.com/assets.json\", \"sha1\": \"abc123\"},"
            + "\"downloads\": {\"client\": {\"url\": \"https://example.com/client.jar\", \"sha1\": \"def456\", \"size\": 12345678}},"
            + "\"arguments\": {"
            + "  \"jvm\": ["
            + "    \"-Djava.library.path=${natives_directory}\","
            + "    {\"rules\": [{\"action\": \"allow\", \"os\": {\"name\": \"osx\"}}], \"value\": \"-XstartOnFirstThread\"}"
            + "  ],"
            + "  \"game\": ["
            + "    \"--username\", \"${auth_player_name}\","
            + "    \"--version\", \"${version_name}\""
            + "  ]"
            + "},"
            + "\"libraries\": ["
            + "  {\"name\": \"com.mojang:authlib:6.0.55\", \"downloads\": {\"artifact\": {\"url\": \"https://example.com/authlib.jar\", \"sha1\": \"111\", \"size\": 1000}}}"
            + "]"
            + "}";

    @Test
    public void testParseVersionJson() {
        MojangManifest.VersionDetail detail = MojangManifest.parseVersionJson(SAMPLE_VERSION_JSON);
        assertNotNull(detail);
        assertEquals("1.21.1", detail.id);
        assertEquals("net.minecraft.client.main.Main", detail.mainClass);
        assertEquals(21, detail.javaVersion);
        assertEquals("17", detail.assetIndex);
        assertEquals("https://example.com/client.jar", detail.clientJarUrl);
        assertEquals("def456", detail.clientJarSha1);
        assertEquals(12345678, detail.clientJarSize);

        // Check libraries
        assertEquals(1, detail.libraries.size());
        MojangManifest.Library lib = detail.libraries.get(0);
        assertEquals("com.mojang:authlib:6.0.55", lib.name);
        assertEquals("com/mojang/authlib/6.0.55/authlib-6.0.55.jar", lib.toRelativePath());
        assertEquals("https://example.com/authlib.jar", lib.url);

        // Check arguments
        assertEquals(2, detail.jvmArguments.size());
        assertEquals(4, detail.gameArguments.size());
    }

    @Test
    public void testArgumentRules() {
        MojangManifest.VersionDetail detail = MojangManifest.parseVersionJson(SAMPLE_VERSION_JSON);
        MojangManifest.Argument osxArg = detail.jvmArguments.get(1);
        assertEquals("-XstartOnFirstThread", osxArg.value);
        boolean isOsx = System.getProperty("os.name", "").toLowerCase().contains("mac");
        assertEquals(isOsx, osxArg.appliesOnCurrentOs());
    }
}
