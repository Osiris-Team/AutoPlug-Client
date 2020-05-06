/*
 *  Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.mcserver;

import com.osiris.autoplug.client.Settings;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import com.osiris.autoplug.client.mcserver.SearchAndStartServerJar;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Scanner;

public class MCServer {

    private AutoPlugLogger logger = new AutoPlugLogger();

    private Process process;
    private Scanner scanner;
    private OutputStream os;

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    public boolean isCommand(String command) {
        if (command.equals(".help")){
            logger.global_info( " [All Console commands]");
            logger.global_info( " .help - prints out this (if you didn't notice)");
            logger.global_info( " .stop - stops the autoplug client");
            logger.global_info( " .morerandomcommandscomingsoonmaboi");

            return true;
        }
        else if (command.equals(".morerandomcommandscomingsoonmaboi")) {
            logger.global_info( " U're nerd");
            return true;
        }
        else if (command.equals(".stop")) {
            logger.global_info( " Stopping AutoPlug-Client! Good-by!");
            System.exit(0);
            return true;
        }
        else{
            return false;
        }
    }

    public boolean startup(){

        AutoPlugLogger logger = new AutoPlugLogger();
        String working_dir = System.getProperty("user.dir");
        String directory_plugins = working_dir + "\\plugins";
        logger.global_debugger("MCServer","startup"," Searching for server.jar at " + working_dir + "...");

        Path pluginPath = Paths.get(directory_plugins);
        Path mainPath = Paths.get(working_dir);

        boolean success = true;
        String crunchifyExtension = "*.jar";
        //This will search the server jar (maybe update it) and execute it inside the current console (I/O)
        SearchAndStartServerJar searchAndStartServerJar = new SearchAndStartServerJar(crunchifyExtension);
        try {
            //walk through all files in main directory and search for any jar with another name than AutoPlug.jar (subdirectories are ignored)
            logger.global_info(" Searching for server jar file...");
            Files.walkFileTree(mainPath, searchAndStartServerJar);

            String path = searchAndStartServerJar.getServer_path();
            if (!path.equals("false")){
                createProcess(path);
                createConsole();
            }


            success = true;
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }

        return success;
    }

    private void createProcess(String path) throws IOException {

        ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", path);
        processBuilder.directory();
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();
    }

    private void createConsole(){

        // Then retreive the process streams
        InputStream in = process.getInputStream();

        //New thread for input from server console
        new Thread(new Runnable() {
            public void run() {
                try{

                    if (isRunning()){
                        int b = -1;
                        while ( (b =  in.read()) != -1 ) {
                            System.out.write(b);
                        }
                        if (!isRunning()){ in.close(); }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();

        scanner = new Scanner(System.in);
        //New thread for user input
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        os = null;
                        String user_input = scanner.nextLine();
                        String final_user_input = user_input + "\n"; //WARNING: Without the \n user input isn't registered by the server console

                        if (isRunning()) {
                            os = process.getOutputStream();
                            os.write(final_user_input.getBytes());
                            os.flush();
                            if (isCommand(user_input)) {
                            } else if (!isRunning() && !isCommand(user_input)) {
                                logger.global_info(" Command not found! Enter .help for all available commands!");
                            }
                        } else if (isCommand(user_input)) {
                        } else if (!isCommand(user_input)) {
                            logger.global_info(" Command not found! Enter .help for all available commands!");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }


    public boolean destroy(){

        if (isRunning()){
            process.destroy();
        }

        if (!process.isAlive()) {
                return true;
            } else{
                return false;
            }


    }


}

class SearchAndStartServerJar extends SimpleFileVisitor<Path> {

    private AutoPlugLogger logger = new AutoPlugLogger();

    private String server_path = "false";

    public String getServer_path() {
        return server_path;
    }

    public void setServer_path(String server_path) {
        this.server_path = server_path;
    }

    // An interface that is implemented by objects that perform match operations on paths
    private final java.nio.file.PathMatcher PathMatcher;

    private static int counter = 0;

    public SearchAndStartServerJar(String searchPattern) {

        // getPathMatcher() returns a PathMatcher that performs match operations on the String representation of Path objects by
        // interpreting a given pattern
        PathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + searchPattern);
    }

    // Invoked for a file in a directory
    @Override
    public FileVisitResult visitFile(Path inputPath, BasicFileAttributes crunchifyFileAttr) throws IOException {


        //System.out.println(inputPath.toAbsolutePath().toString());
        //System.out.println(System.getProperty("user.dir"));
        if (!inputPath.toFile().isDirectory() && !inputPath.getFileName().toString().equals("AutoPlug.jar") && PathMatcher.matches(inputPath.getFileName())){
            counter++;

            logger.global_info(" Found server jar at: " + inputPath.toAbsolutePath());

            Settings settings = new Settings();
            if (settings.isConfig_server_check()){
                logger.global_info(" Searching for updates...");

                new UpdateServerJar(inputPath);
            } else {
                logger.global_info(" Skipping server-check...");
            }

            logger.global_info(" Starting up: " +  inputPath.getFileName());
            setServer_path(inputPath.toAbsolutePath().toString());

            counter++;
            return FileVisitResult.TERMINATE;
        }  else {
            logger.global_info("Searching server jar... Not matching: "+inputPath.getFileName());
            return FileVisitResult.CONTINUE;
        }
    }



    // Invoked for a directory before entries in the directory are visited
    @Override
    public FileVisitResult preVisitDirectory(Path crunchifyPath, BasicFileAttributes crunchifyFileAttr) {

        //System.out.println(crunchifyPath);
        //System.out.println(System.getProperty("user.dir"));
        if (crunchifyPath.toFile().isDirectory() && !crunchifyPath.toAbsolutePath().toString().equals(System.getProperty("user.dir"))){
            //Just scanning trough main directory ignoring subdirectories
            return FileVisitResult.SKIP_SUBTREE;

        }
        else{

            if (PathMatcher.matches(crunchifyPath.getFileName())) {
                //System.out.println(crunchifyPath.getFileName());
                return FileVisitResult.CONTINUE;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    // Returns total number of matches for your pattern
    public int crunchifyTotalCount() {
        return counter;
    }

}
