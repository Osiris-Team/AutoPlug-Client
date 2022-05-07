/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.protector;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

class NetworkProtectorTest {

    @Test
    void aa() throws UnknownHostException {
        System.out.println(InetAddress.getLocalHost().getHostAddress());
    }
}