/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.managers;

import com.google.gson.annotations.SerializedName;

public class Pack {
    public String name;
    public Type type;

    public enum Type {

        @SerializedName("0")
        MINECRAFT_PLUGINS(0),

        @SerializedName("1")
        MINECRAFT_MODS(1);

        private final int value;

        Type(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

    }
}
