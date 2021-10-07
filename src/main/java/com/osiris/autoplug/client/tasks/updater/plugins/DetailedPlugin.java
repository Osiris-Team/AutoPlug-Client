/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

public class DetailedPlugin {
    boolean isPremium;
    private String configPath;
    private String installationPath;
    private String name;
    private String version;
    private String author;
    private int spigotId;
    private int bukkitId;
    private boolean ignoreContentType;
    private String customLink;

    public DetailedPlugin(String installationPath, String name, String version, String author, int spigotId, int bukkitId, String customLink) {
        this.installationPath = installationPath;
        this.name = name;
        this.version = version;
        this.author = author;
        this.spigotId = spigotId;
        this.bukkitId = bukkitId;
        this.customLink = customLink;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public void setPremium(boolean premium) {
        isPremium = premium;
    }

    public String getInstallationPath() {
        return installationPath;
    }

    public void setInstallationPath(String installationPath) {
        this.installationPath = installationPath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getSpigotId() {
        return spigotId;
    }

    public void setSpigotId(int spigotId) {
        this.spigotId = spigotId;
    }

    public int getBukkitId() {
        return bukkitId;
    }

    public void setBukkitId(int bukkitId) {
        this.bukkitId = bukkitId;
    }

    public boolean getIgnoreContentType() {
        return ignoreContentType;
    }

    public void setIgnoreContentType(boolean ignoreContentType) {
        this.ignoreContentType = ignoreContentType;
    }

    public String getCustomLink() {
        return customLink;
    }

    public void setCustomLink(String customLink) {
        this.customLink = customLink;
    }
}
