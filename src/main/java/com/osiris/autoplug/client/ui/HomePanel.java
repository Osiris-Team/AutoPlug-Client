/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.ui.layout.VL;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.logger.MessageFormatter;

import javax.swing.*;
import java.awt.*;

public class HomePanel extends VL {

    public JLabel labelConsole = new JLabel("Console");
    public JTextArea txtConsole = new JTextArea();

    public HomePanel(Container parent) {
        super(parent);
        this.addV(labelConsole);
        this.addV(txtConsole);
        AL.actionsOnMessageEvent.add(msg -> {
            SwingUtilities.invokeLater(() -> {
                txtConsole.setText(txtConsole.getText() + MessageFormatter.formatForFile(msg) + "\n");
            });
        });
    }
}
