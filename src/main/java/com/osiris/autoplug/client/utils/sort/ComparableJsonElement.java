/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.sort;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class ComparableJsonElement implements Comparable<ComparableJsonElement> {
    public JsonElement el;
    public Comparator<ComparableJsonElement> comparator;

    public ComparableJsonElement(JsonElement el, Comparator<ComparableJsonElement> comparator) {
        this.el = el;
        this.comparator = comparator;
    }

    @Override
    public int compareTo(@NotNull ComparableJsonElement o) {
        return comparator.compare(this, o);
    }
}
