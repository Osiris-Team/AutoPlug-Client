/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UtilsMap {

    public <K, V> void printStringMap(Map<K, V> map) {
        System.out.println(getStringMapFormatted(map));
    }

    /**
     * Returns a formatted String representation of the map object.
     */
    public <K, V> String getStringMapFormatted(Map<K, V> map) {
        String result = "";
        for (K key :
                map.keySet()) {
            result = result + "key: " + key + " value: " + map.get(key) + "\n";
        }
        return result;
    }

    /**
     * Returns a new sorted entries list that is sorted by value in ascending (small to big) order.
     */
    public <K, V extends Comparable<? super V>> List<Map.Entry<K, V>> getEntriesListSortedByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue());

        return new ArrayList<>(list);
    }

    /**
     * Returns a new sorted map that is sorted by value in ascending (small to big) order.
     */
    public <K, V extends Comparable<? super V>> Map<K, V> getSortedByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }


}
