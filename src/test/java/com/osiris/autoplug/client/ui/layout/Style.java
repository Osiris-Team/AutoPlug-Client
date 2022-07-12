/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

/**
 * Style for a component. <br>
 * Internally represented by a binary string
 * with a length of 10. <br>
 * 0 000000000 <br>
 * The numbers in this class are indexes that point to
 * a specific bit. <br>
 * 1 means that specific feature is enabled, 0 means it's not.
 */
public class Style {
    // ALIGNMENTS
    public static final Style vertical = new Style("align", "vertical");
    public static final Style horizontal = new Style("align", "horizontal");
    // POSITIONS
    public static final Style left = new Style("pos", "left");
    public static final Style right = new Style("pos", "right");
    public static final Style top = new Style("pos", "top");
    public static final Style bottom = new Style("pos", "bottom");
    public static final Style center = new Style("pos", "center");
    // PADDING SIZES
    public static final Style padding_xs = new Style("padding-px", "4"); // 0.25rem
    public static final Style padding_s = new Style("padding-px", "8"); // 0.5rem
    public static final Style padding_m = new Style("padding-px", "16"); // 1rem
    public static final Style padding_l = new Style("padding-px", "24"); // 1.5rem
    public static final Style padding_xl = new Style("padding-px", "40"); // 2.5rem
    public static final Style padding = padding_s;
    // PADDING POSITIONS
    public static final Style padding_left = new Style("padding-pos", "left");
    public static final Style padding_right = new Style("padding-pos", "right");
    public static final Style padding_top = new Style("padding-pos", "top");
    public static final Style padding_bottom = new Style("padding-pos", "bottom");
    public final String key;
    public final String value;

    public Style(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Expects a string looking something like this: <br>
     * 0 1 4 2 0 3 <br>
     * The string gets split by spaces and then converted
     * to a byte array containing the numbers.
     */
    public static byte[] from(String s) {
        String[] s1 = s.split(" ");
        byte[] arr = new byte[s1.length];
        for (int i = 0; i < s1.length; i++) {
            arr[i] = Byte.parseByte(s1[i]);
        }
        return arr;
    }
}
