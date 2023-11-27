package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.betterthread.BThreadManager;

public interface BThreadPrinter {
    void setupPrinterModules(LoggerConfig loggerConfig, BThreadManager manager);
    void start();
}
