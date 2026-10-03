package com.osiris.autoplug.client.worlds;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An argument vector, never a shell command. */
public final class ServerLaunch {
    public final List<String> command;
    public final Path directory;
    public ServerLaunch(List<String> command, Path directory) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("Server command is empty");
        this.command = Collections.unmodifiableList(new ArrayList<>(command));
        this.directory = directory.toAbsolutePath().normalize();
    }
}
