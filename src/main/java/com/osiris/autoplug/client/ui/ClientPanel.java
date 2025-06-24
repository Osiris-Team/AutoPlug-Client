/*
 * Copyright (c) 2022-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.Target;
import com.osiris.autoplug.client.console.Commands;
import com.osiris.autoplug.client.ui.utils.HintTextField;
import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterlayout.BLayout;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.MessageFormatter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

public class ClientPanel extends BLayout {

    public ClientPanel(Container parent) throws Exception {
        super(parent);

        JTabbedPane tabs = new JTabbedPane();
        this.addV(tabs).height(80).widthFull();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        if (GD.TARGET == Target.MINECRAFT_CLIENT || GD.TARGET == Target.MINECRAFT_SERVER) {
            SettingsPanel servers = new SettingsPanel(tabs);
            tabs.addTab("Servers", servers); // TODO list of favorite servers with play/join button which auto-selects
            // (TODO) and launches the correct minecraft client version

            SettingsPanel profiles = new SettingsPanel(tabs);
            tabs.addTab("Profiles", profiles); // TODO game profile containing settings and mods
            // (TODO) is not tied to a minecraft version, meaning internally
            // (TODO) mods/settings will be down/upgraded to match the launched version

            MinecraftModsPanel minecraftMods = new MinecraftModsPanel(tabs);
            tabs.addTab("Mods", minecraftMods);

        }  else if (GD.TARGET == Target.MINDUSTRY_CLIENT || GD.TARGET == Target.MINDUSTRY_SERVER) {
            MindustryModsPanel mindustryModsPanel = new MindustryModsPanel(tabs);
            tabs.addTab("Mods", mindustryModsPanel);
        } else { // Target.OTHER

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
