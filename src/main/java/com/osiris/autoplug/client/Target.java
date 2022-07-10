/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

public enum Target {
    MINECRAFT_CLIENT,
    MINECRAFT_SERVER,
    MINDUSTRY_SERVER,
    MINDUSTRY_CLIENT,
    OTHER;

    public static Target fromString(String s) {
        switch (s) {
            case "MINECRAFT_CLIENT":
                return Target.MINECRAFT_CLIENT;
            case "MINECRAFT_SERVER":
                return Target.MINECRAFT_SERVER;
            case "MINDUSTRY_SERVER":
                return Target.MINDUSTRY_SERVER;
            case "MINDUSTRY_CLIENT":
                return Target.MINDUSTRY_CLIENT;
            case "OTHER":
                return Target.OTHER;
            default:
                return null;
        }
    }
}
