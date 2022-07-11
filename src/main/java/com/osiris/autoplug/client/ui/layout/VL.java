/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

import org.jetbrains.annotations.NotNull;

import java.awt.Toolkit;
import java.awt.*;
import java.util.Objects;

/**
 * Vertical layout aka container, with horizontal component alignment capability,
 * and many more useful features.
 */
public class VL extends Table {
    public Container parent;
    public int minWidthPercent, minHeightPercent, maxWidthPercent, maxHeightPercent;

    /**
     * Initialises with 100% height and 100% width.
     */
    public VL(Container parent) {
        this(parent, 100, 100, 100, 100);
    }

    /**
     * Initialises with provided height and 100% width.
     */
    public VL(Container parent, int heightPercent) {
        this(parent, 100, heightPercent, 100, heightPercent);
    }

    public VL(Container parent, int widthPercent, int heightPercent) {
        this(parent, widthPercent, heightPercent, widthPercent, heightPercent);
    }

    public VL(Container parent, int minWidthPercent, int minHeightPercent,
              int maxWidthPercent, int maxHeightPercent) {
        this.parent = parent;
        this.minWidthPercent = minWidthPercent;
        this.minHeightPercent = minHeightPercent;
        this.maxWidthPercent = maxWidthPercent;
        this.maxHeightPercent = maxHeightPercent;
        setBackground(new Color(0, true)); // transparent
        updateSize();
    }

    private void updateSize() {
        int parentWidth, parentHeight;
        if (parent != null) {
            parentWidth = parent.getWidth();
            parentHeight = parent.getHeight();
        } else { // If no parent provided use the screen dimensions
            parentWidth = java.awt.Toolkit.getDefaultToolkit().getScreenSize().width;
            parentHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        }

        setSize(new Dimension(parentWidth / 100 * minWidthPercent,
                parentHeight / 100 * minHeightPercent));
        setMinimumSize(new Dimension(parentWidth / 100 * minWidthPercent,
                parentHeight / 100 * minHeightPercent));
        setMaximumSize(new Dimension(parentWidth / 100 * maxWidthPercent,
                parentHeight / 100 * maxHeightPercent));
    }

    /**
     * Appends the component on the horizontal axis.
     */
    public Cell addH(Component c) {
        return addCell(c);
    }

    /**
     * Appends the component on the vertical axis.
     */
    public Cell addV(Component c) {
        return addCell(c).row();
    }

    /**
     * Removes the component at the provided index or does nothing if failed to find index.
     */
    public void _remove(int index) {
        try {
            removeCell(index);
        } catch (IndexOutOfBoundsException ignored) {
        }
    }

    /**
     * Removes the provided component or does nothing if failed to find.
     */
    public void _remove(Component comp) {
        Cell cellToRemove = null;
        int i = 0;
        for (; i < getCells().size(); i++) {
            Cell cell = getCells().get(i);
            if (Objects.equals(cell.widget, comp)) // widget is the component
            {
                cellToRemove = cell;
                break;
            }
        }
        if (cellToRemove != null)
            removeCell(i);
    }

    public void updateUI() {
        revalidate();
        repaint();
    }

    /**
     * This method will do nothing. <br>
     * Use {@link #addV(Component)} or {@link #addH(Component)} instead.
     */
    @Deprecated
    @Override
    public Component add(Component comp) {
        return super.add(comp);
    }

    /**
     * This method will do nothing. <br>
     * Use {@link #addV(Component)} or {@link #addH(Component)} instead.
     */
    @Deprecated
    @Override
    public Component add(String name, Component comp) {
        return super.add(name, comp);
    }

    /**
     * This method will do nothing. <br>
     * Use {@link #addV(Component)} or {@link #addH(Component)} instead.
     */
    @Deprecated
    @Override
    public Component add(Component comp, int index) {
        return super.add(comp, index);
    }

    /**
     * This method will do nothing. <br>
     * Use {@link #addV(Component)} or {@link #addH(Component)} instead.
     */
    @Deprecated
    @Override
    public void add(@NotNull Component comp, Object constraints) {
        super.add(comp, constraints);
    }

    /**
     * This method will do nothing. <br>
     * Use {@link #addV(Component)} or {@link #addH(Component)} instead.
     */
    @Deprecated
    @Override
    public void add(Component comp, Object constraints, int index) {
        super.add(comp, constraints, index);
    }

    /**
     * This method will do nothing. <br>
     * Use {@link #_remove(int)} instead.
     */
    @Deprecated
    public void remove(int index) {
        super.remove(index);
    }

    /**
     * This method will do nothing. <br>
     * Use {@link #_remove(Component)} instead.
     */
    @Deprecated
    @Override
    public void remove(Component comp) {
        super.remove(comp);
    }

    /**
     * This method will do nothing. <br>
     * Use {@link #clear()} instead.
     */
    @Deprecated
    @Override
    public void removeAll() {
        super.removeAll();
    }
}
