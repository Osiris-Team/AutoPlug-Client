/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.tasks.updater.plugins.SpigotAuthenticator;
import com.osiris.autoplug.core.logger.AL;
import org.junit.jupiter.api.Test;

public class TestPremiumPluginsUpdating {

    @Test
    void test() {
        new AL().start();

        new SpigotAuthenticator();
    }
}
