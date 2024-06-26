/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */
package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.SSHConfig;
import com.osiris.jlib.logger.AL;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.channel.ChannelSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;

public class SSHServerSetup {

    private static SshServer sshd;

    public static void start() throws Exception {
        if (sshd != null && sshd.isOpen()) {
            AL.warn("SSH server is already running.");
            return;
        }

        sshd = SshServer.setUpDefaultServer();
        SSHConfig sshConfig = new SSHConfig();

        int port = sshConfig.port.asInt();
        String authMethod = sshConfig.auth_method.asString();
        Path allowedKeysPath = Path.of(sshConfig.allowed_keys_path.asString());
        Path serverPrivateKeyPath = Path.of(sshConfig.server_private_key.asString());
        String username = sshConfig.username.asString();
        String password = sshConfig.password.asString();

        try {
            setupServer(port, authMethod, allowedKeysPath, serverPrivateKeyPath, username, password);
            sshd.start();
            AL.info("SSH server started on port " + port + " with auth method: " + authMethod);
        } catch (Exception e) {
            AL.warn("Failed to start SSH server!", e);
            stop(); // Ensure any partial initialization is cleaned up
        }
    }

    public static void stop() throws IOException {
        if (sshd != null) {
            try {
                sshd.stop(true); // Gracefully stop the server
                AL.info("SSH server stopped.");
            } catch (IOException e) {
                AL.warn("Failed to stop SSH server! Details: " + e.getMessage(), e);
                throw e;
            } finally {
                sshd = null; // Ensure sshd is set to null after stopping
            }
        } else {
            AL.warn("Attempted to stop SSH server, but it was already null.");
        }
    }

    public static void close() {
        if (sshd != null) {
            sshd.close(false);
            AL.info("SSH server closed.");
            sshd = null;
        } else {
            AL.warn("Attempted to close SSH server, but it was already null.");
        }
    }

    public static void restart() throws Exception {
        stop();
        start();
    }

    public static boolean channelActive() {
        return sshd != null && sshd.isOpen();
    }

    public static boolean isRunning() {
        boolean isNotNull = sshd != null;
        boolean isStarted = isNotNull && sshd.isStarted();
        return isNotNull && isStarted;
    }

    private static void setupServer(int port, String authMethod, Path allowedKeysPath, Path serverPrivateKeyPath, String username, String password) {
        sshd.setPort(port);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(serverPrivateKeyPath));

        switch (authMethod) {
            case "user-pass-only":
                sshd.setPasswordAuthenticator(getPasswordAuthenticator(username, password));
                break;
            case "key-only":
                sshd.setPublickeyAuthenticator(getPublickeyAuthenticator(allowedKeysPath));
                break;
            case "user-pass-key":
                sshd.setPasswordAuthenticator(getPasswordAuthenticator(username, password));
                sshd.setPublickeyAuthenticator(getPublickeyAuthenticator(allowedKeysPath));
                break;
            default:
                throw new IllegalArgumentException("Invalid authentication method: " + authMethod);
        }

        sshd.setCommandFactory(new CommandFactory() {
            @Override
            public Command createCommand(ChannelSession channel, String command) {
                return new SSHServerConsoleReceive();
            }
        });
        sshd.setShellFactory(channel -> new SSHServerConsoleReceive());
    }

    private static PasswordAuthenticator getPasswordAuthenticator(String username, String password) {
        return (inputUsername, inputPassword, session) ->
                username.equals(inputUsername) && password.equals(inputPassword);
    }

    private static PublickeyAuthenticator getPublickeyAuthenticator(Path authorizedKeysPath) {
        return (username, key, session) -> {
            try {
                List<String> lines = Files.readAllLines(authorizedKeysPath);
                for (String line : lines) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(line);
                    PublicKey authorizedKey = entry.resolvePublicKey(null, null);
                    if (KeyUtils.compareKeys(authorizedKey, key)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                AL.warn("Error reading authorized keys: " + e.getMessage());
            }
            return false;
        };
    }
}
