/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.managers;

import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.utils.GD;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches files, finds them and interacts with them
 */
public class FileManager {

    AutoPlugLogger logger = new AutoPlugLogger();

    private File queryFile = null;
    private List<File> queryFiles = new ArrayList<>();

    public void deleteOldPlugin(String pl_name) {

        String searchPattern = "*"+pl_name+"**.jar";
        //Find the file
        findFileInPluginsDir(searchPattern);
        //Delete the file
        if ( !queryFile.delete() ) {
            logger.global_warn(" [!] Couldn't remove old plugin jar at: " + queryFile.toPath() + " [!] ");
        }

    }

    //Finds all folders starting with "world" in main working dir
    public List<File> serverWorldsFolders() {

        String searchPattern = "*world*";
        //Find the files
        findFoldersInWorkingDir(searchPattern);
        //Return the results
        return queryFiles;

    }

    //Finds all files with another name than AutoPlug.jar in main working dir
    public List<File> serverFiles() {

        String searchPattern = "*";
        //Find the files
        findFilesInWorkingDir(searchPattern);
        //Return the results
        return queryFiles;

    }

    //Finds the server jar by its name
    public File serverJar(String server_jar_name) {

        String searchPattern = "*"+server_jar_name+"**.jar";
        //Find the file
        findJarFileInWorkingDir(searchPattern);
        //Return the result file
        return queryFile;

    }

    //Finds the first jar file with another name than AutoPlug.jar
    public File serverJar() {

        String searchPattern = "*.jar";
        //Find the file
        findJarFileInWorkingDir(searchPattern);
        //Return the result file
        return queryFile;

    }

    //Walks through files (skips AutoPlug.jar and all other subdirectories) and finds one jar
    private void findJarFileInWorkingDir(String searchPattern) {

        try{
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(GD.WORKING_DIR.toPath(), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {

                    //Must match the query name, can't be same name as AutoPlug.jar and can't be a directory
                    if (pathMatcher.matches(path.getFileName()) && !path.getFileName().toString().equals("AutoPlug.jar")) {

                        logger.global_debugger("FileManager","findFileInWorkingDir","Found server jar at: "  + path.toString());
                        queryFile = new File(path.toString());
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                    if (!dir.toString().equals(GD.WORKING_DIR.toString()) && attrs.isDirectory()){
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    else{
                        return FileVisitResult.CONTINUE;
                    }


                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });



        } catch (IOException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Error: "+ e.getMessage() + " [!]");
        }

    }

    //Walks through files (skips AutoPlug.jar and all other subdirectories) and finds ALL files
    private void findFilesInWorkingDir(String searchPattern) {

        try{
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(GD.WORKING_DIR.toPath(), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {

                    //Must match the query name, can't be same name as AutoPlug.jar and can't be a directory
                    if (pathMatcher.matches(path.getFileName()) && !path.getFileName().toString().equals("AutoPlug.jar")) {

                        logger.global_debugger("FileManager","findFilesInWorkingDir","Found file at: "  + path.toString());
                        //Adds files to list to return multiple files
                        queryFiles.add(new File(path.toString()));
                        return FileVisitResult.CONTINUE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                    if (!dir.toString().equals(GD.WORKING_DIR.toString()) && attrs.isDirectory()){
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    else{
                        return FileVisitResult.CONTINUE;
                    }


                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });



        } catch (IOException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Error: "+ e.getMessage() + " [!]");
        }

    }

    //Walks through files and finds ALL sub-folders in main working dir
    private void findFoldersInWorkingDir(String searchPattern) {

        try{
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(GD.WORKING_DIR.toPath(), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                    //Skip subdirectories of non main worki dirs and non matching dirs
                    if (!dir.toString().equals(GD.WORKING_DIR.toString()) && attrs.isDirectory() && !pathMatcher.matches(dir.getFileName())){
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    else if(!dir.toString().equals(GD.WORKING_DIR.toString())){
                        //Adds folders to list
                        queryFiles.add(new File(dir.toString()));
                        return FileVisitResult.CONTINUE;
                    }
                    else {
                        return FileVisitResult.CONTINUE;
                    }


                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });



        } catch (IOException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Error: "+ e.getMessage() + " [!]");
        }

    }

    //Walks through files (skips all Plugin directories) and finds one jar
    private void findFileInPluginsDir(String searchPattern) {

        try{
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);

            Files.walkFileTree(GD.PLUGINS_DIR.toPath(), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {

                    if (pathMatcher.matches(path.getFileName())) {

                        logger.global_debugger("FileManager","findFilesInPluginsDir","Found plugin jar at: " + path.toString());
                        queryFile = new File(path.toString());
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                    if (!dir.toString().equals(GD.PLUGINS_DIR.toString()) && attrs.isDirectory()){
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    else{
                        return FileVisitResult.CONTINUE;
                    }


                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });



        } catch (IOException e) {
            e.printStackTrace();
            logger.global_warn(" [!] Error: "+ e.getMessage() + " [!]");
        }

    }


}
