/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.logger.MessageFormatter;

import javax.swing.*;
import java.awt.*;

public class HomePanel extends CoolContainer {

    public JLabel labelConsole = new JLabel("Console");
    public JTextArea txtConsole = new JTextArea();

    public HomePanel(Container parent) {
        super(parent);
        withFlexLayout(1, 3);
        setFlexLayoutYFlag(1, FlexLayout.EXPAND);
        this.add("0,0", labelConsole);
        this.add("0,1,x", txtConsole);
        AL.actionsOnMessageEvent.add(msg -> {
            SwingUtilities.invokeLater(() -> {
                txtConsole.setText(txtConsole.getText() + MessageFormatter.formatForFile(msg) + "\n");
            });
        });
    }
}
