/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui.layout;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.ui.UIDebugWindow;
import com.osiris.autoplug.core.logger.AL;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.RoundRectangle2D;

public class TestWindow extends JFrame {
    /**
     * There should always be only one instance of {@link TestWindow}.
     */
    public static TestWindow GET = null;

    public TestWindow() throws Exception {
        if (GET != null) return;
        GET = this;
        initTheme();
        start();
        this.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F12)
                    new UIDebugWindow(GET);
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        });

    }

    public void initTheme() {
        try {
            GeneralConfig generalConfig = new GeneralConfig();
            if (generalConfig.autoplug_system_tray_theme.asString().equals("light")) {
                if (!FlatLightLaf.setup()) throw new Exception("Returned false!");
            } else if (generalConfig.autoplug_system_tray_theme.asString().equals("dark")) {
                if (!FlatDarkLaf.setup()) throw new Exception("Returned false!");
            } else if (generalConfig.autoplug_system_tray_theme.asString().equals("darcula")) {
                if (!FlatDarculaLaf.setup()) throw new Exception("Returned false!");
            } else {
                AL.warn("The selected theme '" + generalConfig.autoplug_system_tray_theme.asString() + "' is not a valid option! Using default.");
                if (!FlatLightLaf.setup()) throw new Exception("Returned false!");
            }
        } catch (Exception e) {
            AL.warn("Failed to init GUI theme!", e);
        }
    }

    public void close() {
        this.dispose();
    }

    public void start() throws Exception {
        initUI();
    }

    private void initUI() {
        // TODO dont stop full autoplug when this window is closed
        this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        this.setName("AutoPlug-Tray");
        this.setTitle("AutoPlug-Tray");
        this.setUndecorated(true);
        int screenWidth = java.awt.Toolkit.getDefaultToolkit().getScreenSize().width, screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        int width = (int) (screenWidth / 1.5), height = screenHeight / 2;
        this.setShape(new RoundRectangle2D.Double(0, 0, width, height, 20, 20));
        this.setLocation((screenWidth / 2) - (width / 2), (screenHeight / 2) - (height / 2)); // Position frame in mid of screen
        this.setSize(width, height);
        this.setVisible(true);

        this.setLayout(new BoxLayout(this.getContentPane(), BoxLayout.Y_AXIS));
        //this.setContentPane(new ScrollPane()); // Also sets the layout to scroll


        // Add stuff to main window
        MyContainer vlTitle = new MyContainer();
        this.getContentPane().add(vlTitle);
        vlTitle.isDebug = true;
        vlTitle.access(() -> {
            JLabel titleAutoPlug = new JLabel(), titleTray = new JLabel();
            titleAutoPlug.setText("AutoPlug");
            titleAutoPlug.putClientProperty("FlatLaf.style", "font: 200% $semibold.font");
            vlTitle.add(titleAutoPlug);

            titleTray.setText(" | Tray");
            titleTray.putClientProperty("FlatLaf.style", "font: 200% $light.font");
            vlTitle.add(titleTray);

            vlTitle.addV(new JLabel("aughh"));
            vlTitle.addH(new JLabel("LETS GOOO"));
            vlTitle.addH(new JLabel("LETS GOOO"));
            vlTitle.addH(new JLabel("LETS GOOO"));
            vlTitle.addV(new JLabel("aughh"));
            vlTitle.addH(new JLabel("LETS GOOO"));
        });
    }
}
