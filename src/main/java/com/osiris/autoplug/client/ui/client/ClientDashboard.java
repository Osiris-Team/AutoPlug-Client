package com.osiris.autoplug.client.ui.client;

import com.osiris.autoplug.client.clientapp.NativeClientService;
import javax.swing.*;

/** The actual dashboard also works without a system tray (for example on Linux desktops). */
public final class ClientDashboard extends JTabbedPane {
    private final ClientSettingsPanel settings;
    private final VirtualWorldsPanel worlds;
    public ClientDashboard(NativeClientService service, JComponent serverManager) {
        this(service, serverManager, true);
    }
    public ClientDashboard(NativeClientService service, JComponent serverManager, boolean importVanillaOnOpen) {
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        ServerBrowserPanel browser = new ServerBrowserPanel(service); addTab("Server Browser", browser);
        VirtualWorldsPanel worldPanel = null;
        try { worldPanel = new VirtualWorldsPanel(service); addTab("Singleplayer", worldPanel); }
        catch (java.io.IOException unavailable) { addTab("Singleplayer", new JLabel("Could not load local worlds: " + unavailable.getMessage())); }
        worlds = worldPanel;
        addTab("Profiles", new ProfilesPanel(service));
        if (serverManager != null) addTab("Server Manager", serverManager);
        settings = new ClientSettingsPanel(service); addTab("Settings", settings);
        if (importVanillaOnOpen) browser.importDefaultIfPresent();
    }
    public void release() { settings.release(); if (worlds != null) worlds.release(); }
}
