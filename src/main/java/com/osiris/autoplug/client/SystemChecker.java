/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client;

import com.osiris.autoplug.client.tasks.SSHManager;
import com.osiris.jlib.logger.AL;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

public class SystemChecker {


    public void checkReadWritePermissions() throws Exception {
        try {
            File test = new File(System.getProperty("user.dir") + "/read-write-test.txt");
            if (!test.exists()) {
                test.createNewFile();
            }

            // Test writing to file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(test))) {
                writer.write("Writing some random stuff, to test write permissions!");
                writer.flush();
            }

            // Test reading the file
            try (BufferedReader reader = new BufferedReader(new FileReader(test))) {
                reader.readLine();
            }

            test.delete();
        } catch (Exception e) {
            System.err.println("Make sure that this jar has read/write permissions!");
            throw e;
        }
    }

    public void checkInternetAccess() throws Exception {
        try {
            boolean reachable = InetAddress.getByName("www.google.com").isReachable(10000);
            if (!reachable) throw new Exception("Failed to reach www.google.com!");
        } catch (Exception e) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://www.google.com").openConnection();
                connection.connect();
                connection.disconnect();
            } catch (Exception ex) {
                System.err.println("Make sure that you have an internet connection!");
                throw ex;
            }
        }
    }

    /**
     * This enables AutoPlug to securely
     * shutdown and closes all open things.
     */
    public void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (Server.isRunning()) Server.stop();
            } catch (Exception e) {
                System.err.println("Error during shutdown, related to stopping the server!");
                e.printStackTrace();
                System.err.println("Error during shutdown, related to stopping the server!");
            }

            try {
                if (AL.isStarted)
                    SSHManager.stop();
            } catch (Exception e) {
                System.out.println("Error during shutdown, related to stopping the SSH server!");
                e.printStackTrace();
                System.out.println("Error during shutdown, related to stopping the SSH server!");
            }

            // Stop Logger last
            try {
                if (AL.isStarted) {
                    AL.info("See you soon!");
                    AL.stop();
                } else {
                    System.out.println("See you soon!");
                }
            } catch (Exception e) {
                System.err.println("Error during shutdown, related to the AutoPlug-Logger!");
                e.printStackTrace();
                System.err.println("Error during shutdown, related to the AutoPlug-Logger!");
            }
        }, "Shutdown-Thread"));
    }
}
