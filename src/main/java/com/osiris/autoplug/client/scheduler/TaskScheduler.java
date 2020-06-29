/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.scheduler;

import com.osiris.autoplug.client.configs.RestarterConfig;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Is responsible for scheduling restarts
 */
public class TaskScheduler {

    public TaskScheduler(){
        AutoPlugLogger.newClassDebug("TaskScheduler");
    }

    private static Scheduler scheduler;

    //Is created in config if enabled
    public void createScheduler(){


        AutoPlugLogger.info("DAILY RESTARTER |");
        AutoPlugLogger.barrier();

        AutoPlugLogger.info("Initialising scheduler...");

        try {
            // Grab the Scheduler instance from the Factory
            scheduler = StdSchedulerFactory.getDefaultScheduler();

            String jobName;
            String triggerName;
            String min;
            String h;

            for (int i = 0; i < RestarterConfig.restarter_times_hours.size(); i++) {

                //Get values
                jobName = "restartJob"+i;
                triggerName = "restartTrigger"+i;

                min = String.valueOf(RestarterConfig.restarter_times_minutes.get(i));
                h = String.valueOf(RestarterConfig.restarter_times_hours.get(i));

                //Create job
                createJob(jobName, triggerName, min, h);
            }

            //Start it
            scheduler.start();
            AutoPlugLogger.info("Scheduler initialised successfully!");

        } catch (SchedulerException se) {
            se.printStackTrace();
        }

        AutoPlugLogger.barrier();

    }

    //Creates jobs and links them to the scheduler
    private void createJob(String jobName, String triggerName, String min, String h){

        try {
            AutoPlugLogger.debug("createJob", "Creating job with name: "+jobName+" trigger:" + triggerName+" min:" + min+" hour:" + h);

            //Specify scheduler details
            JobDetail job = newJob(RestartTask.class)
                    .withIdentity(jobName, "restartGroup")
                    .build();

            CronTrigger trigger = newTrigger()
                    .withIdentity(triggerName, "restartGroup")
                    .withSchedule(cronSchedule("0 "+min+" "+h+" * * ? *"))
                    .build();

            //Add details to the scheduler
            scheduler.scheduleJob(job, trigger);
            AutoPlugLogger.info("Created daily restart at "+h+":"+min);

        } catch (SchedulerException e) {
            e.printStackTrace();
        }

    }


    public static void safeShutdown(){

        try {

            if (scheduler!=null){
                scheduler.shutdown();
            }


        } catch (SchedulerException se) {
            se.printStackTrace();
        }


    }




}
