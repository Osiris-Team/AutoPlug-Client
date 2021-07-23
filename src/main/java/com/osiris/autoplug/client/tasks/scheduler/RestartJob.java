/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.scheduler;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.RestarterConfig;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.exceptions.*;
import org.jetbrains.annotations.NotNull;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RestartJob implements Job {

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // Before restarting execute commands
            RestarterConfig config = new RestarterConfig();
            List<DYModule> modules = config.restarter_commands.getChildModules();

            // Sort the stuff
            List<Integer> keysAsIntsList = new ArrayList<>();
            for (DYModule m :
                    modules) {
                keysAsIntsList.add(Integer.parseInt(m.getLastKey()));
            }

            // Normally sorts from lowest, to highest value.
            // But we want it the other way:
            Integer[] keysAsIntsArray = keysAsIntsList.toArray(new Integer[0]);
            Arrays.sort(keysAsIntsArray, Collections.reverseOrder());

            AL.info("Executing scheduled restart in " + keysAsIntsArray[0] + "sec(s)...");
            for (int i = keysAsIntsArray[0]; i >= 0; i--) { // The first int, has the highest value, bc of the sorting
                for (DYModule m :
                        modules) {
                    if (Integer.parseInt(m.getLastKey()) == i) {
                        AL.debug(this.getClass(), "Executing command(s): " + m.getValues().toString());
                        for (String command : m.asStringList()) {
                            try {
                                if (command == null)
                                    AL.debug(this.getClass(), "Command for second '" + i + "' is null.");
                                else
                                    Server.submitCommand(command);
                            } catch (Exception e) {
                                AL.warn(e, "Error executing '" + command + "' command!");
                            }
                        }
                    }
                }
                Thread.sleep(1000);
            }

            //Restart the server
            Server.restart();

        } catch (@NotNull InterruptedException | IOException | DuplicateKeyException | DYReaderException
                | IllegalListException | DYWriterException | NotLoadedException | IllegalKeyException e) {
            AL.warn("Error while executing restart!", e);
        }

    }

}
