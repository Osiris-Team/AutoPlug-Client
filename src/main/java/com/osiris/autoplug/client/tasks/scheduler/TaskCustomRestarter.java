/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.scheduler;

import com.osiris.autoplug.client.configs.RestarterConfig;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import org.jetbrains.annotations.NotNull;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Collections;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class TaskCustomRestarter extends BetterThread {

    private static Scheduler scheduler;

    public TaskCustomRestarter(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        createAllJobs();
    }

    //Is created in config if enabled
    private void createAllJobs() throws Exception {

        RestarterConfig config = new RestarterConfig();

        if (config.c_restarter_enabled.asBoolean()) {

            scheduler = StdSchedulerFactory.getDefaultScheduler();

            if (scheduler.isStarted()) {
                setStatus("Scheduler already running. Put into standby.");
                scheduler.standby();
            }
            setNow(50);
            Thread.sleep(1000);

            String cron = config.c_restarter_cron.asString();
            createOrReplaceJob("customRestartJob", "customRestartTrigger", cron);
            setStatus("Restart at cron: " + cron);

            scheduler.start(); // Create all jobs before starting the scheduler
            finish(true);
        } else {
            skip();
        }

    }

    //Creates jobs and links them to the scheduler
    private void createOrReplaceJob(String jobName, String triggerName, @NotNull String cron) throws Exception {

        AL.debug(this.getClass(), "Creating job with name: " + jobName + " trigger:" + triggerName + " cron:" + cron);

        //Specify scheduler details
        JobDetail job = newJob(CustomRestartJob.class)
                .withIdentity(jobName, "restartGroup")
                .build();

        CronTrigger trigger = newTrigger()
                .withIdentity(triggerName, "restartGroup")
                .withSchedule(cronSchedule(cron))
                .build();

        //Add details to the scheduler
        scheduler.scheduleJob(job, Collections.singleton(trigger), true);
    }

}
