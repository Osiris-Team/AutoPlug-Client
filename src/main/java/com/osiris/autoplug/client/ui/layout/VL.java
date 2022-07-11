/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

import com.osiris.autoplug.client.ui.FlexLayout;

import java.awt.Toolkit;
import java.awt.*;

/**
 * Vertical layout aka container, with horizontal component alignment capability.
 */
public class VL extends Table {
    public Container parent;
    /**
     * If false this container displays components horizontally. <br>
     * Call {@link #updateSize()} after changing this value.
     */
    public boolean isVertical;
    public int minWidthPercent, minHeightPercent, maxWidthPercent, maxHeightPercent;
    public FlexLayout layout;

    /**
     * Initialises as vertical layout with 100% height and width.
     */
    public VL(Container parent) {
        this(parent, true, 100, 100, 100, 100);
    }

    /**
     * Initialises with 100% height and width.
     */
    public VL(Container parent, boolean isVertical) {
        this(parent, isVertical, 100, 100, 100, 100);
    }

    /**
     * Initialises with 100% width.
     */
    public VL(Container parent, boolean isVertical, int heightPercent) {
        this(parent, isVertical, 100, heightPercent, 100, heightPercent);
    }

    public VL(Container parent, boolean isVertical, int widthPercent, int heightPercent) {
        this(parent, isVertical, widthPercent, heightPercent, widthPercent, heightPercent);
    }

    public VL(Container parent, boolean isVertical, int minWidthPercent, int minHeightPercent,
              int maxWidthPercent, int maxHeightPercent) {
        this.parent = parent;
        this.isVertical = isVertical;
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
}
