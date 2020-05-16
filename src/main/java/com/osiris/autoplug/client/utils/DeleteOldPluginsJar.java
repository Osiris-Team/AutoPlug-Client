/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author Crunchify.com
 * File Search Pattern Utility by Crunchify
 *
 */

public class DeleteOldPluginsJar extends SimpleFileVisitor<Path> {

    AutoPlugLogger logger = new AutoPlugLogger();

    // An interface that is implemented by objects that perform match operations on paths
    private final PathMatcher PathMatcher;

    private static int counter = 0;

    public DeleteOldPluginsJar(String searchPattern) {

        // getPathMatcher() returns a PathMatcher that performs match operations on the String representation of Path objects by
        // interpreting a given pattern
        PathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);
    }

    // Invoked for a file in a directory
    @Override
    public FileVisitResult visitFile(Path inputPath, BasicFileAttributes crunchifyFileAttr) {


        // Tells if given path matches this matcher's pattern
        if (PathMatcher.matches(inputPath.getFileName())) {
            try {

                //IMPORTANT: Make sure to unload the plugin before!


                if(Files.exists(inputPath)){
                    logger.global_info(" - Now deleting the old jar file at:" + inputPath);
                    Files.delete(inputPath);

                    if (Files.exists(inputPath)){
                        logger.global_warn(" - Couldn't delete the old jar file at: " + inputPath);
                        logger.global_warn(" - Please delete it manually or you will have this plugin (old and new version) twice!");
                    } else{
                        logger.global_info(" - The old jar file was removed!");
                    }
                } else{
                    logger.global_info(" - Old jar already deleted no to-do!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            counter++;

        }
        return FileVisitResult.CONTINUE;
    }



    // Invoked for a directory before entries in the directory are visited
    @Override
    public FileVisitResult preVisitDirectory(Path crunchifyPath, BasicFileAttributes crunchifyFileAttr) {
        if (PathMatcher.matches(crunchifyPath.getFileName())) {
            counter++;
        }
        return FileVisitResult.CONTINUE;
    }

    // Returns total number of matches for your pattern
    public int crunchifyTotalCount() {
        return counter;
    }

}
