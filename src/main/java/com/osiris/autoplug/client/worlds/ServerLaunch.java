package com.osiris.autoplug.client.worlds;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An argument vector, never a shell command. */
public final class ServerLaunch {
    public final List<String> command;
    public final Path directory;
    public final String stopCommand, restartCommand;
    public ServerLaunch(List<String> command, Path directory) {
        this(command, directory, "stop", null);
    }
    public ServerLaunch(List<String> command, Path directory, String stopCommand, String restartCommand) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("Server command is empty");
        this.command = Collections.unmodifiableList(new ArrayList<>(command));
        this.directory = directory.toAbsolutePath().normalize();
        this.stopCommand = stopCommand; this.restartCommand = restartCommand;
    }
}
