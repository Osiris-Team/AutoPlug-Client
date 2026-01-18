package io.github.projectunified.mcserverupdater.updater;

import io.github.projectunified.mcserverupdater.UpdateBuilder;
import io.github.projectunified.mcserverupdater.api.Checksum;
import io.github.projectunified.mcserverupdater.api.DebugConsumer;
import io.github.projectunified.mcserverupdater.api.Updater;
import io.github.projectunified.mcserverupdater.util.VersionQuery;
import io.github.projectunified.mcserverupdater.util.WebUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SpigotUpdater implements Updater {
    private final UpdateBuilder updateBuilder;
    private final String version;

    public SpigotUpdater(VersionQuery versionQuery) {
        this.updateBuilder = versionQuery.updateBuilder;
        this.version = versionQuery.isDefault ? "latest" : versionQuery.version;
    }

    private File downloadBuildTools() {
        File file = new File(updateBuilder.workingDirectory(), "BuildTools.jar");
        try {
            String buildToolsURL = "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar";
            updateBuilder.debug("Downloading BuildTools from " + buildToolsURL);
            URLConnection connection = WebUtils.openConnection(buildToolsURL, updateBuilder);
            InputStream inputStream = connection.getInputStream();
            Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return file;
        } catch (IOException e) {
            debug("Failed to download BuildTools", e);
            return null;
        }
    }

    @Override
    public boolean update(File file) throws Exception {
        File buildTools = downloadBuildTools();
        if (buildTools == null) {
            return false;
        }
        File outputDir = new File(updateBuilder.workingDirectory(), "output");
        updateBuilder.debug("Running BuildTools...");
        if (!runBuildTools(buildTools, outputDir)) {
            return false;
        }
        File[] outputFiles = outputDir.listFiles();
        if (outputFiles != null) {
            for (File outputFile : outputFiles) {
                String name = outputFile.getName();
                if (name.startsWith("spigot-") && name.endsWith(".jar")) {
                    updateBuilder.debug("Copying " + name + " to " + file.getName());
                    Files.copy(outputFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.delete(outputFile.toPath());
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public Checksum getChecksumChecker() {
        return null;
    }

    @Override
    public DebugConsumer getDebugConsumer() {
        return updateBuilder.debugConsumer();
    }

    private boolean runBuildTools(File buildTools, File outputDir) throws IOException, InterruptedException {
        String javaExecutable = System.getProperty("MCServerUpdater.javaExecutable", "java");
        ProcessBuilder processBuilder = new ProcessBuilder(
                javaExecutable,
                "-jar",
                buildTools.getAbsolutePath(),
                "--rev", version,
                "--output-dir", outputDir.getAbsolutePath(),
                "--compile-if-changed",
                "--disable-java-check"
        );
        processBuilder.directory(updateBuilder.workingDirectory());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
        InputStream inputStream = process.getInputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            updateBuilder.debug(new String(buffer, 0, length).trim());
        }
        return process.waitFor() == 0;
    }
}
