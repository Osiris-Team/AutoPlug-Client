/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.tasks.CustomDisplayer;
import com.osiris.betterthread.BetterThreadDisplayer;
import com.osiris.betterthread.BetterThreadManager;

public class MyBetterThreadManager {
    public BetterThreadManager manager;
    public BetterThreadDisplayer displayer;
    public CustomDisplayer customDisplayer;

    public MyBetterThreadManager(BetterThreadManager manager, BetterThreadDisplayer displayer) {
        this.manager = manager;
        this.displayer = displayer;
    }

    public MyBetterThreadManager(BetterThreadManager manager, BetterThreadDisplayer displayer, CustomDisplayer customDisplayer) {
        this.manager = manager;
        this.displayer = displayer;
        this.customDisplayer = customDisplayer;
    }
}
