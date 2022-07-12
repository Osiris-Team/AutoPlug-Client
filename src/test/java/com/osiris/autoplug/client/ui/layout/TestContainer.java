/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Magical container that makes layouting
 * as simple as writing basic english.
 */
public class TestContainer extends JPanel {
    public Map<String, String> childStyleMap = new HashMap<>();

    public TestContainer() {
        putChildStyle(Style.center);
        putChildStyle(Style.padding);
        setLayout(new TestLayout(new Dimension(1000, 100)));
    }

    // |c1c2c3|
    // components can be packed together like above
    // |c1 c2 c3|
    // components can have gaps between each other
    // solve by adding component paddings in px for: right, left, top, bottom
    // |c1 c2          c3|
    // can have absolute postions like in this case at the end.
    // solve by adding component positions for: right, left, top, bottom, center
    // default setting for layout: center
    // |c1 +C2+ c3|
    // can be vertically bigger or smaller than others

    /**
     * Access this container in a thread-safe way. <br>
     * Performs {@link #revalidate()} when done running the provided code.
     *
     * @param code to be run in this containers' context.
     */
    public synchronized TestContainer access(Runnable code) {
        code.run();
        revalidate();
        add(null, Style.bottom);
        return this;
    }

    /**
     * Adds provided style or replaces existing.
     */
    public void putChildStyle(Style style) {
        childStyleMap.put(style.key, style.value);
    }

    public Component add(Component comp, Style style) { // Without this method
        // the super method somehow gets used
        return add(comp, new Style[]{style});
    }

    public Component add(Component comp, Style... styles) {
        if (styles != null)
            for (Style style : styles) {
                if (style != null) putChildStyle(style);
            }
        comp.setName(childStylesMapToJsonString());
        return super.add(comp);
    }

    public String childStylesMapToJsonString() {
        JsonObject obj = new JsonObject();
        for (String key : childStyleMap.keySet()) {
            obj.addProperty(key, childStyleMap.get(key));
        }
        return new Gson().toJson(obj);
    }

    @Override
    public Component add(Component comp) {
        return this.add(comp);
    }

    @Override
    public Component add(String name, Component comp) {
        return this.add(comp);
    }

    @Override
    public Component add(Component comp, int index) {
        return this.add(comp);
    }

    @Override
    public void add(@NotNull Component comp, Object constraints) {
        this.add(comp);
    }

    @Override
    public void add(Component comp, Object constraints, int index) {
        this.add(comp);
    }
}
