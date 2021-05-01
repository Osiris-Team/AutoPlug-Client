/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.scheduler;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Responsible for scheduling restarts.
 */
public class JobScheduler {

    private static Scheduler scheduler;
    private static Scheduler c_scheduler; //WORK IN PROGRESS!

    /**
     * Get the one and only scheduler, which is used for scheduling tasks (who would have though!).
     * All the tasks attached to this scheduler are created by its Process.
     * If there isn't one created already a new scheduler will be created.
     * @return Scheduler.
     * @throws Exception
     */
    public static Scheduler getScheduler() throws Exception{
        if (scheduler==null){
            scheduler = StdSchedulerFactory.getDefaultScheduler();
        }
        return scheduler;
    }

    /**
     * WORK IN PROGRESS!
     * @return
     * @throws Exception
     */
    public static Scheduler getCustomScheduler() throws Exception{
        // TODO
        if (c_scheduler==null){
            c_scheduler = StdSchedulerFactory.getDefaultScheduler();
        }
        return c_scheduler;
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
