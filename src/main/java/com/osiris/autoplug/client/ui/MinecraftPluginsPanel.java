/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class MinecraftPluginsPanel extends JPanel {
    public JButton btnRefreshData = new JButton("Refresh");
    public JTable table = new JTable();

    public MinecraftPluginsPanel() {
        this.add(btnRefreshData);
        this.add(table);
        updateData();
        btnRefreshData.addMouseListener(new MouseListener().onClick(click -> {
            updateData();
        }));
    }

    public void updateData() {
        // Fetch data
        int columnsCount = 4;
        String[] columnNames = {"Name", "Version", "Latest", "Author"};
        Object[][] data = null;
        File yamlFile = new File(System.getProperty("user.dir") + "/autoplug/plugins.yml");
        if (!yamlFile.exists()) {
            data = new Object[][]{
                    {"-", "-", "-", "-"}
            };
        } else {
            Yaml yamlMods = new Yaml(yamlFile);
            List<YamlSection> sections = yamlMods.get("plugins").getChildModules();
            sections.remove(0); // To skip first child, since that's no plugin data
            data = new Object[sections.size()][columnsCount];
            for (int i = 0; i < sections.size(); i++) {
                YamlSection sec = sections.get(i);
                String modName = sec.getLastKey();
                data[i][0] = modName;
                data[i][1] = yamlMods.get("plugins", modName, "version").asString();
                data[i][2] = yamlMods.get("plugins", modName, "latest-version").asString();
                data[i][3] = yamlMods.get("plugins", modName, "author").asString();
            }
        }

        // Update UI
        this.remove(table);
        table = new JTable(data, columnNames);
        this.add(table);
    }
}
