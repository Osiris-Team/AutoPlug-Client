/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import com.osiris.autoplug.client.configs.SSHConfig;
import com.osiris.autoplug.client.network.online.connections.SSHServerConsoleReceive;
import com.osiris.autoplug.client.network.online.connections.SSHServerSetup;
import com.osiris.autoplug.client.utils.ConsoleOutputCapturer;
import com.osiris.jlib.logger.AL;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class SSHManager {
    @Nullable
    private static Thread sshThread;
    @Nullable
    private static Thread consoleCaptureThread;
    @Nullable
    private static ConsoleOutputCapturer capturer;

    private static void createSSHThread() {
        sshThread = new Thread(() -> {
            try {
                SSHServerSetup.start();
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

    public static synchronized boolean start(boolean force) {
        if (isRunning()) {
            AL.info("SSH Server is already running!");
            return true;
        }
        
        SSHConfig sshConfig;
        try {
            sshConfig = new SSHConfig();
            capturer = new ConsoleOutputCapturer();
        } catch (Exception e) {
            AL.warn("Failed to initialize components", e);
            return false;
        }

        try {
            if (sshConfig.enabled.asBoolean() || force) {
                createSSHThread();
                createConsoleCaptureThread();
                sshThread.start();
                consoleCaptureThread.start();
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            AL.warn("Exception during SSH Server start!", e);
            return false;
        }
    }

    public static boolean stop() {
        return stop(false);
    }
    
    public static synchronized boolean stop(boolean printInfo) {
        if (!isRunning()) {
            if(printInfo) AL.info("SSH Server is not running!");
            return true;
        }
        try {
            capturer.stop();
            SSHServerSetup.stop();
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
        return SSHServerSetup.isRunning();
    }
}
