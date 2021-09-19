/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.autoplug.client.configs.GeneralConfig;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;

import java.io.File;
import java.io.FileWriter;

public class TaskGeneral extends BetterThread {
    private GeneralConfig generalConfig;

    public TaskGeneral(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        generalConfig = new GeneralConfig();
        if (generalConfig.server_auto_eula.asBoolean()) {
            setStatus("Searching for eula.txt file...");
            File eula = new File(GD.WORKING_DIR + "/eula.txt");
            if (!eula.exists()) eula.createNewFile();
            try (FileWriter fw = new FileWriter(eula)) {
                fw.write("eula=true\n");
                fw.flush();
            }
            setStatus("File 'eula.txt' created successfully.");
        }
        finish("Finished.");
    }
}
