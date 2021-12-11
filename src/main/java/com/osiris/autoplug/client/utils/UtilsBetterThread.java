package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.*;
import com.osiris.autoplug.client.network.online.ConMain;
import com.osiris.autoplug.client.tasks.CustomDisplayer;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.exceptions.JLineLinkException;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;
import java.text.SimpleDateFormat;

public class UtilsBetterThread {

    public BetterThreadManager createManagerWithDisplayer() throws DYWriterException, NotLoadedException, IOException, IllegalKeyException, DuplicateKeyException, DYReaderException, IllegalListException, JLineLinkException {
        LoggerConfig loggerConfig = new LoggerConfig();
        TasksConfig tasksConfig = new TasksConfig();
        BetterThreadManager manager = new BetterThreadManager();
        if (tasksConfig.live_tasks.asBoolean()){
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
