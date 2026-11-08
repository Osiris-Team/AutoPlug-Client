/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 tests.
 */

package com.osiris.autoplug.client.minecraft;

import com.osiris.autoplug.client.minecraft.server.ServerEntry;
import com.osiris.autoplug.client.minecraft.server.ServersDat;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ServersDatTest {

    @Test
    public void testRoundTripPreservingUnknownTags() throws Exception {
        File tempDat = Files.createTempFile("servers-", ".dat").toFile();
        tempDat.deleteOnExit();

        List<ServerEntry> original = new ArrayList<>();
        ServerEntry s1 = new ServerEntry("Hypixel Network", "mc.hypixel.net");
        s1.acceptTextures = 1;
        s1.icon = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==";
        s1.extraTags.put("customUnknownTag", "extraValue123");
        original.add(s1);

        ServerEntry s2 = new ServerEntry("Local Test Server", "127.0.0.1:25565");
        s2.extraTags.put("anotherTag", "valueXYZ");
        original.add(s2);

        // Write
        ServersDat.write(tempDat, original);
        assertTrue(tempDat.exists());
        assertTrue(tempDat.length() > 0);

        // Read back
        List<ServerEntry> readBack = ServersDat.read(tempDat);
        assertNotNull(readBack);
        assertEquals(2, readBack.size());

        ServerEntry r1 = readBack.get(0);
        assertEquals("Hypixel Network", r1.name);
        assertEquals("mc.hypixel.net", r1.ip);
        assertEquals(1, r1.acceptTextures);
        assertEquals("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==", r1.icon);
        assertEquals("extraValue123", r1.extraTags.get("customUnknownTag"));

        ServerEntry r2 = readBack.get(1);
        assertEquals("Local Test Server", r2.name);
        assertEquals("127.0.0.1:25565", r2.ip);
        assertEquals("valueXYZ", r2.extraTags.get("anotherTag"));
        assertEquals("127.0.0.1", r2.host());
        assertEquals(25565, r2.port());
    }

    @Test
    public void testEmptyServersDat() throws Exception {
        File tempDat = Files.createTempFile("servers-empty-", ".dat").toFile();
        tempDat.deleteOnExit();

        ServersDat.write(tempDat, List.of());
        List<ServerEntry> list = ServersDat.read(tempDat);
        assertNotNull(list);
        assertEquals(0, list.size());
    }
}
