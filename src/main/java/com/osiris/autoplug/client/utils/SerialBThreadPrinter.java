package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.betterthread.BThreadManager;

public class SerialBThreadPrinter implements BThreadPrinter {
    private BThreadManager manager;

    public SerialBThreadPrinter(BThreadManager manager) {
        this.manager = manager;
    }

    @Override
    public void setupPrinterModules(LoggerConfig loggerConfig, BThreadManager manager) {
        // Setup modules for the serial printer
        // Use 'manager' as needed
    }

    @Override
    public void start() {
        // Start the serial printer
        // Use 'manager' as needed
    }
}
