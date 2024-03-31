package com.osiris.autoplug.client.managers;

import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class FileSearcher {
    private static final List<File> queryFiles = new ArrayList<>();
    @Nullable
    private static File queryFile = null;

    public static void findFileInPluginsDir(String searchPattern) {
        try {
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(GD.PLUGINS_DIR.toPath(), new SimpleFileVisitor<Path>() {

                @NotNull
                @Override
                public FileVisitResult visitFile(@NotNull Path path,
                                                 BasicFileAttributes attrs) throws IOException {

                    if (pathMatcher.matches(path.getFileName())) {

                        queryFile = new File(path.toString());
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {

                    if (!dir.toString().equals(GD.PLUGINS_DIR.toString()) && attrs.isDirectory()) {
                        return FileVisitResult.SKIP_SUBTREE;
                    } else {
                        return FileVisitResult.CONTINUE;
                    }


                }

                @NotNull
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });


        } catch (IOException e) {
            e.printStackTrace();
            AL.warn(" [!] Error: " + e.getMessage() + " [!]");
        }
    }

    static void findFilesInPluginsDir(String searchPattern) {

        try {
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(GD.PLUGINS_DIR.toPath(), new SimpleFileVisitor<Path>() {

                @NotNull
                @Override
                public FileVisitResult visitFile(@NotNull Path path,
                                                 BasicFileAttributes attrs) throws IOException {

                    if (pathMatcher.matches(path.getFileName())) {

                        queryFile = new File(path.toString());
                        queryFiles.add(queryFile);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {

                    if (!dir.toString().equals(GD.PLUGINS_DIR.toString()) && attrs.isDirectory()) {
                        return FileVisitResult.SKIP_SUBTREE;
                    } else {
                        return FileVisitResult.CONTINUE;
                    }


                }

                @NotNull
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });


        } catch (IOException e) {
            AL.warn(e);
        }

    }

    public static void findFilesInWorkingDir(String searchPattern) {
        try {
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(GD.WORKING_DIR.toPath(), new SimpleFileVisitor<Path>() {

                @NotNull
                @Override
                public FileVisitResult visitFile(@NotNull Path path,
                                                 BasicFileAttributes attrs) throws IOException {

                    if (pathMatcher.matches(path.getFileName())) {
                        //Adds files to list to return multiple files
                        queryFiles.add(new File(path.toString()));
                        return FileVisitResult.CONTINUE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {

                    if (!dir.toString().equals(GD.WORKING_DIR.toString()) && attrs.isDirectory()) {
                        return FileVisitResult.SKIP_SUBTREE;
                    } else {
                        return FileVisitResult.CONTINUE;
                    }
                }

                @NotNull
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });


        } catch (IOException e) {
            e.printStackTrace();
            AL.warn(" [!] Error: " + e.getMessage() + " [!]");
        }
    }

    public static void findFoldersInWorkingDir(String searchPattern) {
        try {
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(GD.WORKING_DIR.toPath(), new SimpleFileVisitor<Path>() {

                @NotNull
                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                // Skip subdirectories of non main working dirs and non matching dirs
                    if (shouldSkipDirectory(dir, attrs)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    } else if (!dir.toString().equals(GD.WORKING_DIR.toString())) {
                        // Adds folders to list
                        queryFiles.add(new File(dir.toString()));
                        return FileVisitResult.CONTINUE;
                    } else {
                        return FileVisitResult.CONTINUE;
                    }
                }

                // Method to determine whether to skip the directory
                private boolean shouldSkipDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(GD.WORKING_DIR) && attrs.isDirectory() && !matchesPattern(dir);
                }

                // Method to check if directory name matches a pattern
                private boolean matchesPattern(Path dir) {
                    return pathMatcher.matches(dir.getFileName());
                }


                @NotNull
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });


        } catch (IOException e) {
            e.printStackTrace();
            AL.warn(" [!] Error: " + e.getMessage() + " [!]");
        }
    }

    public void findAutoPlugJarFileInDir(String searchPattern, File dirToSearch) {
        try {
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(dirToSearch.toPath(), new SimpleFileVisitor<Path>() {

                @NotNull
                @Override
                public FileVisitResult visitFile(@NotNull Path path,
                                                 BasicFileAttributes attrs) throws IOException {
                    if (pathMatcher.matches(path.getFileName())
                            && FileManager.jarContainsAutoPlugProperties(path.toFile())) {
                        queryFile = new File(path.toString());
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {

                    if (!dir.toString().equals(dirToSearch.toString()) && attrs.isDirectory()) {
                        return FileVisitResult.SKIP_SUBTREE;
                    } else {
                        return FileVisitResult.CONTINUE;
                    }
                }

                @NotNull
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });


        } catch (IOException e) {
            AL.warn(e);
        }
    }

    // Getter methods for queryFiles and queryFile
    public List<File> getQueryFiles() {
        return queryFiles;
    }

    @Nullable
    public File getQueryFile() {
        return queryFile;
    }
}
