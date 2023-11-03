/*
 * Copyright (c) 2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.bugs;

import com.osiris.autoplug.client.UtilsTest;
import com.osiris.jlib.search.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class VersionBugs {

    @Test
    void test() throws IOException {
        UtilsTest.init();
        Version.isLatestBigger("0", "10000000000000000000000000000000000");
    }
}
