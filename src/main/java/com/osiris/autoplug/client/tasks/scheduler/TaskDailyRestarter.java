/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.scheduler;

import com.osiris.autoplug.client.configs.RestarterConfig;
import com.osiris.betterthread.BThread;
import com.osiris.betterthread.BThreadManager;
import com.osiris.betterthread.BWarning;
import com.osiris.jlib.logger.AL;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Collections;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class TaskDailyRestarter extends BThread {

    private static Scheduler scheduler;

    public TaskDailyRestarter(String name, BThreadManager manager) {
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

        if (config.restarter_enabled.asBoolean()) {

            scheduler = StdSchedulerFactory.getDefaultScheduler();

            if (scheduler.isStarted()) {
                setStatus("Scheduler already running. Put into standby.");
                scheduler.standby();
            }
            sleep(1000);

            int size = config.restarter_times_hours.size();
            setMax(size);

            String jobName;
            String triggerName;
            String min;
            String h;

            StringBuilder stringJobs = new StringBuilder("Restarts at: ");

            for (int i = 0; i < size; i++) {

                //Get values
                jobName = "restartJob" + i;
                triggerName = "restartTrigger" + i;

                min = String.valueOf(config.restarter_times_minutes.get(i));
                h = String.valueOf(config.restarter_times_hours.get(i));

                //Create job
                createOrReplaceJob(jobName, triggerName, min, h);
                setStatus("Created job: " + jobName + " at " + h + ":" + min);
                stringJobs.append("[" + h + ":" + min + "]");
                step();
            }

            scheduler.start(); // Create all jobs before starting the scheduler
            finish(stringJobs.toString(), true);
        } else {
            skip();
        }
    }

    //Creates jobs and links them to the scheduler
    private void createOrReplaceJob(String jobName, String triggerName, String min, String h) {

        try {
            AL.debug(this.getClass(), "Creating job with name: " + jobName + " trigger:" + triggerName + " min:" + min + " hour:" + h);

            //Specify scheduler details
            JobDetail job = newJob(RestartJob.class)
                    .withIdentity(jobName, "restartGroup")
                    .build();

            CronTrigger trigger = newTrigger()
                    .withIdentity(triggerName, "restartGroup")
                    .withSchedule(cronSchedule("0 " + min + " " + h + " * * ? *"))
                    .build();

            //Add details to the scheduler
            scheduler.scheduleJob(job, Collections.singleton(trigger), true);

        } catch (SchedulerException e) {
            setSuccess(false);
            getWarnings().add(new BWarning(this, e));
        }

    }

}
