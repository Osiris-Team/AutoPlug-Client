/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.configs.TasksConfig;
import com.osiris.autoplug.client.network.online.MainConnection;
import com.osiris.autoplug.client.network.online.connections.PluginsUpdaterConnection;
import com.osiris.autoplug.client.tasks.backup.TaskPluginsBackup;
import com.osiris.autoplug.client.tasks.backup.TaskServerFilesBackup;
import com.osiris.autoplug.client.tasks.backup.TaskWorldsBackup;
import com.osiris.autoplug.client.tasks.scheduler.TaskCustomRestarter;
import com.osiris.autoplug.client.tasks.scheduler.TaskDailyRestarter;
import com.osiris.autoplug.client.tasks.updater.TaskPluginsUpdater;
import com.osiris.autoplug.client.tasks.updater.TaskServerUpdater;
import com.osiris.autoplug.client.utils.ConfigUtils;
import com.osiris.autoplug.client.utils.CoolDownReport;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Stuff that is executed before starting the minecraft server
 */
public class BeforeStartupActions {
    private TasksConfig tasksConfig = new TasksConfig();

    public BeforeStartupActions() {

        try{

            // Wait until the main connection stuff is done, so the log isn't a mess
            while (!MainConnection.isDone)
                Thread.sleep(1000);

            // Do cool-down check stuff
            String format = "dd/MM/yyyy HH:mm:ss";
            CoolDownReport coolDownReport = new ConfigUtils().checkIfOutOfCoolDown(new SimpleDateFormat(format)); // Get the report first before saving any new values
            SystemConfig systemConfig = new SystemConfig();
            systemConfig.timestamp_last_tasks.setValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern(format)));
            systemConfig.save(); // Save the current timestamp to file
            if (!coolDownReport.isOutOfCoolDown()){
                AL.info("Cool-down still active! Time remaining: "+ (((coolDownReport.getMsRemaining()/1000)/60D))+" minutes.");
                AL.info("Note: You can change the cool-down in the general config.");
                return;
            }


            BetterThreadManager man = new BetterThreadManager();
            BetterThreadDisplayer dis = new BetterThreadDisplayer(
                    man, "[AutoPlug]", "[TASK]", null, false,
                    false, tasksConfig.refresh_interval.asInt()); // We have our own way of displaying the warnings, that's why its set to false

            if (tasksConfig.live_tasks.asBoolean())
                dis.start();
            else{
                AL.info("Waiting for before startup tasks to finish...");
                if (new BackupConfig().backup_worlds.asBoolean())
                    AL.info("Remember that the bigger your world, the longer it will take to back it up!");
            }


            // Create processes
            TaskWorldsBackup taskWorldsBackup = new TaskWorldsBackup("WorldsBackup", man);
            TaskPluginsBackup taskPluginsBackup = new TaskPluginsBackup("PluginsBackup", man);
            TaskServerFilesBackup taskServerFilesBackup = new TaskServerFilesBackup("ServerFilesBackup", man);

            TaskDailyRestarter taskDailyRestarter = new TaskDailyRestarter("DailyRestarter", man);
            TaskCustomRestarter taskCustomRestarter = new TaskCustomRestarter("CustomRestarter", man);

            TaskServerUpdater taskServerUpdater = new TaskServerUpdater("ServerUpdater", man);
            TaskPluginsUpdater taskPluginsUpdater = new TaskPluginsUpdater("PluginsUpdater", man, new PluginsUpdaterConnection());


            // Start processes
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
            AL.warn(e);
        }

    }

    private void printSummary(List<BetterThread> all) {
        for (BetterThread t :
                all) {
            for (String s :
                    t.getSummary()) {
                AL.info("[SUMMARY] ["+t.getName()+"] "+s);
            }

        }
    }

    private void printWarnings(List<BetterWarning> allWarnings) {

        for (BetterWarning w :
                allWarnings) {
            String extra = w.getExtraInfo();
            if (extra==null && w.getException()!=null && w.getException().getMessage()!=null)
                extra = w.getException().getMessage();
            AL.warn("["+w.getThread().getName()+"] "+extra , w.getException());
        }
    }

    private void printFinalStatus(List<BetterThread> all) {
        for (BetterThread t :
                all) {
            AL.info("["+t.getName()+"] "+t.getStatus());
        }
    }


}
