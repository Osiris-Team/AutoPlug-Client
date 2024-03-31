/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.managers;

import static com.osiris.jprocesses2.util.OS.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;

/**
 * Search & find files!
 * TODO (Not tasty code. Rework needed!)
 */
public class FileManager {
    private final FileSearcher fileSearcher = new FileSearcher();

    private final List<File> queryFiles = new ArrayList<>();
    @Nullable
    private File queryFile = null;

    /**
     * Example current working dir: /user/directory <br>
     * Example relative path: ./AutoPlug-Client.jar <br>
     * Example result: /user/directory/AutoPlug-Client.jar <br>
     *
     * @param shortPath a string that contains './' which represents the current working dir
     * @return a {@link File} with the absolute path like in the example result above.
     */
    public static File convertRelativeToAbsolutePath(@NotNull String shortPath) {
        return new File(shortPath.replace("./", GD.WORKING_DIR + "/"));
    }

    public void deleteOldPlugin(String pl_name) {
        String searchPattern = "*" + pl_name + "**.jar";
        //Find the file
        FileSearcher.findFileInPluginsDir(searchPattern);
        //Delete the file
        if (!queryFile.delete()) {
            AL.warn(" [!] Couldn't remove old plugin jar at: " + queryFile.toPath() + " [!] ");
        }

    }

    /**
     * Gets all plugins located in the /plugins folder.
     *
     * @return list of files.
     */
    @NotNull
    public List<File> getAllPlugins() {
        String searchPattern = "*.jar";
        //Find the file
        FileSearcher.findFilesInPluginsDir(searchPattern);
        return queryFiles;
    }

    //Finds all folders starting with "world" in main working dir
    @NotNull
    public List<File> serverWorldsFolders() {
        String searchPattern = "*world*";
        //Find the files
        FileSearcher.findFoldersInWorkingDir(searchPattern);
        //Return the results
        return queryFiles;

    }

    /**
     * Finds all files with another name than AutoPlug.jar in main working dir.
     * Only files not folders!
     */
    @NotNull
    public List<File> serverFiles() {
        String searchPattern = "*";
        //Find the files
        FileSearcher.findFilesInWorkingDir(searchPattern);
        //Return the results
        return queryFiles;

    }

    public File autoplugJar(File dirToSearch) {
        String searchPattern = "*.jar";
        //Find the file
        fileSearcher.findAutoPlugJarFileInDir(searchPattern, dirToSearch);
        //Return the result file
        return fileSearcher.getQueryFile();
    }

    /**
     * Finds the first jar file with another name than AutoPlugLauncher.jar.
     *
     * @return server jar file.
     */
    @Nullable
    public File serverExecutable(File dir) {
        List<File> files = serverExecutables(dir);
        if (files.isEmpty()) return null;
        return files.get(0);
    }

    public List<File> serverExecutables(File dir) {
        List<File> files = new ArrayList<>();
        for (File f : dir.listFiles()) {
            if (f.isFile() && // Can't be directory
                    !f.getName().toLowerCase().contains("crashhandler") // Avoid unity crash handler exe
                    && !jarContainsAutoPlugProperties(f) // Can't be AutoPlug.jar
                    && (f.getName().endsWith(".jar")
                    || f.getName().endsWith(".exe")
                    || (!isWindows && !f.getName().contains("."))) // On Unix binaries/exes names usually don't contain dots
            ) files.add(f);
        }
        return files;
    }

    @NotNull
    public List<File> getFilesFrom(@NotNull String dirPath) throws IOException {
        Objects.requireNonNull(dirPath);
        return getFilesFrom(FileSystems.getDefault().getPath(dirPath));
    }

    @NotNull
    public List<File> getFilesFrom(@NotNull File dir) throws IOException {
        Objects.requireNonNull(dir);
        return getFilesFrom(dir.toPath());
    }

    /**
     * Depth is 1. All folders are ignored.
     *
     * @param dirPath the directory path.
     * @return a list of files in this directory.
     * @throws IOException
     */
    @NotNull
    public List<File> getFilesFrom(@NotNull Path dirPath) throws IOException {
        Objects.requireNonNull(dirPath);
        List<File> list = new ArrayList<>();
        SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            @NotNull
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(dirPath)) // Makes sure the parent directory is not added to the results list and that its child dirs are read
                    return FileVisitResult.CONTINUE;
                else
                    return FileVisitResult.SKIP_SUBTREE;
            }

            @NotNull
            @Override
            public FileVisitResult visitFile(@NotNull Path path, BasicFileAttributes attrs) throws IOException {
                list.add(new File(path.toString()));
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(dirPath, visitor);
        return list;
    }

    @NotNull
    public List<File> getFoldersFrom(@NotNull String dirPath) throws IOException {
        Objects.requireNonNull(dirPath);
        return getFoldersFrom(FileSystems.getDefault().getPath(dirPath));
    }

    @NotNull
    public List<File> getFoldersFrom(@NotNull File dir) throws IOException {
        Objects.requireNonNull(dir);
        return getFoldersFrom(dir.toPath());
    }

    /**
     * Depth is 1. All sub-folders are ignored.
     *
     * @param dirPath the directory path.
     * @return a list of folders in this directory.
     * @throws IOException
     */
    @NotNull
    public List<File> getFoldersFrom(@NotNull Path dirPath) throws IOException {
        Objects.requireNonNull(dirPath);
        List<File> list = new ArrayList<>();
        SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            @NotNull
            @Override
            public FileVisitResult preVisitDirectory(@NotNull Path path, BasicFileAttributes attrs) throws IOException {
                if (path.equals(dirPath)) // Makes sure the parent directory is not added to the results list and that its child dirs are read
                    return FileVisitResult.CONTINUE;

                list.add(new File(path.toString()));
                return FileVisitResult.SKIP_SUBTREE;
            }
        };
        Files.walkFileTree(dirPath, visitor);
        return list;
    }
    static boolean  jarContainsAutoPlugProperties(File jar) {
        if (!jar.getName().endsWith(".jar")) return false;
        FileInputStream fis = null;
        ZipInputStream zis = null;
        ZipEntry ze = null;
        try {
            fis = new FileInputStream(jar);
            zis = new ZipInputStream(fis);
            ze = zis.getNextEntry();

            while (ze != null) {
                if (ze.getName().equals("autoplug.properties")) {
                    return true;
                }
                // Get next file in zip
                zis.closeEntry();
                ze = zis.getNextEntry();
            } // Loop end
            // Close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (Exception e) {
            AL.warn("Failed to get information for: " + jar.getName(), e);
        } finally {
            try {
                if (zis != null && ze != null)
                    zis.closeEntry();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (zis != null)
                    zis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (fis != null) fis.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
