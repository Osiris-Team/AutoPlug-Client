/*
 * Copyright (c) 2022-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.Target;
import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterlayout.BLayout;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class ServerPanel extends BLayout {

    public ServerPanel(Container parent) throws Exception {
        super(parent);

        JTabbedPane tabs = new JTabbedPane();
        this.addV(tabs).height(80).widthFull();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        if (GD.TARGET == Target.MINECRAFT_CLIENT || GD.TARGET == Target.MINECRAFT_SERVER) {
            MinecraftPluginsPanel minecraftPluginsPanel = new MinecraftPluginsPanel(tabs);
            MinecraftModsPanel minecraftModsPanel = new MinecraftModsPanel(tabs);
            tabs.addTab("Plugins", minecraftPluginsPanel);
            tabs.addTab("Mods", minecraftModsPanel);
        }  else if (GD.TARGET == Target.MINDUSTRY_SERVER ||  GD.TARGET == Target.MINDUSTRY_CLIENT) {
            MindustryModsPanel mindustryModsPanel = new MindustryModsPanel(tabs);
            tabs.addTab("Mods", mindustryModsPanel);
        }  else { // Target.OTHER

        }
        SettingsPanel settingsPanel = new SettingsPanel(tabs);
        tabs.addTab("Settings", settingsPanel);

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
