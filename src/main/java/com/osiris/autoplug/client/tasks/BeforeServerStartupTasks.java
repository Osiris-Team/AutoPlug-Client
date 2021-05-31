/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.configs.TasksConfig;
import com.osiris.autoplug.client.network.online.MainConnection;
import com.osiris.autoplug.client.network.online.connections.PluginsUpdaterConnection;
import com.osiris.autoplug.client.tasks.backup.TaskPluginsBackup;
import com.osiris.autoplug.client.tasks.backup.TaskServerFilesBackup;
import com.osiris.autoplug.client.tasks.backup.TaskWorldsBackup;
import com.osiris.autoplug.client.tasks.scheduler.TaskCustomRestarter;
import com.osiris.autoplug.client.tasks.scheduler.TaskDailyRestarter;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.autoplug.client.tasks.updater.self.TaskSelfUpdater;
import com.osiris.autoplug.client.tasks.updater.server.TaskServerUpdater;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Stuff that is executed before starting the minecraft server.
 */
public class BeforeServerStartupTasks {
    private TasksConfig tasksConfig;

    public BeforeServerStartupTasks() {
        BetterThreadManager man = null;
        BetterThreadDisplayer dis; // We have our own way of displaying the warnings, that's why its set to false
        try {
            tasksConfig = new TasksConfig();

            // Wait until the main connection stuff is done, so the log isn't a mess
            while (!MainConnection.isDone)
                Thread.sleep(1000);

            man = new BetterThreadManager();
            dis = new BetterThreadDisplayer(
                    man, "[AutoPlug]", "[TASK]", null, false,
                    false, tasksConfig.refresh_interval.asInt()); // We have our own way of displaying the warnings, that's why its set to false

            if (tasksConfig.live_tasks.asBoolean())
                dis.start();
            else {
                AL.info("Waiting for before startup tasks to finish...");
                if (new BackupConfig().backup_worlds.asBoolean())
                    AL.info("Remember that the bigger your world, the longer it will take to back it up!");
            }


            // Create processes
            TaskSelfUpdater selfUpdater = new TaskSelfUpdater("Self-Updater", man);

            TaskServerFilesBackup taskServerFilesBackup = new TaskServerFilesBackup("ServerFilesBackup", man);
            TaskWorldsBackup taskWorldsBackup = new TaskWorldsBackup("WorldsBackup", man);
            TaskPluginsBackup taskPluginsBackup = new TaskPluginsBackup("PluginsBackup", man);

            TaskDailyRestarter taskDailyRestarter = new TaskDailyRestarter("DailyRestarter", man);
            TaskCustomRestarter taskCustomRestarter = new TaskCustomRestarter("CustomRestarter", man);

            TaskServerUpdater taskServerUpdater = new TaskServerUpdater("ServerUpdater", man);
            TaskPluginsUpdater taskPluginsUpdater = new TaskPluginsUpdater("PluginsUpdater", man, new PluginsUpdaterConnection());


            // Start processes
            selfUpdater.start();

            while (!selfUpdater.isFinished()) // Wait until the self updater finishes
                Thread.sleep(1000);

            taskWorldsBackup.start();
            taskPluginsBackup.start();
            taskServerFilesBackup.start();

            // Wait till backup is done
            while (!taskWorldsBackup.isFinished() || !taskPluginsBackup.isFinished() || !taskServerFilesBackup.isFinished())
                Thread.sleep(1000);

            taskDailyRestarter.start();
            taskCustomRestarter.start();

            taskServerUpdater.start();
            taskPluginsUpdater.start();

            // Wait until the rest is finished
            while (!man.isFinished())
                Thread.sleep(1000);

            if (!tasksConfig.live_tasks.asBoolean())
                printFinalStatus(man.getAll());

            printSummary(man.getAll());
            printWarnings(man.getAllWarnings());

        } catch (Exception e) {
            AL.warn("A severe error occurred while executing the before server startup tasks! Interrupting tasks...");
            try {
                if (man != null)
                    for (BetterThread t :
                            man.getAll()) {
                        if (t != null && !t.isInterrupted())
                            t.interrupt();
                    }
            } catch (Exception exception) {
                AL.warn(exception);
            }
            AL.warn("Severe error while executing before server startup tasks!", e);
        }

    }

    private void printSummary(@NotNull List<BetterThread> all) {
        for (BetterThread t :
                all) {
            for (String s :
                    t.getSummary()) {
                AL.info(s);
            }

        }
    }

    private void printWarnings(@NotNull List<BetterWarning> allWarnings) {

        for (BetterWarning w :
                allWarnings) {
            AL.warn(w.getException(), w.getExtraInfo());
        }
    }

    private void printFinalStatus(@NotNull List<BetterThread> all) {
        for (BetterThread t :
                all) {
            AL.info("[" + t.getName() + "] " + t.getStatus());
        }
    }


}
