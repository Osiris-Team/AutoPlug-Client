/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.ui.layout.VL;
import com.osiris.autoplug.client.ui.utils.MouseListener;
import com.osiris.autoplug.core.logger.AL;

import javax.swing.*;
import java.awt.*;

public class SettingsPanel extends VL {
    public SettingsPanel(Container parent) {
        super(parent);
        JButton btnOpenUIDebug = new JButton("Open UI-Debug");
        this.addV(btnOpenUIDebug);
        btnOpenUIDebug.addMouseListener(new MouseListener().onClick(click -> {
            try {
                new UIDebugWindow();
            } catch (Exception e) {
                AL.warn(e);
            }
        }));
    }
}
