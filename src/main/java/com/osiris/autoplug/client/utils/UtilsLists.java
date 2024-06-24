/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import java.util.Arrays;
import java.util.List;

public class UtilsLists {
    public <T> String toString(List<T> list) {
        return Arrays.toString(list.toArray());
    }
}
