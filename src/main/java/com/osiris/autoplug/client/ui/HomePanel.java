/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.console.AutoPlugConsole;
import com.osiris.autoplug.client.ui.utils.HintTextField;
import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.betterlayout.BLayout;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.MessageFormatter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

public class HomePanel extends BLayout {

    public JLabel labelConsole = new JLabel("Console");
    public BLayout txtConsole;
    public HintTextField txtSendCommand = new HintTextField("Send command...");

    public HomePanel(Container parent) {
        super(parent);

        //TODO this.addV(getBtnMinecraftLaunch());

        txtConsole = new BLayout(this, 100, 80);
        txtConsole.defaultCompStyles.delPadding();
        txtConsole.makeScrollable();
        //TODO txtConsole.getScrollPane().getVerticalScrollBar().setUnitIncrement(16);

        this.addV(txtSendCommand);

        AL.actionsOnMessageEvent.add(msg -> {
            txtConsole.access(() -> {
                for (JLabel jLabel : toLabel(MessageFormatter.formatForFile(msg))) {
                    txtConsole.addV(jLabel);
                }
                //txtConsole.setText(txtConsole.getText() +  + "\n");
            });
            txtConsole.scrollToEndV();
        });
        txtSendCommand.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                    txtConsole.access(() -> {
                        txtConsole.addV(new JLabel(txtSendCommand.getText()));
                    });
                    txtConsole.scrollToEndV();
                    AL.info("Received System-Tray command: '" + txtSendCommand.getText() + "'");
                    AutoPlugConsole.executeCommand(txtSendCommand.getText());
                    txtSendCommand.setText("");
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {

            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
    }

    private JButton getBtnMinecraftLaunch() {
        JButton btn = new JButton("Launch Minecraft");
        btn.addMouseListener(new MyMouseListener().onClick(event -> {

        }));
        return btn;
    }

    private java.util.List<JLabel> toLabel(String ansi) {
        // TODO convert ansi colors to awt
        String[] lines = ansi.split("\n");
        java.util.List<JLabel> list = new ArrayList<>();
        for (String line : lines) {
            list.add(new JLabel(line));
        }
        return list;
    }
}
