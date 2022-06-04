/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import javax.swing.*;
import java.awt.*;

public class CoolContainer extends JPanel {

    public Container parent;
    /**
     * If false this container displays components horizontally. <br>
     * Call {@link #updateLayout()} after changing this value.
     */
    public boolean isVertical;
    public int minWidthPercent, minHeightPercent, maxWidthPercent, maxHeightPercent;
    public int paddingX, paddingY;
    public int gapX, gapY;
    public FlexLayout layout;

    /**
     * Initialises as vertical layout with 100% height and width.
     */
    public CoolContainer(Container parent) {
        this(parent, true, 100, 100, 100, 100);
    }

    /**
     * Initialises with 100% height and width.
     */
    public CoolContainer(Container parent, boolean isVertical) {
        this(parent, isVertical, 100, 100, 100, 100);
    }

    /**
     * Initialises with 100% width.
     */
    public CoolContainer(Container parent, boolean isVertical, int heightPercent) {
        this(parent, isVertical, 100, heightPercent, 100, heightPercent);
    }

    public CoolContainer(Container parent, boolean isVertical, int widthPercent, int heightPercent) {
        this(parent, isVertical, widthPercent, heightPercent, widthPercent, heightPercent);
    }

    public CoolContainer(Container parent, boolean isVertical, int minWidthPercent, int minHeightPercent,
                         int maxWidthPercent, int maxHeightPercent) {
        this.parent = parent;
        this.isVertical = isVertical;
        this.minWidthPercent = minWidthPercent;
        this.minHeightPercent = minHeightPercent;
        this.maxWidthPercent = maxWidthPercent;
        this.maxHeightPercent = maxHeightPercent;
        setBackground(new Color(0, true)); // transparent
        updateLayout();
    }

    private void updateLayout() {
        int parentWidth, parentHeight;
        if (parent != null) {
            parentWidth = parent.getWidth();
            parentHeight = parent.getHeight();
        } else { // If no parent provided use the screen dimensions
            parentWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
            parentHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        }

        setSize(new Dimension(parentWidth / 100 * minWidthPercent,
                parentHeight / 100 * minHeightPercent));
        setMinimumSize(new Dimension(parentWidth / 100 * minWidthPercent,
                parentHeight / 100 * minHeightPercent));
        setMaximumSize(new Dimension(parentWidth / 100 * maxWidthPercent,
                parentHeight / 100 * maxHeightPercent));

        int compCount = getComponentCount();
        this.layout = new FlexLayout(compCount, compCount);
        this.setLayout(layout);
        this.layout.setXgap(gapX);
        this.layout.setYgap(gapY);
        for (int i = 0; i < compCount; i++) {
            this.layout.setColProp(i, FlexLayout.EXPAND);
            this.layout.setRowProp(i, FlexLayout.EXPAND);
        }
    }

    public CoolContainer addComp(String constrains, Component comp) {
        super.add(constrains, comp);
        return this;
    }

    public CoolContainer withPadding() {
        withPadding(CoolSpace.S);
        return this;
    }

    /**
     * Useful default sizes in {@link CoolSpace}. <br>
     */
    public CoolContainer withPadding(int pixel) {
        this.paddingY = pixel;
        this.paddingX = pixel;
        updateLayout();
        return this;
    }

    public CoolContainer withGap() {
        withGap(CoolSpace.S);
        return this;
    }

    /**
     * Useful default sizes in {@link CoolSpace}. <br>
     */
    public CoolContainer withGap(int pixel) {
        this.gapX = pixel;
        this.gapY = pixel;
        updateLayout();
        return this;
    }

}
