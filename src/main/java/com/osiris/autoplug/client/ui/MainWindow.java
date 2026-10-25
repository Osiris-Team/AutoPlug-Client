/* Copyright (c) 2022-2026 Osiris-Team. Licensed under the MIT License. */
package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.browser.ServerBrowserService;
import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterlayout.utils.UIDebugWindow;
import com.osiris.jlib.logger.AL;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Objects;

/** One dashboard shared by the tray and the desktop window. Closing the window hides it. */
public class MainWindow extends JFrame {
    public static volatile MainWindow GET;
    public static final Object lockUI = new Object();
    private static volatile LauncherActions launcherActions = new LauncherActions() {};
    private static volatile ServerBrowserService browserService = ServerBrowserService.defaults();
    public TrayIcon trayIcon;
    private DashboardPanel dashboard;

    public static void setLauncherActions(LauncherActions actions) {
        launcherActions = Objects.requireNonNull(actions);
        SwingUtilities.invokeLater(() -> { if (GET != null) GET.installDashboard(); });
    }
    public static void setBrowserService(ServerBrowserService browser) { browserService = Objects.requireNonNull(browser); }

    public MainWindow(GeneralConfig generalConfig) throws Exception {
        Runnable initialize = () -> {
            synchronized (lockUI) { if (GET != null) GET.close(); GET = this; }
            initTheme(generalConfig);
            try { start(); } catch (Exception e) { AL.warn("Could not initialize the dashboard", e); }
        };
        if (SwingUtilities.isEventDispatchThread()) initialize.run(); else SwingUtilities.invokeAndWait(initialize);
    }

    public void initTheme() { initTheme(null); }
    public void initTheme(GeneralConfig generalConfig) {
        if (!SwingUtilities.isEventDispatchThread()) {
            final GeneralConfig configuration = generalConfig;
            SwingUtilities.invokeLater(() -> initTheme(configuration)); return;
        }
        try {
            if (generalConfig == null) generalConfig = new GeneralConfig();
            String theme = generalConfig.autoplug_system_tray_theme.asString();
            if ("dark".equals(theme)) FlatDarkLaf.setup();
            else if ("darcula".equals(theme)) FlatDarculaLaf.setup();
            else FlatLightLaf.setup();
            DashboardTheme.installDefaults();
            if (isDisplayable()) SwingUtilities.invokeLater(() -> SwingUtilities.updateComponentTreeUI(this));
        } catch (Exception e) { AL.warn("Failed to initialize the dashboard theme", e); }
    }

    public void close() {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::close); return; }
        if (dashboard != null) dashboard.close();
        if (trayIcon != null && SystemTray.isSupported()) SystemTray.getSystemTray().remove(trayIcon);
        trayIcon = null; dispose(); if (GET == this) GET = null;
    }

    public void start() throws Exception {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE); setName("AutoPlug-Dashboard"); setTitle("AutoPlug — Minecraft Dashboard");
        setMinimumSize(new Dimension(950, 620));
        installDashboard(); pack(); setLocationRelativeTo(null);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), "ui-debug");
        getRootPane().getActionMap().put("ui-debug", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { new UIDebugWindow(MainWindow.this); } });
        Image image = loadIcon(); if (image != null) setIconImage(image);
        if (SystemTray.isSupported()) {
            if (image == null) image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
            PopupMenu menu = new PopupMenu(); MenuItem open = new MenuItem("Open AutoPlug Dashboard");
            open.addActionListener(e -> openDashboard()); menu.add(open);
            trayIcon = new TrayIcon(image, "AutoPlug", menu); trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> openDashboard());
            trayIcon.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) openDashboard(); } });
            try { SystemTray.getSystemTray().add(trayIcon); }
            catch (AWTException e) { trayIcon = null; AL.warn("Tray unavailable; opening the desktop dashboard", e); }
        }
        setVisible(trayIcon == null);
    }

    private void installDashboard() {
        if (dashboard != null) dashboard.close();
        dashboard = new DashboardPanel(launcherActions, browserService); setContentPane(dashboard); revalidate(); repaint();
    }
    private void openDashboard() { SwingUtilities.invokeLater(() -> { setVisible(true); setState(Frame.NORMAL); toFront(); requestFocus(); }); }
    private Image loadIcon() {
        File custom = new File(GD.WORKING_DIR, "autoplug/system/icon.png");
        try { if (custom.isFile() && custom.length() > 0) { Image image = ImageIO.read(custom); if (image != null) return image; } }
        catch (Exception e) { AL.warn("Could not read the custom tray icon", e); }
        try (InputStream stream = getClass().getResourceAsStream("/autoplug-icon.png")) { return stream == null ? null : ImageIO.read(stream); }
        catch (Exception e) { AL.warn("Could not read the dashboard icon", e); return null; }
    }
}
