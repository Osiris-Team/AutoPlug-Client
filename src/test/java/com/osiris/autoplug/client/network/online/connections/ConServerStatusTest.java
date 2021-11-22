/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

class ConServerStatusTest {

    @Test
    void testTypes() {
        float f = 1.122f;
        double d = 0.0;
        DecimalFormat df = new DecimalFormat("#.#");
        DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        df.setDecimalFormatSymbols(dfs);
        String s2 = "1,0111";
        System.out.println(df.format(f));
        Float.parseFloat(df.format(f));
    }
}