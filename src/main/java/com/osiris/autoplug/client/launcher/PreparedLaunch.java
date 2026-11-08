package com.osiris.autoplug.client.launcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fully resolved command; argument boundaries are retained, including paths with spaces. */
public final class PreparedLaunch {
    public final Path executable;
    public final Path gameDir;
    public final List<String> arguments;
    public final String version;
    public final int javaMajor;
    final List<Path> temporaryFiles;

    public PreparedLaunch(Path executable, Path gameDir, List<String> arguments, String version, int javaMajor) {
        this(executable, gameDir, arguments, version, javaMajor, Collections.emptyList());
    }
    PreparedLaunch(Path executable, Path gameDir, List<String> arguments, String version, int javaMajor, List<Path> temporaryFiles) {
        this.executable = executable.toAbsolutePath().normalize();
        this.gameDir = gameDir.toAbsolutePath().normalize();
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.version = version;
        this.javaMajor = javaMajor;
        this.temporaryFiles = Collections.unmodifiableList(new ArrayList<>(temporaryFiles));
    }
    public List<String> command() {
        List<String> result = new ArrayList<>();
        result.add(executable.toString());
        result.addAll(arguments);
        return result;
    }
    public List<String> redactedCommand() {
        List<String> result = command();
        for (int i = 0; i + 1 < result.size(); i++) {
            if (result.get(i).equals("--accessToken") || result.get(i).equals("--session")) result.set(++i, "<redacted>");
        }
        return result;
    }
    public Path getExecutable() { return executable; }
    public Path getGameDir() { return gameDir; }
    public List<String> getArguments() { return arguments; }
    @Override public String toString() { return redactedCommand().toString(); }
}
