/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class UtilsMapTest {
    @Test
    void sortByValue() {
        UtilsMap utilsMap = new UtilsMap();
        Map<String, Integer> map = new HashMap<>();
        map.put("fifty", 50);
        map.put("null", 0);
        map.put("ten", 10);
        utilsMap.printStringMap(utilsMap.getSortedByValue(map));
    }
}