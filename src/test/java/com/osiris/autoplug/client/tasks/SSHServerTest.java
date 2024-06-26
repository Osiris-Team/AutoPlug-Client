/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.osiris.autoplug.client.configs.SSHConfig;

public class SSHServerTest {

    private static SSHConfig sshConfig;

    @BeforeAll
    public static void setUp() throws Exception {
        sshConfig = new SSHConfig();
        sshConfig.validateValues();
    }

    @Test
    public void testSSHWithUserPass() throws Exception {
        if (sshConfig.auth_method.asString().contains("user-pass")) {
            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.start();

                ConnectFuture connectFuture = client.connect(sshConfig.username.asString(), "localhost", Integer.parseInt(sshConfig.port.asString()));
                connectFuture.await();

                try (ClientSession session = connectFuture.getSession()) {
                    session.addPasswordIdentity(sshConfig.password.asString());
                    AuthFuture authFuture = session.auth();
                    authFuture.await(5, TimeUnit.SECONDS);

                    if (authFuture.isSuccess()) {
                        System.out.println("Authentication with user-pass successful.");
                        // Send data and receive response
                        String response = sendData(session, ".testssh");
                        System.out.println("Received response: " + response);
                        if (response.endsWith("OK")) {
                            System.out.println("Received response ends with OK.");
                        } else {
                            System.out.println("Received response does not end with OK.");
                        }
                    } else {
                        System.out.println("Authentication with user-pass failed.");
                    }
                }
            }
        }
    }

    @Test
    public void testSSHWithKey() throws Exception {
        if (sshConfig.auth_method.asString().contains("key")) {
            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.start();

                ConnectFuture connectFuture = client.connect(sshConfig.username.asString(), "localhost", Integer.parseInt(sshConfig.port.asString()));
                connectFuture.await();

                try (ClientSession session = connectFuture.getSession()) {
                    KeyPair keyPair = loadKeyPair(sshConfig.server_private_key.asString());
                    session.addPublicKeyIdentity(keyPair);
                    AuthFuture authFuture = session.auth();
                    authFuture.await(5, TimeUnit.SECONDS);

                    if (authFuture.isSuccess()) {
                        System.out.println("Authentication with key successful.");
                        // Send data and receive response
                        String response = sendData(session, ".testssh");
                        System.out.println("Received response: " + response);
                        if (response.endsWith("OK")) {
                            System.out.println("Received response ends with OK.");
                        } else {
                            System.out.println("Received response does not end with OK.");
                        }
                    } else {
                        System.out.println("Authentication with key failed.");
                    }
                }
            }
        }
    }

    private String sendData(ClientSession session, String data) throws IOException {
        try (ByteArrayOutputStream responseStream = new ByteArrayOutputStream()) {
            ChannelExec channel = session.createExecChannel("cat"); // Use 'cat' to echo input back
            channel.setOut(responseStream);
            channel.setErr(responseStream);
            channel.open().verify(5, TimeUnit.SECONDS);

            try (OutputStreamWriter writer = new OutputStreamWriter(channel.getInvertedIn(), StandardCharsets.UTF_8)) {
                writer.write(data);
                writer.flush();
            }

            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(5));
            return responseStream.toString(StandardCharsets.UTF_8);
        }
    }

    private KeyPair loadKeyPair(String keyPath) throws IOException {
        try (InputStream keyStream = Files.newInputStream(Paths.get(keyPath))) {
            Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(null, () -> keyPath, keyStream, null);
            if (keyPairs.iterator().hasNext()) {
                return keyPairs.iterator().next();
            } else {
                throw new IOException("No key pairs found in the provided key path: " + keyPath);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to load key pair due to security exception", e);
        }
    }
}
