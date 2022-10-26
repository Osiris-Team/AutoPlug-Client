/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterlayout.BLayout;
import com.osiris.betterlayout.utils.UIDebugWindow;

import javax.swing.*;
import java.awt.*;

public class SettingsPanel extends BLayout {
    public SettingsPanel(Container parent) throws Exception {
        super(parent);
        JButton btnOpenUIDebug = new JButton("Open UI-Debug");
        this.addV(btnOpenUIDebug);
        btnOpenUIDebug.addMouseListener(new MyMouseListener().onClick(click -> {
            try {
                new UIDebugWindow(MainWindow.GET);
            } catch (Exception e) {
                AL.warn(e);
            }
        }));
    }
}
