/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.managers;

import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.AutoPlugLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class FileManager {

    AutoPlugLogger logger = new AutoPlugLogger();

    private File queryFile = null;

    public void deleteOldPlugin(String pl_name) {

        String searchPattern = "*"+pl_name+"**.jar";
        //Find the file
        findFileInPluginsDir(searchPattern);
        //Delete the file
        if ( !queryFile.delete() ) {
            logger.global_warn(" [!] Couldn't remove old plugin jar at: " + queryFile.toPath() + " [!] ");
        }

    }

    //Finds the server jar by its name
    public File serverJar(String server_jar_name) {

        String searchPattern = "*"+server_jar_name+"**.jar";
        //Find the file
        findFileInWorkingDir(searchPattern);
        //Return the result file
        return queryFile;

    }

    //Finds the first jar file with another name than AutoPlug.jar
    public File serverJar() {

        String searchPattern = "*.jar";
        //Find the file
        findFileInWorkingDir(searchPattern);
        //Return the result file
        return queryFile;

    }

    //Walks through files (skips AutoPlug.jar and all other subdirectories) and finds one jar
    private void findFileInWorkingDir(String searchPattern) {

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
