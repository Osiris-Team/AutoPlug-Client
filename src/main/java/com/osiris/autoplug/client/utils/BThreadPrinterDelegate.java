package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.LoggerConfig;
import com.osiris.betterthread.BThreadManager;

public class BThreadPrinterDelegate {
    private BThreadManager manager;

    public BThreadPrinterDelegate(BThreadManager manager) {
        this.manager = manager;
    }

    public void setupDefaultPrinterModules(LoggerConfig loggerConfig, BThreadManager manager) {
        // Common setup for default printer modules
        // ...
    }

    public void setupSerialPrinterModules(LoggerConfig loggerConfig, BThreadManager manager) {
        // Common setup for serial printer modules
        // ...
    }

    public void startPrinter() {
        // Common logic to start the printer
        // ...
    }
}