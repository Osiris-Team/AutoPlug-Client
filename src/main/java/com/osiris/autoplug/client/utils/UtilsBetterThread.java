/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.autoplug.client.configs.TasksConfig;
import com.osiris.autoplug.client.tasks.CustomDisplayer;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.exceptions.JLineLinkException;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class UtilsBetterThread {

    public BetterThreadManager createManagerWithDisplayer() throws DYWriterException, NotLoadedException, IOException, IllegalKeyException, DuplicateKeyException, DYReaderException, IllegalListException, JLineLinkException {
        LoggerConfig loggerConfig = new LoggerConfig();
        TasksConfig tasksConfig = new TasksConfig();
        BetterThreadManager manager = new BetterThreadManager();
        if (tasksConfig.live_tasks.asBoolean()) {
            new BetterThreadDisplayer(
                    manager,
                    "[" + loggerConfig.autoplug_label.asString() + "]",
                    "[TASK]",
                    null,
                    tasksConfig.show_warnings.asBoolean(),
                    tasksConfig.show_detailed_warnings.asBoolean(),
                    tasksConfig.refresh_interval.asInt()).start();
        } else {
            new CustomDisplayer(manager).start();
        }
        return manager;
    }

}
