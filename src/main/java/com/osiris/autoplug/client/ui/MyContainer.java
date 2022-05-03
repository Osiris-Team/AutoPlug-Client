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

public class MyContainer extends JPanel {

    public MyContainer(boolean vertical, int heightPercent) {
        this(vertical, 100, heightPercent, 100, heightPercent);
    }

    public MyContainer(boolean vertical, int minWidthPercent, int minHeightPercent) {
        this(vertical, minWidthPercent, minHeightPercent, minWidthPercent, minHeightPercent);
    }

    public MyContainer(boolean vertical, int minWidthPercent, int minHeightPercent,
                       int maxWidthPercent, int maxHeightPercent) {
        if (vertical) setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        else setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width, screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        setSize(new Dimension(screenWidth / 100 * minWidthPercent,
                screenHeight / 100 * minHeightPercent));
        setMinimumSize(new Dimension(screenWidth / 100 * minWidthPercent,
                screenHeight / 100 * minHeightPercent));
        setMaximumSize(new Dimension(screenWidth / 100 * maxWidthPercent,
                screenHeight / 100 * maxHeightPercent));
        //TODO parent layout manager overrides this sizes....
    }
}
