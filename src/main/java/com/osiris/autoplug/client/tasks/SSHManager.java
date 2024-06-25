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
import com.osiris.autoplug.client.network.online.connections.SSHServerSetup;
import com.osiris.jlib.logger.AL;

public class SSHManager {
    private static SSHManager instance;
    private Thread sshThread;
    private SSHServerSetup sshServerSetup;
    private SSHConfig sshConfig;

    private SSHManager(SSHConfig config) throws IOException {
        this.sshConfig = config;
        this.sshServerSetup = new SSHServerSetup();
        createThread();
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

    public boolean start() {
        AL.info("Starting SSH Server...");
        if (isRunning()) {
            AL.info("SSH Server is already running!");
            return true;
        }
        try {
            if (sshConfig.enabled.asBoolean()) {
                if (sshThread.getState() == Thread.State.NEW) {
                    sshThread.start();
                } else if (sshThread.getState() == Thread.State.TERMINATED) {
                    createThread();
                    sshThread.start();
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

    public boolean stop() {
        AL.info("Stopping SSH Server...");
        if (!isRunning()) {
            AL.info("SSH Server is not running!");
            return true;
        }
        try {
            sshServerSetup.stop();
            sshThread.join();
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
