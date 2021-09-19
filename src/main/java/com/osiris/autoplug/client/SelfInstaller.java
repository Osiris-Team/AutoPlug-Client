/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.utils.GD;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class SelfInstaller {

    /**
     * If this method is called, it means that the current jars location (working dir)
     * is in the /autoplug/downloads directory and we need to install it.
     * For that we take the parent directory (which should be the server root)
     * search for the AutoPlug-Client.jar in it and overwrite it with our current jars copy.
     * Note that the AutoPlug-Client-Copy.jar must already exist. Normally it gets created
     * right after successfully downloading the update to the /autoplug/downloads directory.
     *
     * @param parentDir the parent directory of the current /autoplug/downloads directory.
     */
    public void installUpdateAndStartIt(@NotNull File parentDir) throws Exception {

        class MyVisitor<T> extends SimpleFileVisitor<Path> {
            private final String fileToFindName;
            @Nullable
            private File oldJar = null;

            public MyVisitor(String fileToFindName) {
                this.fileToFindName = fileToFindName;
            }

            @NotNull
            @Override
            public FileVisitResult visitFile(@NotNull Path path, BasicFileAttributes attrs) throws IOException {
                if (path.toFile().getName().equals(fileToFindName)) {
                    oldJar = path.toFile();
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Nullable
            public File getResult() {
                return oldJar;
            }
        }

        File oldJar = new FileManager().autoplugJar(parentDir);
        if (oldJar == null)
            throw new Exception("Self-Update failed! Cause: Couldn't find the old AutoPlug-Client.jar or a jar with autoplug.properties inside, in " + parentDir.getAbsolutePath());

        // Since we can't copy this jar because its currently running
        // we rely on a already made copy of it.
        // That copy should've been done after downloading the update (this jar).
        MyVisitor<Path> myVisitor = new MyVisitor<Path>("AutoPlug-Client-Copy.jar");
        Files.walkFileTree(GD.WORKING_DIR.toPath(), Collections.singleton(FileVisitOption.FOLLOW_LINKS), 1, myVisitor);
        File copyJar = myVisitor.getResult();
        if (copyJar == null)
            throw new Exception("Self-Update failed! Cause: Couldn't find the update file 'AutoPlug-Client-Copy.jar' in " + GD.WORKING_DIR.getAbsolutePath());

        // Copy and overwrite the old jar with the update file
        // Note: Deletion of the current jar and the copy jar are done
        // at startup.
        System.out.println("Installing update for jar file: '" + oldJar.getAbsolutePath() + "'...");
        for (int i = 1; i < 11; i++) {
            try {
                if (!isFileInUse(oldJar)) {
                    System.out.println("Old jar is not in use. Installing update...");
                    Files.copy(copyJar.toPath(), oldJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    break;
                }
            } catch (Exception e) {
                if (i == 10) {
                    throw e;
                }
            }
            Thread.sleep(1000);
        }
        System.out.println("Successfully installed update!");
        // Start that updated old jar and close this one
        startJarFromPath(oldJar, oldJar.getParentFile());
        System.exit(0);
    }

    public boolean isFileInUse(File file) {
        try {
            if (file == null || !file.exists())
                return false;

            File copy = new File(System.getProperty("user.dir") + "/Copy-" + new Random().nextInt(10000) + "-" + file.getName());
            Files.copy(file.toPath(),
                    copy.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);

            file.delete();
            Files.copy(copy.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            copy.delete();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Starts the provided jar file with the same VM-Arguments as the current jar. <br>
     * Original author: https://stackoverflow.com/questions/4159802/how-can-i-restart-a-java-application/48992863#48992863 <br>
     *
     * @param jarToStart the jar file to start.
     * @param workingDir the jar files working directory.
     */
    public void startJarFromPath(File jarToStart, File workingDir) throws Exception {
        List<String> commands = new ArrayList<>();
        appendJavaExecutable(commands);
        appendVMArgs(commands);
        commands.add("-jar");
        commands.add(jarToStart.getAbsolutePath());

        System.out.println("Starting jar with: " + commands);
        Process process = new ProcessBuilder(commands)
                .directory(workingDir)
                .inheritIO()
                .start();
        // Wait until the process is alive
        // dunno if this really helps preventing the closing of a screen session for linux users
        while (!process.isAlive())
            Thread.sleep(100);
    }

    private void appendJavaExecutable(@NotNull List<String> cmd) {
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    }

    private void appendVMArgs(@NotNull Collection<String> cmd) {
        Collection<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();

        String javaToolOptions = System.getenv("JAVA_TOOL_OPTIONS");
        if (javaToolOptions != null) {
            Collection<String> javaToolOptionsList = Arrays.asList(javaToolOptions.split(" "));
            vmArguments = new ArrayList<>(vmArguments);
            vmArguments.removeAll(javaToolOptionsList);
        }

        cmd.addAll(vmArguments);
    }

    private void appendClassPath(@NotNull List<String> cmd) {
        cmd.add("-cp");
        cmd.add(ManagementFactory.getRuntimeMXBean().getClassPath());
    }

    private void appendEntryPoint(@NotNull List<String> cmd) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        StackTraceElement stackTraceElement = stackTrace[stackTrace.length - 1];
        String fullyQualifiedClass = stackTraceElement.getClassName();
        String entryMethod = stackTraceElement.getMethodName();
        if (!entryMethod.equals("main"))
            throw new AssertionError("Entry method is not called 'main': " + fullyQualifiedClass + '.' + entryMethod);

        cmd.add(fullyQualifiedClass);
    }

}
