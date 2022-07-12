/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Note that this class is private to classes outside this package, due to it
 * being specifically designed for {@link TestContainer} and thus not being compatible with other containers. <p>
 * <p>
 * Features: <br>
 * - Ensures the container never expands if the components require more space.
 * This is done by overriding the container max size at {@link #preferredLayoutSize(Container)}. <br>
 */
class TestLayout implements LayoutManager {
    private final int vgap;
    public int minWidth = 0, minHeight = 0;
    public int preferredWidth = 0, preferredHeight = 0;
    public Dimension minimumSize, preferredSize;

    public TestLayout(Dimension size) {
        this(size, size);
    }

    public TestLayout(Dimension minimumSize, Dimension preferredSize) {
        this.minimumSize = minimumSize;
        this.preferredSize = preferredSize;
        vgap = 5;
    }

    /* Required by LayoutManager. */
    public void addLayoutComponent(String name, Component comp) {
        System.out.println("addLayoutComponent " + comp);
    }

    /* Required by LayoutManager. */
    public void removeLayoutComponent(Component comp) {
        System.out.println("removeLayoutComponent " + comp);
    }

    private void setSizes(Container parent) {
        System.out.println("setSizes " + parent);
        int nComps = parent.getComponentCount();
        Dimension d = null;

        //Reset preferred/minimum width and height.
        preferredWidth = 0;
        preferredHeight = 0;
        minWidth = 0;
        minHeight = 0;

        for (int i = 0; i < nComps; i++) {
            Component c = parent.getComponent(i);
            if (c.isVisible()) {
                d = c.getPreferredSize();

                if (i > 0) {
                    preferredWidth += d.width / 2;
                    preferredHeight += vgap;
                } else {
                    preferredWidth = d.width;
                }
                preferredHeight += d.height;

                minWidth = Math.max(c.getMinimumSize().width,
                        minWidth);
                minHeight = preferredHeight;
            }
        }
    }


    /* Required by LayoutManager. */
    public Dimension preferredLayoutSize(Container container) {
        System.out.println("preferredLayoutSize " + container);
        container.setMaximumSize(preferredSize);
        return preferredSize;
    }

    /* Required by LayoutManager. */
    public Dimension minimumLayoutSize(Container parent) {
        System.out.println("minimumLayoutSize " + parent);
        return minimumSize;
    }

    /* Required by LayoutManager. */
    /*
     * This is called when the panel is first displayed,
     * and every time its size changes.
     * Note: You CAN'T assume preferredLayoutSize or
     * minimumLayoutSize will be called -- in the case
     * of applets, at least, they probably won't be.
     */
    public void layoutContainer(Container container) {
        System.out.println("layoutContainer " + container);
        Insets insets = container.getInsets();
        int maxWidth = container.getWidth()
                - (insets.left + insets.right);
        int maxHeight = container.getHeight()
                - (insets.top + insets.bottom);
        int nComps = container.getComponentCount();
        int previousWidth = 0, previousHeight = 0;
        int x = 0, y = insets.top;
        int rowh = 0, start = 0;
        int xFudge = 0, yFudge = 0;
        boolean oneColumn = false;

        for (Component comp : container.getComponents()) {
            if (comp.isVisible()) {
                // Get styles map stored as json in the component name
                JsonObject stylesObj = JsonParser.parseString(comp.getName()).getAsJsonObject();
                Map<String, String> stylesMap = new HashMap<>();
                for (String key : stylesObj.keySet()) {
                    stylesMap.put(key, stylesObj.get(key).isJsonNull() ? null : stylesObj.get(key).getAsString());
                }
                String alignment = stylesMap.get(Style.vertical.key);
                String position = stylesMap.get(Style.left.key);
                String paddingPx = stylesMap.get(Style.padding_xs.key);
                String paddingPosition = stylesMap.get(Style.padding_left.key);

                Dimension compSize = comp.getPreferredSize();
                int totalWidth = compSize.width;
                int totalHeight = compSize.height;
                // Set the component's size and position.
                comp.setBounds(x, y, totalWidth, totalHeight);
                x += totalWidth;
                y += totalHeight;
            }
        }
    }

    public String toString() {
        System.out.println("toString");
        String str = "";
        return getClass().getName() + "[vgap=" + vgap + str + "]";
    }
}
