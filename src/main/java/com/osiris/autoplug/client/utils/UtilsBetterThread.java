/*
 * Copyright (c) 2021-2022 Osiris-Team.
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

    public MyBetterThreadManager createManagerWithDisplayer() throws YamlWriterException, NotLoadedException, IOException, IllegalKeyException, DuplicateKeyException, YamlReaderException, IllegalListException, JLineLinkException {
        LoggerConfig loggerConfig = new LoggerConfig();
        TasksConfig tasksConfig = new TasksConfig();
        BetterThreadManager manager = new BetterThreadManager();
        BetterThreadDisplayer displayer = new BetterThreadDisplayer(
                manager,
                "[" + loggerConfig.autoplug_label.asString() + "]",
                "[TASK]",
                null,
                tasksConfig.show_warnings.asBoolean(),
                tasksConfig.show_detailed_warnings.asBoolean(),
                tasksConfig.refresh_interval.asInt());
        if (tasksConfig.live_tasks.asBoolean()) {
            displayer.start();
        } else {
            new CustomDisplayer(manager).start();
        }
        return new MyBetterThreadManager(manager, displayer);
    }

}
