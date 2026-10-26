/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 * Implements GitHub issue #296 – Minecraft Client Launcher.
 */

package com.osiris.autoplug.client.minecraft.profile;

import com.osiris.autoplug.client.configs.MyYaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dream-YAML backed persistence for a single {@link LaunchProfile}.
 * File location: {@code autoplug/profiles/<name>/profile.yml}
 */
public class ProfileConfig extends MyYaml {

    public YamlSection profileName;
    public YamlSection minecraftVersion;
    public YamlSection loaderType;
    public YamlSection gameDir;
    public YamlSection jvmArgs;
    public YamlSection maxMemoryMb;
    public YamlSection versionType;
    public YamlSection serverAddress;
    public YamlSection mods;

    public ProfileConfig(File profileYml)
            throws IOException, DuplicateKeyException, YamlReaderException,
                   IllegalListException, NotLoadedException, IllegalKeyException, YamlWriterException {
        super(profileYml.getAbsolutePath());
        lockFile();
        load();
        String n = "profile";
        profileName      = put(n, "name").setDefValues("");
        minecraftVersion = put(n, "minecraft-version").setDefValues("1.21.1");
        loaderType       = put(n, "loader-type").setDefValues("vanilla");
        gameDir          = put(n, "game-dir").setDefValues("");
        jvmArgs          = put(n, "jvm-args").setDefValues();
        maxMemoryMb      = put(n, "max-memory-mb").setDefValues("0");
        versionType      = put(n, "version-type").setDefValues("release");
        serverAddress    = put(n, "server-address").setDefValues("");
        mods             = put(n, "mods").setDefValues();
        save();
        unlockFile();
    }

    /** Converts this config to an in-memory {@link LaunchProfile}. */
    public LaunchProfile toProfile() {
        LaunchProfile p = new LaunchProfile(
                profileName.asString() != null && !profileName.asString().isEmpty()
                        ? profileName.asString()
                        : new File(this.file.getParent()).getName(),
                minecraftVersion.asString() != null ? minecraftVersion.asString() : "1.21.1");
        p.loaderType = loaderType.asString() != null ? loaderType.asString() : "vanilla";
        String gd = gameDir.asString();
        if (gd != null && !gd.isEmpty()) p.gameDir = new File(gd);
        List<String> args = jvmArgs.asStringList();
        if (args != null) p.jvmArgs = args;
        p.maxMemoryMb = maxMemoryMb.asInt();
        p.versionType = versionType.asString();
        String sa = serverAddress.asString();
        if (sa != null && !sa.isEmpty()) p.serverAddress = sa;
        List<String> modList = mods.asStringList();
        if (modList != null) p.mods = modList;
        return p;
    }

    /** Writes a {@link LaunchProfile} into this config and saves. */
    public void fromProfile(LaunchProfile p)
            throws IOException, DuplicateKeyException, YamlReaderException,
                   IllegalListException, YamlWriterException {
        profileName.setValues(p.name);
        minecraftVersion.setValues(p.minecraftVersion);
        loaderType.setValues(p.loaderType);
        gameDir.setValues(p.gameDir != null ? p.gameDir.getAbsolutePath() : "");
        jvmArgs.setValues(p.jvmArgs.toArray(new String[0]));
        maxMemoryMb.setValues(String.valueOf(p.maxMemoryMb));
        versionType.setValues(p.versionType != null ? p.versionType : "release");
        serverAddress.setValues(p.serverAddress != null ? p.serverAddress : "");
        mods.setValues(p.mods.toArray(new String[0]));
        save();
    }

    @Override
    public com.osiris.dyml.Yaml validateValues() {
        // Nothing to validate beyond defaults
        return this;
    }
}
