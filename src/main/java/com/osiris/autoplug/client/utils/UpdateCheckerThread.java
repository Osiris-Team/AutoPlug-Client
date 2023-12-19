/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.tasks.updater.java.TaskJavaUpdater;
import com.osiris.autoplug.client.tasks.updater.mods.TaskModsUpdater;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.autoplug.client.tasks.updater.self.TaskSelfUpdater;
import com.osiris.autoplug.client.tasks.updater.server.TaskServerUpdater;
import com.osiris.autoplug.client.utils.tasks.MyBThreadManager;
import com.osiris.autoplug.client.utils.tasks.UtilsTasks;
import com.osiris.jlib.logger.AL;

import java.text.SimpleDateFormat;

public class UpdateCheckerThread extends Thread {
    public boolean isRunning = false;

    @Override
    public void run() {
        try {
            isRunning = true;
            while (isRunning) {
                long last = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                        .parse(new SystemConfig().timestamp_last_updater_tasks.asString())
                        .getTime();
                long now1 = System.currentTimeMillis();
                long msSinceLast = now1 - last;
                long msLeft = (new UpdaterConfig().global_recurring_checks_intervall.asInt() * 3600000L) // 1h in ms
                        - msSinceLast;
                if (msLeft > 0) Thread.sleep(msLeft);
                AL.info("Running tasks from recurring update-checker thread.");
                MyBThreadManager man = new UtilsTasks().createManagerAndPrinter();
                TaskSelfUpdater selfUpdater = new TaskSelfUpdater("SelfUpdater", man.manager);
                TaskJavaUpdater taskJavaUpdater = new TaskJavaUpdater("JavaUpdater", man.manager);
                TaskServerUpdater taskServerUpdater = new TaskServerUpdater("ServerUpdater", man.manager);
                TaskPluginsUpdater taskPluginsUpdater = new TaskPluginsUpdater("PluginsUpdater", man.manager);
                TaskModsUpdater taskModsUpdater = new TaskModsUpdater("ModsUpdater", man.manager);
                selfUpdater.start();
                while (!selfUpdater.isFinished()) // Wait until the self updater finishes
                    Thread.sleep(1000);
                taskJavaUpdater.start();
                taskServerUpdater.start();
                taskPluginsUpdater.start();
                taskModsUpdater.start();
                while (!man.manager.isFinished())
                    Thread.sleep(1000);
            }
        } catch (Exception e) {
            AL.warn(e);
        }
    }
}
