/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class MainWindow extends JFrame {
    /**
     * There should always be only one instance of {@link MainWindow}.
     */
    public static MainWindow GET = null;

    public MainWindow() throws IOException {
        if (GET != null) return;
        GET = this;
        try {
            if (!FlatLightLaf.setup())
                throw new Exception("Returned false!");
        } catch (Exception e) {
            AL.warn("Failed to init GUI light theme!", e);
        }

        TrayIcon trayIcon = null;
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            File icon = new File(GD.WORKING_DIR + "/autoplug/system/icon.png");
            if (!icon.exists()) {
                icon.getParentFile().mkdirs();
                icon.createNewFile();
                InputStream link = (getClass().getResourceAsStream("/autoplug-icon.png"));
                Files.copy(link, icon.toPath());
            }
            ActionListener listener = e -> {
                System.out.println("command: " + e.getActionCommand() + " param:" + e.paramString() + " modifiers:" + e.getModifiers());
            };
            Image image = Toolkit.getDefaultToolkit().getImage(icon.getAbsolutePath());
            trayIcon = new TrayIcon(image, "AutoPlug", null);
            trayIcon.addMouseListener(new MouseListener().onClick(event -> {
                this.setVisible(true);
            }));
            trayIcon.addActionListener(listener);
            trayIcon.setImageAutoSize(true);
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                AL.warn("Failed to create system tray GUI: Exception occurred.", e);
            }

            // Customize main window
            this.addMouseListener(new MouseListener().onExit(event -> {
                this.setVisible(false);
            }));
            this.setIconImage(image);
            this.setUndecorated(true);
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width, screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
            int width = (int) (screenWidth / 1.5), height = screenHeight / 2;
            this.setShape(new RoundRectangle2D.Double(0, 0, width, height, 20, 20));
            this.setLocation((screenWidth / 2) - (width / 2), (screenHeight / 2) - (height / 2)); // Position frame in mid of screen
            this.setSize(width, height);
            this.setVisible(false);
            this.setLayout(new FlowLayout());

            // Add stuff to main window
            JLabel titleAutoPlug = new JLabel(), titleTray = new JLabel();
            titleAutoPlug.setText("AutoPlug");
            titleAutoPlug.putClientProperty("FlatLaf.style", "font: 200% $semibold.font");
            getContentPane().add(titleAutoPlug);

            titleTray.setText("| Tray");
            titleTray.putClientProperty("FlatLaf.style", "font: 200% $light.font");
            getContentPane().add(titleTray);
        } else {
            AL.warn("Failed to create system tray GUI: Not supported on your system.");
        }
    }
}
