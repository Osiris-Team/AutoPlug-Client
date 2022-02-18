/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.configs.TasksConfig;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.tasks.backup.TaskBackup;
import com.osiris.autoplug.client.tasks.scheduler.TaskCustomRestarter;
import com.osiris.autoplug.client.tasks.scheduler.TaskDailyRestarter;
import com.osiris.autoplug.client.tasks.updater.java.TaskJavaUpdater;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import com.osiris.autoplug.client.tasks.updater.self.TaskSelfUpdater;
import com.osiris.autoplug.client.tasks.updater.server.TaskServerUpdater;
import com.osiris.autoplug.client.utils.CoolDownReport;
import com.osiris.autoplug.client.utils.UtilsConfig;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.autoplug.core.logger.LogFileWriter;
import com.osiris.autoplug.core.logger.Message;
import com.osiris.autoplug.core.logger.MessageFormatter;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Stuff that is executed before starting the minecraft server.
 */
public class BeforeServerStartupTasks {
    private LoggerConfig loggerConfig;
    private TasksConfig tasksConfig;

    public BeforeServerStartupTasks() {
        BetterThreadManager manager = null;
        BetterThreadDisplayer displayer = null; // We have our own way of displaying the warnings, that's why its set to false
        try {
            loggerConfig = new LoggerConfig();
            tasksConfig = new TasksConfig();

            // Wait until the main connection stuff is done, so the log isn't a mess
            while (!ConMain.isDone)
                Thread.sleep(1000);

            // Do cool-down check stuff
            boolean isUpdaterCoolDownActive = false;
            UpdaterConfig config = new UpdaterConfig();
            SystemConfig systemConfig = new SystemConfig();
            String format = "dd/MM/yyyy HH:mm:ss";
            CoolDownReport coolDownReport = new UtilsConfig().getCoolDown(
                    config.global_cool_down.asInt(),
                    new SimpleDateFormat(format),
                    systemConfig.timestamp_last_updater_tasks.asString()); // Get the report first before saving any new values
            if (coolDownReport.isInCoolDown()) {
                AL.info("Skipped updater tasks. Global updater cool-down still active (" + (((coolDownReport.getMsRemaining() / 1000) / 60)) + " minutes remaining).");
                isUpdaterCoolDownActive = true;
            }
            // The systemconfig gets updated with the new timestamp when all updater tasks have finished

            manager = new BetterThreadManager();
            displayer = new BetterThreadDisplayer(
                    manager,
                    "[" + loggerConfig.autoplug_label.asString() + "]",
                    "[TASK]",
                    null,
                    tasksConfig.show_warnings.asBoolean(),
                    tasksConfig.show_detailed_warnings.asBoolean(),
                    tasksConfig.refresh_interval.asInt());

            if (tasksConfig.live_tasks.asBoolean())
                displayer.start();
            else {
                AL.info("Waiting for before startup tasks to finish...");
                new CustomDisplayer(manager).start();
            }

            // Create processes
            TaskSelfUpdater selfUpdater = null;
            if (!isUpdaterCoolDownActive)
                selfUpdater = new TaskSelfUpdater("SelfUpdater", manager);

            TaskBackup taskBackup = new TaskBackup("BackupTask", manager);

            TaskGeneral taskGeneral = new TaskGeneral("GeneralTasks", manager);

            TaskDailyRestarter taskDailyRestarter = new TaskDailyRestarter("DailyRestarter", manager);
            TaskCustomRestarter taskCustomRestarter = new TaskCustomRestarter("CustomRestarter", manager);


            TaskJavaUpdater taskJavaUpdater = null;
            TaskServerUpdater taskServerUpdater = null;
            TaskPluginsUpdater taskPluginsUpdater = null;

            if (!isUpdaterCoolDownActive) {
                taskJavaUpdater = new TaskJavaUpdater("JavaUpdater", manager);
                taskServerUpdater = new TaskServerUpdater("ServerUpdater", manager);
                taskPluginsUpdater = new TaskPluginsUpdater("PluginsUpdater", manager);
            }


            // Start processes
            if (!isUpdaterCoolDownActive) {
                selfUpdater.start();
                while (!selfUpdater.isFinished()) // Wait until the self updater finishes
                    Thread.sleep(1000);
            }

            taskBackup.start();

            // Wait till backup is done
            while (!taskBackup.isFinished())
                Thread.sleep(1000);

            taskGeneral.start();

            taskDailyRestarter.start();
            taskCustomRestarter.start();

            if (!isUpdaterCoolDownActive) {
                taskJavaUpdater.start();
                taskServerUpdater.start();
                taskPluginsUpdater.start();
            }

            // Wait until the rest is finished
            if (tasksConfig.live_tasks.asBoolean()) {
                // In this case we have to wait until the displayer thread finishes, because
                // of some stuff related to the System.out and to avoid duplicate printing of the summary
                while (displayer.isAlive())
                    Thread.sleep(1000);
            } else {
                while (!manager.isFinished())
                    Thread.sleep(1000);
            }

            // Update the updater global cool-down with current time
            systemConfig.lockFile();
            systemConfig.load();
            systemConfig.timestamp_last_updater_tasks.setValues(LocalDateTime.now().format(DateTimeFormatter.ofPattern(format)));
            systemConfig.save();
            systemConfig.unlockFile();// Save the current timestamp to file

            writeFinalStatus(manager.getAll());

            if (!tasksConfig.live_tasks.asBoolean())
                displayer.printAll();


            PrintWriter printWriter = new PrintWriter(LogFileWriter.BUFFERED_WRITER);

            // We want the log file to have all the information.
            boolean showWarnings = displayer.isShowWarnings();
            boolean showDetailedWarnings = displayer.isShowDetailedWarnings();
            displayer.setShowWarnings(true);
            displayer.setShowDetailedWarnings(true);

            displayer.printAndWriteResults(null, printWriter);

            displayer.setShowWarnings(showWarnings);
            displayer.setShowDetailedWarnings(showDetailedWarnings);

            // We don't need to do the below, because its already automatically done when the tasks finish
            //displayer.printAndWriteResults(printStream, null);

        } catch (Exception e) {
            AL.warn("A severe error occurred while executing the before server startup tasks! Interrupting tasks...");
            try {
                if (manager != null)
                    for (BetterThread t :
                            manager.getAll()) {
                        try {
                            if (t != null && !t.isInterrupted() && !t.isFinished())
                                t.setSuccess(false);
                        } catch (Exception exception) {
                            AL.warn(exception);
                        }
                    }
            } catch (Exception exception) {
                AL.warn(exception);
            }
            try {
                if (displayer != null && !displayer.isInterrupted())
                    displayer.interrupt();
            } catch (Exception exception) {
                AL.warn(exception);
            }

            AL.warn("Severe error while executing before server startup tasks!", e);
        }

    }

    private void writeFinalStatus(List<BetterThread> all) {
        for (BetterThread t :
                all) {
            StringBuilder builder = new StringBuilder();
            if (t.isSuccess())
                builder.append("[OK]");
            else if (t.isSkipped())
                builder.append("[SKIPPED]");
            else
                builder.append("[" + t.getWarnings().size() + "x WARN]");

            builder.append("[" + t.getName() + "] ");
            builder.append(t.getStatus());

            LogFileWriter.writeToLog(MessageFormatter.formatForFile(
                    new Message(Message.Type.INFO, builder.toString())));
        }
    }

    private void printFinalStatus(@NotNull List<BetterThread> all) {
        for (BetterThread t :
                all) {
            AL.info("[" + t.getName() + "] " + t.getStatus());
        }
    }


}
