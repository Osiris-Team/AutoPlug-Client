/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SystemCheckerTest {

    private SystemChecker systemChecker;

    @Test
    void checkInternetAccessMethod1() throws Exception {
        boolean reachable = InetAddress.getByName("www.google.com").isReachable(10000);
        if (!reachable) throw new Exception("Failed to reach www.google.com!");
    }

    @Test
    void checkInternetAccessMethod2() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://www.google.com").openConnection();
        connection.connect();
        connection.disconnect();
    }

    @BeforeEach
    public void setUp() {
        systemChecker = new SystemChecker();
    }

    @Test
    public void testCheckReadWritePermissions() {
        assertDoesNotThrow(() -> systemChecker.checkReadWritePermissions());
    }

    @Test
    public void testCheckInternetAccess() {
        assertDoesNotThrow(() -> systemChecker.checkInternetAccess());
    }
}