/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.minecraft.server.ServerBrowser;
import com.osiris.autoplug.client.minecraft.server.ServerEntry;
import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterlayout.BLayout;
import com.osiris.jlib.logger.AL;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;

public class MinecraftServersPanel extends BLayout {
    public JButton btnRefresh = new JButton("Ping / Refresh");
    public JButton btnAdd = new JButton("Add Server");
    public JButton btnJoin = new JButton("Join Selected");
    public JButton btnDelete = new JButton("Delete");
    public JTable table = new JTable();

    private final ServerBrowser browser;
    private List<ServerEntry> serverList;

    public MinecraftServersPanel(Container parent) throws Exception {
        super(parent);
        File serversDat = new File(GD.WORKING_DIR, "autoplug/profiles/default/game/servers.dat");
        this.browser = new ServerBrowser(serversDat);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.add(btnRefresh);
        btnPanel.add(btnAdd);
        btnPanel.add(btnJoin);
        btnPanel.add(btnDelete);
        this.addV(btnPanel);

        JScrollPane scrollPane = new JScrollPane(table);
        this.addV(scrollPane);

        updateData();

        btnRefresh.addMouseListener(new MyMouseListener().onClick(e -> {
            new Thread(() -> {
                try {
                    browser.pingAllAsync().thenAccept(res -> SwingUtilities.invokeLater(this::populateTable));
                } catch (Exception ex) {
                    AL.warn(ex);
                }
            }).start();
        }));

        btnAdd.addMouseListener(new MyMouseListener().onClick(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter Server Name:", "Add Server", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) return;
            String ip = JOptionPane.showInputDialog(this, "Enter Server Address (host:port):", "Add Server", JOptionPane.PLAIN_MESSAGE);
            if (ip == null || ip.trim().isEmpty()) return;
            try {
                browser.addServer(new ServerEntry(name.trim(), ip.trim()));
                updateData();
            } catch (Exception ex) {
                AL.warn(ex);
                JOptionPane.showMessageDialog(this, "Failed to add server: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }));

        btnJoin.addMouseListener(new MyMouseListener().onClick(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || serverList == null || row >= serverList.size()) {
                JOptionPane.showMessageDialog(this, "Please select a server to join.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            ServerEntry entry = serverList.get(row);
            String player = JOptionPane.showInputDialog(this, "Enter Player Name:", "Player");
            if (player == null || player.trim().isEmpty()) player = "Player";
            final String finalPlayer = player.trim();
            new Thread(() -> {
                try {
                    AL.info("Joining " + entry.ip + " as " + finalPlayer + "...");
                    Process proc = browser.join(entry, finalPlayer, null, null);
                    proc.waitFor();
                } catch (Exception ex) {
                    AL.warn(ex);
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Failed to join: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            }, "join-" + entry.ip).start();
        }));

        btnDelete.addMouseListener(new MyMouseListener().onClick(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || serverList == null || row >= serverList.size()) return;
            ServerEntry entry = serverList.get(row);
            try {
                browser.removeServer(entry.ip);
                updateData();
            } catch (Exception ex) {
                AL.warn(ex);
            }
        }));
    }

    public void updateData() {
        try {
            serverList = browser.readServers();
            populateTable();
        } catch (Exception e) {
            AL.warn("Failed to load servers.dat", e);
        }
    }

    private void populateTable() {
        String[] columnNames = {"Name", "IP", "Version", "Players", "Latency", "MOTD"};
        Object[][] data = new Object[serverList != null ? serverList.size() : 0][6];
        if (serverList != null) {
            for (int i = 0; i < serverList.size(); i++) {
                ServerEntry s = serverList.get(i);
                data[i][0] = s.name;
                data[i][1] = s.ip;
                data[i][2] = s.detectedVersion != null ? s.detectedVersion : "-";
                data[i][3] = s.onlinePlayers >= 0 ? s.onlinePlayers + "/" + s.maxPlayers : "-";
                data[i][4] = s.latencyMs >= 0 ? s.latencyMs + "ms" : "-";
                data[i][5] = s.motd != null ? s.motd : "-";
            }
        }
        table.setModel(new DefaultTableModel(data, columnNames));
    }
}
