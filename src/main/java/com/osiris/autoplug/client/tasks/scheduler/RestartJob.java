/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.scheduler;

import com.osiris.autoplug.client.configs.RestarterConfig;
import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.exceptions.DYReaderException;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.IOException;
import java.util.List;

public class RestartJob implements Job {

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            AL.info("Executing scheduled restart in 10sec...");

            //Before restarting execute server
            List<String> commands = new RestarterConfig().restarter_commands.asStringList();

            for (String command : commands) {
                try {
                    Server.submitCommand(command);
                } catch (Exception e) {
                    AL.warn(e, "Error executing '" + command + "' command!");
                }
            }


            Thread.sleep(10000);
            //Restart the server
            Server.restart();
        } catch (InterruptedException | IOException | DuplicateKeyException | DYReaderException | IllegalListException e) {
            AL.warn("Error while executing restart!", e);
        }

    }

}
