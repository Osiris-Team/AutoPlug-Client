package com.osiris.autoplug.client.worlds;

import io.github.projectunified.mcserverupdater.UpdateBuilder;
import io.github.projectunified.mcserverupdater.UpdateStatus;

/** Isolated worker for existing updaters which need their own JVM system properties. */
public final class ServerInstallerWorker {
    private ServerInstallerWorker() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Expected software, version, work directory, output file");
        UpdateStatus status = UpdateBuilder.updateProject(args[0]).version(args[1]).workingDirectory(args[2])
                .outputFile(args[3]).debugConsumer(System.out::println).execute();
        if (!status.isSuccessStatus()) throw new IllegalStateException(status.getMessage(), status.getThrowable());
    }
}
