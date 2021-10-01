/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import java.util.Map;

public class UtilsMap {

    public void printStringMap(Map<String, String> map) {
        System.out.println(getStringMapFormatted(map));
    }

    /**
     * Returns a formatted String representation of the map object.
     */
    public String getStringMapFormatted(Map<String, String> map) {
        String result = "";
        for (String key :
                map.keySet()) {
            result = result + "key: " + key + " value: " + map.get(key) + "\n";
        }
        return result;
    }

}
