/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilsStringTest {

    @Test
    void splitBySpacesAndQuotes() throws Exception {
        List<String> l;
        l = new UtilsString().splitBySpacesAndQuotes("hello there");
        assertEquals(2, l.size());
        l = new UtilsString().splitBySpacesAndQuotes("\"Hello there\" my friend \"!\"");
        assertEquals(4, l.size());
        l = new UtilsString().splitBySpacesAndQuotes("hello there \"mate\"");
        assertEquals(3, l.size());
        assertEquals("hello", l.get(0));
        assertEquals("there", l.get(1));
        assertEquals("\"mate\"", l.get(2));
        l = new UtilsString().splitBySpacesAndQuotes("" +
                "\"D:\\Coding\\JAVA\\AutoPlug-Client\\AP-TEST-SERVER\\autoplug\\system\\jre\\jdk-17.0.1+12\\bin\\java.exe\"" +
                " -Xms2G" +
                " -Xmx2G" +
                " -jar" +
                " D:\\Coding\\JAVA\\AutoPlug-Client\\AP-TEST-SERVER\\fabric-server-mc.1.18.2-loader.0.13.3-launcher.0.10.2.jar" +
                " nogui");
        assertEquals(6, l.size());
    }

    @Test
    void indexOf_singleOccurrence() {
        UtilsString utilsString = new UtilsString();
        int result = utilsString.indexOf("Hello world!", 'o', 1);
        assertEquals(4, result);
    }

}