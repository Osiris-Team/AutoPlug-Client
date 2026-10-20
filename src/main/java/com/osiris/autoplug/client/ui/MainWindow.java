/*
 * Copyright (c) 2022-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterlayout.BLayout;
import com.osiris.betterlayout.utils.UIDebugWindow;
import com.osiris.jlib.logger.AL;
import com.osiris.autoplug.client.clientapp.NativeClientService;
import com.osiris.autoplug.client.ui.client.ClientDashboard;
import com.osiris.autoplug.client.ui.client.ServerManagerPanel;

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
    public static final Object lockUI = new Object();
    public TrayIcon trayIcon;
    private ServerManagerPanel serverManager;
    public static void open(GeneralConfig config) {
        SwingUtilities.invokeLater(() -> {
            try { new MainWindow(config); } catch (Exception exception) { AL.warn("Could not open dashboard", exception); }
        });
    }

    public MainWindow(GeneralConfig generalConfig) throws Exception {
        synchronized (lockUI){
            if (GET != null) {
                GET.close(); // There should only be one, thus stop old
            }
            GET = this;
        }
        initTheme(generalConfig);
        try { start(); } catch (Exception failure) { close(); throw failure; }
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
        initTheme(null);
    }

    public void initTheme(GeneralConfig generalConfig) {
        try {
            if (generalConfig == null) generalConfig = new GeneralConfig();
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
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::close); return; }
        if (serverManager != null) serverManager.release();
        if (getContentPane() instanceof ClientDashboard) ((ClientDashboard)getContentPane()).release();
        if (SystemTray.isSupported() && trayIcon != null)
            SystemTray.getSystemTray().remove(trayIcon);
        this.dispose();
        GET = null;
    }

    public void start() throws Exception {
        if (SwingUtilities.isEventDispatchThread()) initUI();
        else SwingUtilities.invokeAndWait(() -> {
            try { initUI(); } catch (Exception exception) { throw new IllegalStateException(exception); }
        });
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            File icon = new File(GD.WORKING_DIR + "/autoplug/system/icon.png");
            if (!icon.exists()) {
                icon.getParentFile().mkdirs();
                try (InputStream link = getClass().getResourceAsStream("/autoplug-icon.png")) {
                    if (link == null) throw new java.io.IOException("Missing application icon");
                    Files.copy(link, icon.toPath());
                }
            }
            Image image = Toolkit.getDefaultToolkit().getImage(icon.getAbsolutePath());
            trayIcon = new TrayIcon(image, "AutoPlug", null);
            trayIcon.addMouseListener(new MyMouseListener().onClick(event -> {
                openDashboard();
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
        } else openDashboard();
    }

    public void openDashboard() {
        SwingUtilities.invokeLater(() -> { setVisible(true); setExtendedState(getExtendedState() & ~Frame.ICONIFIED); toFront(); requestFocus(); });
    }

    private void initUI() throws Exception {
        // TODO dont stop full autoplug when this window is closed
        this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        this.setName("AutoPlug-Client-Dashboard");
        this.setTitle("AutoPlug • Client & Server Dashboard");
        this.setUndecorated(false);
        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width, screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        int width = Math.min(1180, screenWidth - 80), height = Math.min(780, screenHeight - 80);
        this.setLocation((screenWidth / 2) - (width / 2), (screenHeight / 2) - (height / 2)); // Position frame in mid of screen
        this.setSize(width, height);
        this.setMinimumSize(new Dimension(800, 540));
        this.setVisible(false);
        serverManager = new ServerManagerPanel();
        this.setContentPane(new ClientDashboard(NativeClientService.getDefault(), serverManager));
    }
}
