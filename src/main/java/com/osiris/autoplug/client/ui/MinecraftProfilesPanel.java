/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.minecraft.launcher.MinecraftLauncher;
import com.osiris.autoplug.client.minecraft.profile.LaunchProfile;
import com.osiris.autoplug.client.minecraft.profile.ProfileManager;
import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.betterlayout.BLayout;
import com.osiris.jlib.logger.AL;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class MinecraftProfilesPanel extends BLayout {
    public JButton btnRefresh = new JButton("Refresh");
    public JButton btnCreate = new JButton("Create Profile");
    public JButton btnLaunch = new JButton("Launch Profile");
    public JButton btnDelete = new JButton("Delete");
    public JTable table = new JTable();

    private List<LaunchProfile> profileList;

    public MinecraftProfilesPanel(Container parent) throws Exception {
        super(parent);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.add(btnRefresh);
        btnPanel.add(btnCreate);
        btnPanel.add(btnLaunch);
        btnPanel.add(btnDelete);
        this.addV(btnPanel);

        JScrollPane scrollPane = new JScrollPane(table);
        this.addV(scrollPane);

        updateData();

        btnRefresh.addMouseListener(new MyMouseListener().onClick(e -> updateData()));

        btnCreate.addMouseListener(new MyMouseListener().onClick(e -> {
            String name = JOptionPane.showInputDialog(this, "Profile Name:", "Create Profile", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) return;
            String ver = JOptionPane.showInputDialog(this, "Minecraft Version (e.g. 1.21.1):", "1.21.1");
            if (ver == null || ver.trim().isEmpty()) ver = "1.21.1";
            try {
                ProfileManager.create(name.trim(), ver.trim());
                updateData();
            } catch (Exception ex) {
                AL.warn(ex);
                JOptionPane.showMessageDialog(this, "Failed to create profile: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }));

        btnLaunch.addMouseListener(new MyMouseListener().onClick(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || profileList == null || row >= profileList.size()) {
                JOptionPane.showMessageDialog(this, "Please select a profile to launch.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            LaunchProfile profile = profileList.get(row);
            String player = JOptionPane.showInputDialog(this, "Enter Player Name:", "Player");
            if (player == null || player.trim().isEmpty()) player = "Player";
            final String finalPlayer = player.trim();
            new Thread(() -> {
                try {
                    AL.info("Launching profile " + profile.name + " as " + finalPlayer + "...");
                    Process proc = MinecraftLauncher.launch(profile, finalPlayer, null, null,
                            (done, total) -> AL.info("[Download] " + done + "/" + total));
                    proc.waitFor();
                } catch (Exception ex) {
                    AL.warn(ex);
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Launch failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            }, "launch-" + profile.name).start();
        }));

        btnDelete.addMouseListener(new MyMouseListener().onClick(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || profileList == null || row >= profileList.size()) return;
            LaunchProfile profile = profileList.get(row);
            int confirm = JOptionPane.showConfirmDialog(this, "Delete profile '" + profile.name + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                ProfileManager.delete(profile.name);
                updateData();
            }
        }));
    }

    public void updateData() {
        try {
            profileList = ProfileManager.loadAll();
            String[] columnNames = {"Name", "MC Version", "Loader", "Game Dir", "RAM (MB)", "Server"};
            Object[][] data = new Object[profileList.size()][6];
            for (int i = 0; i < profileList.size(); i++) {
                LaunchProfile p = profileList.get(i);
                data[i][0] = p.name;
                data[i][1] = p.minecraftVersion;
                data[i][2] = p.loaderType;
                data[i][3] = p.gameDir != null ? p.gameDir.getAbsolutePath() : "Default";
                data[i][4] = p.maxMemoryMb > 0 ? p.maxMemoryMb : "Default (2048)";
                data[i][5] = p.serverAddress != null ? p.serverAddress : "-";
            }
            table.setModel(new DefaultTableModel(data, columnNames));
        } catch (Exception e) {
            AL.warn("Failed to load profiles", e);
        }
    }
}
