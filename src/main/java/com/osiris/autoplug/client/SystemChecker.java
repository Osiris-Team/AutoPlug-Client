package com.osiris.autoplug.client;

import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.tasks.scheduler.JobScheduler;
import com.osiris.autoplug.core.logger.AL;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class SystemChecker {

    public void checkReadWritePermissions() throws Exception {
        try {
            File test = new File(System.getProperty("user.dir") + "/read-write-test.txt");
            if (!test.exists()) {
                test.createNewFile();
            }
            test.delete();
        } catch (Exception e) {
            System.err.println("Make sure that this jar has read/write permissions!");
            throw e;
        }
    }

    public void checkInternetAccess() throws Exception {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://www.google.com").openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Failed to get code 200 from " + connection.getURL().toString());
            }
        } catch (Exception e) {
            System.err.println("Make sure that you have an internet connection!");
            throw e;
        }
    }

    /**
     * This enables AutoPlug to securely
     * shutdown and closes all open things.
     */
    public void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (Server.isRunning()) Server.stop();
            while (Server.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            }
            JobScheduler.safeShutdown();
            if (AL.isStarted) {
                AL.info("See you soon!");
                new AL().stop();
            } else {
                System.out.println("See you soon!");
            }
        }, "Shutdown-Thread"));
    }


    /**
     * Searches for missing files and adds them.
     */
    public void checkMissingFiles() {
        final File working_dir = new File(System.getProperty("user.dir"));
        final File plugins = new File(working_dir + "/plugins");
        final File autoplug_system = new File(working_dir + "/autoplug-system");
        final File autoplug_downloads = new File(working_dir + "/autoplug-downloads");
        final File autoplug_backups = new File(working_dir + "/autoplug-backups");
        final File autoplug_backups_server = new File(working_dir + "/autoplug-backups/server");
        final File autoplug_backups_plugins = new File(working_dir + "/autoplug-backups/plugins");
        final File autoplug_backups_worlds = new File(working_dir + "/autoplug-backups/worlds");
        final File autoplug_logs = new File(working_dir + "/autoplug-logs");

        List<File> directories = Arrays.asList(
                plugins,
                autoplug_downloads, autoplug_backups, autoplug_backups_server,
                autoplug_backups_plugins, autoplug_backups_worlds, autoplug_logs,
                autoplug_system);

        //Iterate through all directories and create missing ones
        for (File dir :
                directories) {
            if (!dir.exists()) {
                dir.mkdirs();
                System.out.println(" + Created directory: " + dir.getName());
            }
        }
    }
}
