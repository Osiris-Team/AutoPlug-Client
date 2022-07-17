/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

import java.awt.*;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Note that this class is private to classes outside this package, due to it
 * being specifically designed for {@link TestContainer} and thus not being compatible with other containers.
 * If used on another container you probably will get {@link ClassCastException}s. <p>
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

    @Override
    public void addLayoutComponent(String name, Component comp) {
        System.out.println("addLayoutComponent " + comp);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        System.out.println("removeLayoutComponent " + comp);
    }

    @Override
    public Dimension preferredLayoutSize(Container _container) {
        // Not 100% safe this method is always called, thus nothing important being done here.
        return preferredSize;
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        // Not 100% safe this method is always called, thus nothing important being done here.
        return minimumSize;
    }

    @Override
    public void layoutContainer(Container _container) {
        TestContainer container = (TestContainer) _container;
        synchronized (container.getTreeLock()) {
            System.out.println("layoutContainer " + container);
            container.setMaximumSize(preferredSize); // Make sure maximum is never bigger than preferred.
            Insets insets = container.getInsets();
            int startX = insets.left;
            int startY = insets.top;
            int x = startX;
            int y = startY;

            int totalHeightTallestCompInRow = 0;
            // To avoid memory filling up with leftover (already removed) components
            // we replace the original compsAndStyles map after being done,
            // with the map below, that only contains currently added/active components.
            Map<Component, Styles> newCompsAndStyles = new HashMap<>();
            for (Component comp : container.getComponents()) {
                Styles styles = container.compsAndStyles.get(comp);
                if (styles == null) {
                    styles = new Styles(); // Components added via the regular container add() methods
                    styles.getMap().putAll(container.defaultCompStyles.getMap()); // Add defaults
                }
                newCompsAndStyles.put(comp, styles);

                if (comp.isVisible()) {
                    Dimension compSize = comp.getPreferredSize();
                    int totalWidth = compSize.width;
                    int totalHeight = compSize.height;
                    byte paddingLeft = 0, paddingRight = 0, paddingTop = 0, paddingBottom = 0;

                    // Calc total width and height
                    for (Map.Entry<String, String> entry : styles.getMap().entrySet()) {
                        String key, value;
                        try {
                            key = entry.getKey();
                            value = entry.getValue();
                        } catch (IllegalStateException ise) {
                            // this usually means the entry is no longer in the map.
                            throw new ConcurrentModificationException(ise);
                        }
                        // Calc total height and width
                        if (Objects.equals(key, Style.padding_left.key)) {
                            paddingLeft = Byte.parseByte(value);
                            totalWidth += paddingLeft;
                        } else if (Objects.equals(key, Style.padding_right.key)) {
                            paddingRight = Byte.parseByte(value);
                            totalWidth += paddingRight;
                        } else if (Objects.equals(key, Style.padding_top.key)) {
                            paddingTop = Byte.parseByte(value);
                            totalHeight += paddingTop;
                        } else if (Objects.equals(key, Style.padding_bottom.key)) {
                            paddingBottom = Byte.parseByte(value);
                            totalHeight += paddingBottom;
                        }
                    }
                    styles.debugInfo = new DebugInfo(totalWidth, totalHeight, paddingLeft, paddingRight, paddingTop, paddingBottom);

                    // Align the component either vertically or horizontally
                    String alignment = styles.getMap().get(Style.vertical.key);
                    if (alignment == null) alignment = Style.horizontal.value;
                    boolean isHorizontal = Objects.equals(alignment, Style.horizontal.value);

                    if (totalHeight > totalHeightTallestCompInRow)
                        totalHeightTallestCompInRow = totalHeight;

                    if (!isHorizontal) {
                        x = startX; // Move comp to the left (start)
                        y += totalHeightTallestCompInRow; // Move comp to the next line
                        totalHeightTallestCompInRow = totalHeight; // Directly update variable since we are now in the next row
                    }

                    // Set the component's size and position.
                    comp.setBounds(x + paddingLeft, y + paddingTop, compSize.width, compSize.height);
                    // Set the next components start positions.
                    x += totalWidth;

                }
            }
            container.compsAndStyles = newCompsAndStyles;
            if (container.isDebug) drawDebugLines(container); // Must be done after replacing the map
        }
    }


    private void drawDebugLines(TestContainer container) {
        Graphics2D g = (Graphics2D) container.getGraphics();
        if (g == null) return;
        for (Component comp : container.getComponents()) {
            Styles styles = container.compsAndStyles.get(comp);
            Objects.requireNonNull(styles);
            int x = comp.getX() - styles.debugInfo.paddingLeft;
            int y = comp.getY() - styles.debugInfo.paddingTop;
            int width = comp.getWidth() + styles.debugInfo.paddingLeft + styles.debugInfo.paddingRight;
            int height = comp.getHeight() + styles.debugInfo.paddingTop + styles.debugInfo.paddingBottom;
            g.setColor(Color.red);
            g.drawRect(x, y, width, height); // Full width/height with padding included
            g.setColor(Color.blue); // Actual component width/height
            g.drawRect(comp.getX(), comp.getY(), comp.getWidth(), comp.getHeight());
        }
    }

    public String toString() {
        System.out.println("toString");
        String str = "";
        return getClass().getName() + "[vgap=" + vgap + str + "]";
    }
}
