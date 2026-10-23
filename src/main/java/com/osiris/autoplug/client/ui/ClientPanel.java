/*
 * Copyright (c) 2022-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.Target;
import com.osiris.autoplug.client.mc.client.NativeClientAccess;
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
            tabs.addTab("Minecraft", new NativeMinecraftPanel(NativeClientAccess.get()));

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

}
