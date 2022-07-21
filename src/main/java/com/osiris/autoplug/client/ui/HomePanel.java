/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.console.AutoPlugConsole;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.logger.MessageFormatter;
import com.osiris.betterlayout.BLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class HomePanel extends BLayout {

    public JLabel labelConsole = new JLabel("Console");
    public BLayout txtConsole = new BLayout();
    public JTextField txtSendCommand = new JTextField();

    public HomePanel(Container parent) {
        super(parent);
        this.addV(txtConsole).left();
        this.addV(new JScrollPane(txtConsole, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)).left();
        this.addV(txtSendCommand).left();

        AL.actionsOnMessageEvent.add(msg -> {
            SwingUtilities.invokeLater(() -> {
                txtConsole.addV(new JLabel(MessageFormatter.formatForFile(msg))).left();
                //txtConsole.setText(txtConsole.getText() +  + "\n");
            });
        });
        txtSendCommand.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == KeyEvent.VK_ENTER)
                    AutoPlugConsole.executeCommand(txtSendCommand.getText());
            }

            @Override
            public void keyPressed(KeyEvent e) {

            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
    }
}
