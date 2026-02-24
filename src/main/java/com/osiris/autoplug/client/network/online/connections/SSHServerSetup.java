package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.SSHConfig;
import com.osiris.jlib.logger.AL;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import java.io.File;
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
        String allowedKeysRaw = sshConfig.allowed_keys_path.asString();
        String privateKeyRaw = sshConfig.server_private_key.asString();
        String username = sshConfig.username.asString();
        String password = sshConfig.password.asString();

        // === Defensive checks ===
        if (allowedKeysRaw == null || allowedKeysRaw.trim().isEmpty()) {
            AL.warn("SSH config error: 'allowed-keys-path' is missing or empty!");
            throw new IllegalArgumentException("Missing 'allowed-keys-path' in SSH config");
        }
        if (privateKeyRaw == null || privateKeyRaw.trim().isEmpty()) {
            AL.warn("SSH config error: 'server-private-key' is missing or empty! "
                    + "AutoPlug cannot start SSH without a valid key path.\n"
                    + "Example fix:\n  server-private-key: ./autoplug/server_host_key.ser");
            throw new IllegalArgumentException("Missing 'server-private-key' in SSH config");
        }

        Path allowedKeysPath = new File(allowedKeysRaw).toPath();
        Path serverPrivateKeyPath = new File(privateKeyRaw).toPath();

        try {
            setupServer(port, authMethod, allowedKeysPath, serverPrivateKeyPath, username, password);
            sshd.start();
            AL.info("SSH server started on port " + port + " with auth method: " + authMethod);
        } catch (Exception e) {
            AL.warn("Failed to start SSH server! Reason: " + e.getMessage(), e);
            stop(); // Ensure any partial initialization is cleaned up
        }
    }

    public static void stop() throws IOException {
        if (sshd != null) {
            sshd.close(false);
            AL.info("SSH server closed.");
            sshd = null;
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

        sshd.setCommandFactory((channel, command) -> new SSHServerConsoleReceive());
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