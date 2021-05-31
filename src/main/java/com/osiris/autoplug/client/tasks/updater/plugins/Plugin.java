/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Represents a Minecraft Plugin.
 */
public class Plugin {

    private final String installationPath;
    private final String name;
    private final String version;
    private String author;

    /**
     * @param installationPath
     * @param name
     * @param version
     * @param author           NOTE THAT THIS CAN BE A STRING IN A LIST FORMAT CONTAINING MULTIPLE AUTHORS
     */
    public Plugin(String installationPath, Object name, Object version, @Nullable Object author) {
        this.installationPath = installationPath;
        this.name = String.valueOf(name);
        this.version = String.valueOf(version);
        // Why this is done? Because each plugin.yml file stores its authors list differently (Array or List, or numbers idk, or some other stuff...)
        // and all we want is just a simple list. This causes errors.
        // We get the list as a String, remove all "[]" brackets and " "(spaces) so we get a list of names only separated by commas
        // That is then sliced into a list.
        // Before: [name1, name2]
        // After: name1,name2
        if (author != null) this.author = Arrays.asList(
                String.valueOf(author).replaceAll("[\\[\\]]", "")
                        .split(","))
                .get(0);
        //if (author != null) this.author = String.valueOf(author).trim();
    }

    public String getInstallationPath() {
        return installationPath;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }
}
