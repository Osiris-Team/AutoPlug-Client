package io.github.projectunified.mcserverupdater.util;

import io.github.projectunified.mcserverupdater.UpdateBuilder;

public class VersionQuery {
    public final String version;
    public final boolean isDefault;
    public final UpdateBuilder updateBuilder;

    public VersionQuery(String version, UpdateBuilder updateBuilder) {
        this.version = version;
        this.isDefault = "latest".equalsIgnoreCase(version) || "default".equalsIgnoreCase(version);
        this.updateBuilder = updateBuilder;
    }
}
