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
import java.util.Objects;

public class CoolContainer extends JPanel {
    public Container parent;
    /**
     * If false this container displays components horizontally. <br>
     * Call {@link #updateLayout()} after changing this value.
     */
    public boolean isVertical;
    public int minWidthPercent, minHeightPercent, maxWidthPercent, maxHeightPercent;
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
    }

    public CoolContainer withFlexLayout(int width, int height) {
        setFlexLayout(new FlexLayout(width, height));
        return this;
    }

    public CoolContainer setFlexLayoutXFlag(int index, int flag) {
        Objects.requireNonNull(layout).setColProp(index, flag);
        return this;
    }

    public CoolContainer setFlexLayoutYFlag(int index, int flag) {
        Objects.requireNonNull(layout).setRowProp(index, flag);
        return this;
    }

    public void setFlexLayout(FlexLayout ly) {
        super.setLayout(ly);
        this.layout = ly;
    }

    public void withGridBagLayout() {
        setGridBagLayout(new GridBagLayout());
    }

    public void setGridBagLayout(GridBagLayout ly) {
        super.setLayout(ly);
    }

    public CoolContainer add(Component comp, CStyle... styles) {
        super.add(comp, styles);
        return this;
    }
}
