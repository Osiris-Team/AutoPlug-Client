/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

public class DebugInfo {

    /**
     * Returns the total width of a component
     * including paddings.
     */
    public int totalWidth;
    /**
     * Returns the total height of a component
     * including paddings.
     */
    public int totalHeight;

    public byte paddingLeft, paddingRight, paddingTop, paddingBottom;

    public DebugInfo(int totalWidth, int totalHeight, byte paddingLeft, byte paddingRight, byte paddingTop, byte paddingBottom) {
        this.totalWidth = totalWidth;
        this.totalHeight = totalHeight;
        this.paddingLeft = paddingLeft;
        this.paddingRight = paddingRight;
        this.paddingTop = paddingTop;
        this.paddingBottom = paddingBottom;
    }
}
