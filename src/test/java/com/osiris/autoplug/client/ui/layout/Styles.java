/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper around a {@link Style}s map that
 * provides util methods for setting and retrieving stuff.
 */
public class Styles {
    /**
     * Map containing the actual styles.
     *
     * @see Style
     */
    public Map<String, String> map;
    /**
     * Contains details relevant to debugging
     * layouts and components.
     * Usually gets updated/set inside the layout class.
     *
     * @see TestLayout
     */
    public DebugInfo debugInfo;

    public Styles() {
        this.map = new HashMap<>();
    }

    public Styles(Map<String, String> map) {
        this.map = map;
    }

    // ALIGNMENT

    public Styles vertical() {
        map.put(Style.vertical.key, Style.vertical.value);
        return this;
    }

    public Styles horizontal() {
        map.put(Style.horizontal.key, Style.horizontal.value);
        return this;
    }

    // POSITION

    public Styles left() {
        map.put(Style.left.key, Style.left.value);
        return this;
    }

    public Styles right() {
        map.put(Style.right.key, Style.right.value);
        return this;
    }

    public Styles top() {
        map.put(Style.top.key, Style.top.value);
        return this;
    }

    public Styles bottom() {
        map.put(Style.bottom.key, Style.bottom.value);
        return this;
    }

    public Styles center() {
        map.put(Style.center.key, Style.center.value);
        return this;
    }

    // PADDING

    /**
     * Adds default padding to the left, right, top and bottom.
     */
    public Styles padding() {
        map.put(Style.padding_left.key, Style.padding_left.value);
        map.put(Style.padding_right.key, Style.padding_right.value);
        map.put(Style.padding_top.key, Style.padding_top.value);
        map.put(Style.padding_bottom.key, Style.padding_bottom.value);
        return this;
    }

    public Styles padding(int px) {
        map.put(Style.padding_left.key, "" + (byte) px);
        map.put(Style.padding_right.key, "" + (byte) px);
        map.put(Style.padding_top.key, "" + (byte) px);
        map.put(Style.padding_bottom.key, "" + (byte) px);
        return this;
    }

    public Styles paddingLeft() {
        map.put(Style.padding_left.key, Style.padding_left.value);
        return this;
    }

    public Styles paddingLeft(int px) {
        map.put(Style.padding_left.key, "" + (byte) px);
        return this;
    }

    public Styles paddingRight() {
        map.put(Style.padding_right.key, Style.padding_right.value);
        return this;
    }

    public Styles paddingRight(int px) {
        map.put(Style.padding_right.key, "" + (byte) px);
        return this;
    }

    public Styles paddingTop() {
        map.put(Style.padding_top.key, Style.padding_top.value);
        return this;
    }

    public Styles paddingTop(int px) {
        map.put(Style.padding_top.key, "" + (byte) px);
        return this;
    }

    public Styles paddingBottom() {
        map.put(Style.padding_bottom.key, Style.padding_bottom.value);
        return this;
    }

    public Styles paddingBottom(int px) {
        map.put(Style.padding_bottom.key, "" + (byte) px);
        return this;
    }

    /**
     * Deletes all the padding.
     */
    public Styles delPadding() {
        map.remove(Style.padding_left.key);
        map.remove(Style.padding_right.key);
        map.remove(Style.padding_top.key);
        map.remove(Style.padding_bottom.key);
        return this;
    }


    public Map<String, String> getMap() {
        return map;
    }

    public void setMap(Map<String, String> stylesMap) {
        this.map = stylesMap;
    }
}
