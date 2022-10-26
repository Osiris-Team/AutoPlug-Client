/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.Target;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterlayout.BLayout;
import com.osiris.betterlayout.utils.UIDebugWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

public class MainWindow extends JFrame {
    /**
     * There should always be only one instance of {@link MainWindow}.
     */
    public static MainWindow GET = null;
    public TrayIcon trayIcon;

    public MainWindow() throws Exception {
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
        if (SystemTray.isSupported())
            SystemTray.getSystemTray().remove(trayIcon);
        this.dispose();
    }

    public void start() throws Exception {
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            File icon = new File(GD.WORKING_DIR + "/autoplug/system/icon.png");
            if (!icon.exists()) {
                icon.getParentFile().mkdirs();
                icon.createNewFile();
                InputStream link = (getClass().getResourceAsStream("/autoplug-icon.png"));
                Files.copy(link, icon.toPath());
            }
            Image image = Toolkit.getDefaultToolkit().getImage(icon.getAbsolutePath());
            trayIcon = new TrayIcon(image, "AutoPlug", null);
            trayIcon.addMouseListener(new MyMouseListener().onClick(event -> {
                this.setVisible(true);
            }));
            trayIcon.setImageAutoSize(true);
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                AL.warn("Failed to create system tray GUI: Exception occurred.", e);
            }

            TrayIcon finalTrayIcon = trayIcon;
            /* // TODO causes dead lock and the jvm doesnt close:
            // TODO find alternative to remove the icon.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    AL.info("removing");
                    tray.remove(finalTrayIcon);
                    AL.info("aaa");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));

             */

            this.setIconImage(image);
            initUI();
        } else throw new Exception("Failed to create system tray GUI: Not supported on your system.");
    }

    private void initUI() throws Exception {
        // TODO dont stop full autoplug when this window is closed
        this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        this.setName("AutoPlug-Tray");
        this.setTitle("AutoPlug-Tray");
        this.setUndecorated(true);
        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width, screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        int width = (int) (screenWidth / 1.5), height = screenHeight / 2;
        this.setShape(new RoundRectangle2D.Double(0, 0, width, height, 20, 20));
        this.setLocation((screenWidth / 2) - (width / 2), (screenHeight / 2) - (height / 2)); // Position frame in mid of screen
        this.setSize(width, height);
        this.setVisible(false);

        BLayout thisLy = new BLayout(this);
        this.setContentPane(thisLy);
        thisLy.access(() -> {
            // Add stuff to main window
            JLabel titleAutoPlug = new JLabel(), titleTray = new JLabel();
            titleAutoPlug.setText("AutoPlug");
            titleAutoPlug.putClientProperty("FlatLaf.style", "font: 200% $semibold.font");
            thisLy.addH(titleAutoPlug);

            titleTray.setText(" | Tray");
            titleTray.putClientProperty("FlatLaf.style", "font: 200% $light.font");
            thisLy.addH(titleTray).delPadding().paddingTop();

            JTabbedPane tabbedPane = new JTabbedPane();
            thisLy.addV(tabbedPane).height(80).widthFull();
            tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

            // Tab panels/layouts
            try {
                tabbedPane.addTab("Home", new HomePanel(tabbedPane));
                if (Main.TARGET == Target.MINECRAFT_CLIENT) {
                    MinecraftPluginsPanel minecraftMods = new MinecraftPluginsPanel(tabbedPane);
                    tabbedPane.addTab("Mods", minecraftMods);
                } else if (Main.TARGET == Target.MINECRAFT_SERVER) {
                    MinecraftPluginsPanel minecraftPluginsPanel = new MinecraftPluginsPanel(tabbedPane);
                    MinecraftModsPanel minecraftModsPanel = new MinecraftModsPanel(tabbedPane);
                    tabbedPane.addTab("Plugins", minecraftPluginsPanel);
                    tabbedPane.addTab("Mods", minecraftModsPanel);
                } else if (Main.TARGET == Target.MINDUSTRY_SERVER) {
                    MindustryModsPanel mindustryModsPanel = new MindustryModsPanel(tabbedPane);
                    tabbedPane.addTab("Mods", mindustryModsPanel);
                } else if (Main.TARGET == Target.MINDUSTRY_CLIENT) {
                    MindustryModsPanel mindustryModsPanel = new MindustryModsPanel(tabbedPane);
                    tabbedPane.addTab("Mods", mindustryModsPanel);
                } else { // Target.OTHER

                }
                SettingsPanel settingsPanel = new SettingsPanel(tabbedPane);
                tabbedPane.addTab("Settings", settingsPanel);
                //tabbedPane.addChangeListener(e -> selectedTabChanged());
            } catch (Exception e) {
                AL.warn(e);
            }
        });
    }
}
