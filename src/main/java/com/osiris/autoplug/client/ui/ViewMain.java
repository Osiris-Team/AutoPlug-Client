/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.core.logger.AL;

import java.awt.*;
import java.awt.event.ActionListener;

public class ViewMain {
    public ViewMain() {
    }

    public void initGUI() {
        try {
            if (!FlatLightLaf.setup())
                throw new Exception("Returned false!");
        } catch (Exception e) {
            AL.warn("Failed to init GUI light theme!", e);
        }

        TrayIcon trayIcon = null;
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = Toolkit.getDefaultToolkit().getImage("your_image/path_here.gif");
            ActionListener listener = e -> {
                System.out.println("command: " + e.getActionCommand() + " param:" + e.paramString() + " modifiers:" + e.getModifiers());
            };
            PopupMenu popup = new PopupMenu();
            // create menu item for the default action
            MenuItem defaultItem = new MenuItem("Hello there ;)");
            defaultItem.addActionListener(listener);
            popup.add(defaultItem);
            // TODO add other menu items
            trayIcon = new TrayIcon(image, "AutoPlug", popup);
            trayIcon.addActionListener(listener);
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                AL.warn("Failed to create system tray GUI: Exception occurred.", e);
            }
        } else {
            AL.warn("Failed to create system tray GUI: Not supported on your system.");
        }
    }
}
