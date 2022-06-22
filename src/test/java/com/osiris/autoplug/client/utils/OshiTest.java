/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import org.junit.jupiter.api.Test;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

public class OshiTest {
    @Test
    void testCpu() {
        SystemInfo si = new SystemInfo();
        for (int i = 0; i < 10; i++) {
            HardwareAbstractionLayer hal = si.getHardware();
            CentralProcessor cpu = hal.getProcessor();
            System.out.println(cpu.getSystemCpuLoad(1000) * 100);
        }
    }
}
