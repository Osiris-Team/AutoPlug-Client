package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.betterthread.BThreadManager;

public class DefaultBThreadPrinter implements BThreadPrinter {
    private BThreadManager manager;

    public DefaultBThreadPrinter(BThreadManager manager) {
        this.manager = manager;
    }

    @Override
    public void setupPrinterModules(LoggerConfig loggerConfig, BThreadManager manager) {
        // Setup modules for the default printer
        // Use 'manager' as needed
    }

    @Override
    public void start() {
        // Start the default printer
        // Use 'manager' as needed
    }
}

