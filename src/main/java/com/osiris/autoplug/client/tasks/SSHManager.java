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
    private static SSHManager instance;
    private Thread sshThread;
    private Thread consoleCaptureThread;
    private SSHServerSetup sshServerSetup;
    private SSHConfig sshConfig;
    private ConsoleOutputCapturer capturer;

    private SSHManager(SSHConfig config) throws IOException {
        this.sshConfig = config;
        this.sshServerSetup = new SSHServerSetup();
        this.capturer = new ConsoleOutputCapturer();
        createThread();
        createConsoleCaptureThread();
    }

    public static SSHManager getInstance(SSHConfig config) throws IOException {
        if (instance == null) {
            synchronized (SSHManager.class) {
                if (instance == null) {
                    instance = new SSHManager(config);
                }
            }
        }
        return instance;
    }

    private void createThread() throws IOException {
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

    private void createConsoleCaptureThread() {
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

    synchronized public boolean start() {
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

    synchronized public boolean stop() {
        AL.info("Stopping SSH Server...");
        if (!isRunning()) {
            AL.info("SSH Server is not running!");
            return true;
        }
        try {
            capturer.stop();  // Stop the capturer
            sshServerSetup.stop();
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

    public boolean isRunning() {
        return sshServerSetup.isRunning();
    }
}

