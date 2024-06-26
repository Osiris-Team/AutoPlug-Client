/*
 * Copyright (c) 2021-2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import java.io.IOException;

import com.osiris.autoplug.client.configs.SSHConfig;
import com.osiris.autoplug.client.network.online.connections.SSHServerConsoleReceive;
import com.osiris.autoplug.client.network.online.connections.SSHServerSetup;
import com.osiris.autoplug.client.utils.ConsoleOutputCapturer;
import com.osiris.jlib.logger.AL;

public class SSHManager {
    private static Thread sshThread;
    private static Thread consoleCaptureThread;
    private static SSHServerSetup sshServerSetup;
    private static SSHConfig sshConfig;
    private static ConsoleOutputCapturer capturer;

    static {
        try {
            sshConfig = new SSHConfig();
            sshServerSetup = new SSHServerSetup();
            capturer = new ConsoleOutputCapturer();
            createThread();
            createConsoleCaptureThread();
        } catch (IOException e) {
            AL.warn("Failed to initialize SSHManager", e);
        } catch (Exception e) {
            AL.warn("Unexpected exception during SSHManager initialization", e);
        }
    }

    private static void createThread() throws IOException {
        sshThread = new Thread(() -> {
            try {
                sshServerSetup.start();
            } catch (IOException e) {
                AL.warn("IOException occurred while starting SSH server", e);
            } catch (Exception e) {
                AL.warn("Failed to start SSH server", e);
            }
        });
    }

    private static void createConsoleCaptureThread() {
        consoleCaptureThread = new Thread(() -> {
            capturer.start();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String newOutput = capturer.getNewOutput();
                    if (!newOutput.isEmpty()) {
                        SSHServerConsoleReceive.broadcastToAll(newOutput);
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public static synchronized boolean start() {
        AL.info("Starting SSH Server...");
        if (isRunning()) {
            AL.info("SSH Server is already running!");
            return true;
        }
        try {
            if (sshConfig.enabled.asBoolean()) {
                if (sshThread.getState() == Thread.State.NEW) {
                    sshThread.start();
                    consoleCaptureThread.start();
                } else if (sshThread.getState() == Thread.State.TERMINATED) {
                    createThread();
                    createConsoleCaptureThread();
                    sshThread.start();
                    consoleCaptureThread.start();
                }
                return true;
            } else {
                AL.info("SSH Server is disabled in the config!");
                return false;
            }
        } catch (Exception e) {
            AL.warn("Exception during SSH Server start!", e);
            return false;
        }
    }
    
    public static synchronized boolean stop(boolean is_final) {
        if (!isRunning()) {
            AL.info("SSH Server is not running!");
            return true;
        }
        try {
            capturer.stop();
            if (is_final) {
                AL.info("Closing SSH Server...");
                sshServerSetup.close();  // Use close instead of stop if final
            } else {
                AL.info("Stopping SSH Server...");
                sshServerSetup.stop();
            }
            sshThread.join();
            consoleCaptureThread.interrupt();
            consoleCaptureThread.join();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AL.warn("Thread interrupted while stopping SSH Server!", e);
        } catch (Exception e) {
            AL.warn("Failed to stop SSH Server!", e);
        }
        return false;
    }

    public static boolean isRunning() {
        return sshServerSetup.isRunning();
    }
}
