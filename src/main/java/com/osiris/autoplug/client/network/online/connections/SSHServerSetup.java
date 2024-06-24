/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */
package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.SSHConfig;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import com.osiris.jlib.logger.AL;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;

public class SSHServerSetup {

    private SshServer sshd;

    public void start() throws Exception {
        sshd = SshServer.setUpDefaultServer();

        SSHConfig sshConfig = new SSHConfig();

        boolean enabled = sshConfig.enabled.asBoolean();
        int port = sshConfig.port.asInt();
        String auth_method = sshConfig.auth_method.asString();
        String allowed_keys_path = sshConfig.allowed_keys_path.asString();
        String server_private_key = sshConfig.server_private_key.asString();
        String username = sshConfig.username.asString();
        String password = sshConfig.password.asString();

        if (!enabled) {
            AL.info("SSH server is disabled in the config file.");
            return;
        }

        sshd.setPort(port);

        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Path.of(server_private_key)));

        if (auth_method.equals("user-pass-only")) {
            sshd.setPasswordAuthenticator(getPasswordAuthenticator(username, password));
        } else if (auth_method.equals("key-only")) {
            sshd.setPublickeyAuthenticator(getPublickeyAuthenticator(Path.of(allowed_keys_path)));
        } else if (auth_method.equals("user-pass-key")) {
            sshd.setPasswordAuthenticator(getPasswordAuthenticator(username, password));
            sshd.setPublickeyAuthenticator(getPublickeyAuthenticator(Path.of(allowed_keys_path)));
        }

        sshd.setCommandFactory(new SSHServerConsoleFactory());
        sshd.setShellFactory(channel -> new SSHServerConsoleReceive());

        sshd.start();
        AL.info("SSH server started on port " + port + " with auth method: " + auth_method);
    }

    public void close() throws IOException {
        if (sshd != null) {
            sshd.close();
            AL.info("SSH server closed.");
        }
    }

    public void stop() throws IOException {
        if (sshd != null) {
            sshd.stop();
            AL.info("SSH server stopped.");
        }
    }

    public void restart() throws Exception {
        stop();
        start();
    }

    public boolean isRunning() {
        return sshd != null && sshd.isOpen();
    }

    public boolean isClosed() {
        return sshd == null || !sshd.isOpen();
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
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(line);
                    PublicKey authorizedKey = entry.resolvePublicKey(null, null);
                    if (KeyUtils.compareKeys(authorizedKey, key)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        };
    }
}
