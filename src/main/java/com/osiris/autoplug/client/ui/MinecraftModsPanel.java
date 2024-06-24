/*
 * Copyright (c) 2022-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.ui.utils.MyMouseListener;
import com.osiris.betterlayout.BLayout;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import com.osiris.dyml.exceptions.YamlReaderException;
import com.osiris.jlib.logger.AL;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class MinecraftModsPanel extends BLayout {
    public JButton btnRefreshData = new JButton("Refresh");
    public JTable table = new JTable();

    public MinecraftModsPanel(Container parent) throws Exception {
        super(parent);
        this.addV(btnRefreshData);
        this.addV(table);
        updateData();
        btnRefreshData.addMouseListener(new MyMouseListener().onClick(click -> {
            try {
                updateData();
            } catch (Exception e) {
                AL.warn(e);
            }
        }));
    }

    public void updateData() throws YamlReaderException, IOException, DuplicateKeyException, IllegalListException {
        // Fetch data
        int columnsCount = 4;
        String[] columnNames = {"Name", "Version", "Latest", "Author"};
        Object[][] data = null;
        File yamlFile = new File(System.getProperty("user.dir") + "/autoplug/mods.yml");
        if (!yamlFile.exists()) {
            data = new Object[][]{
                    {"-", "-", "-", "-"}
            };
        } else {
            Yaml modsConfig = new Yaml(yamlFile);
            modsConfig.load();
            List<YamlSection> sections = Objects.requireNonNull(modsConfig.get("mods")).getChildSections();
            sections.remove(0); // To skip first child, since that's no mod data
            data = new Object[sections.size()][columnsCount];
            for (int i = 0; i < sections.size(); i++) {
                YamlSection sec = sections.get(i);
                String modName = sec.getLastKey();
                data[i][0] = modName;
                data[i][1] = modsConfig.get("mods", modName, "version").asString();
                data[i][2] = modsConfig.get("mods", modName, "latest-version").asString();
                data[i][3] = modsConfig.get("mods", modName, "author").asString();
            }
        }

        // Update UI
        this.remove(table);
        table = new JTable(data, columnNames);
        this.addV(table);
        table.setBackground(new Color(0, true)); // transparent
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    }
}
