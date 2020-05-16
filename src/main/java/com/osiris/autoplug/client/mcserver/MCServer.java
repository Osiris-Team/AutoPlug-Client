/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.mcserver;

import com.osiris.autoplug.client.Settings;
import com.osiris.autoplug.client.utils.AutoPlugLogger;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.lang.time.DateUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Iterator;
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
            logger.global_info( " .stop - stops the autoplug client and the server (experimental)");
            logger.global_info( " .morerandomcommandscomingsoonmaboi");

            return true;
        }
        else if (command.equals(".morerandomcommandscomingsoonmaboi")) {
            logger.global_info( " U're nerd");
            return true;
        }
        else if (command.equals(".stop")) {
            logger.global_info( " Stopping AutoPlug-Client! Good-by!");
            destroy();
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
        String directory_plugins = working_dir + "/plugins";
        logger.global_debugger("MCServer","startup"," Searching for server.jar at " + working_dir + "...");

        Path mainPath = Paths.get(working_dir);
        File file = new File(working_dir);

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
                createBackups(path, directory_plugins);
                updateServer(new File(path));
                createProcess(path);
                createConsole();
            }
            else{
            logger.global_warn(" Couldn't find a server jar file to startup!");
            success = false;
        }


            success = true;
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }

        return success;
    }

    private void createBackups(String server_path, String plugins_path) {

        String working_dir = System.getProperty("user.dir");
        File autoplug_backups = new File(working_dir+"/autoplug-backups");
        File autoplug_backups_server = new File(working_dir+"/autoplug-backups/server");
        File autoplug_backups_plugins = new File(working_dir+"/autoplug-backups/plugins");

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
        String formattedDate = date.format(formatter);
        String server_backup_dest = autoplug_backups_server.getAbsolutePath()+"/server-backup-"+formattedDate+".zip";
        String plugins_backup_dest = autoplug_backups_plugins.getAbsolutePath()+"/plugins-backup-"+formattedDate+".zip";

        Settings settings = new Settings();

        int max_days_server = settings.getBackupServerMaxDays();
        int max_days_plugins = settings.getBackupPluginsMaxDays();

        //Removes files older than user defined days
        if (max_days_server<=0) {logger.global_info(" Skipping delete of older server backups...");}
        else{
            logger.global_info(" Scanning for server backups older than "+max_days_server+" days...");

            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_server); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_server, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            logger.global_info(" Removed " +deleted_files+ " zips.");
        }

        if (max_days_plugins<=0) {logger.global_info(" Skipping delete of older plugin backups...");}
        else{
            logger.global_info(" Scanning for plugins backups older than "+max_days_plugins+" days...");

            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_plugins); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_plugins, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            logger.global_info(" Removed " +deleted_files+ " zips.");
        }


        if (settings.isBackupServer()) {
            logger.global_info(" Creating server backup...");

            try {
                new ZipFile(server_backup_dest).addFile(new File(server_path));
            } catch (ZipException e) {
                e.printStackTrace();
            }

            logger.global_info(" Created server backup at:" + server_path);
        } else{
            logger.global_info(" Skipping backup-server...");
        }
        if (settings.isBackupPlugins()){
            logger.global_info(" Creating plugins backup...");

            try {
                new ZipFile(plugins_backup_dest).addFolder(new File(plugins_path));
            } catch (ZipException e) {
                e.printStackTrace();
            }

            logger.global_info(" Created plugins backup at:" + plugins_path);
        } else{
            logger.global_info(" Skipping backup-plugins...");
        }



    }

    private void updateServer(File server) {

        Settings settings = new Settings();
        if (settings.isServer_check()){

            logger.global_info(" Searching for updates...");
            new UpdateServerJar(server);

        } else {
            logger.global_info(" Skipping server-check...");
        }

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
            logger.global_info(" Server was stopped!");
            logger.global_info(" To close this console enter .stop");
            logger.global_info(" For all commands enter .help");
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

            logger.global_info(" Found server jar at: " + inputPath.toAbsolutePath());
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
