package com.osiris.autoplug.client.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.loader.openssh.OpenSSHKeyPairResourceParser;
import org.apache.sshd.common.util.io.resource.PathResource;
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
                System.out.println("Connecting to SSH server...");

                ConnectFuture connectFuture = client.connect(sshConfig.username.asString(), "localhost", Integer.parseInt(sshConfig.port.asString()));
                connectFuture.await();

                try (ClientSession session = connectFuture.getSession()) {
                    session.addPasswordIdentity(sshConfig.password.asString());
                    AuthFuture authFuture = session.auth();
                    authFuture.await(5, TimeUnit.SECONDS);

                    if (authFuture.isSuccess()) {
                        System.out.println("Logged in with username " + sshConfig.username.asString() + " and password " + sshConfig.password.asString());
                        testCommandResponse(session);
                    } else {
                        System.err.println("Authentication with user-pass failed.");
                        System.err.println("Authentication result: " + authFuture.isDone() + ", " + authFuture.isSuccess());
                    }
                }
            } catch (Exception e) {
                System.err.println("Exception during SSH connection or authentication: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testSSHWithKey() throws Exception {
        if (sshConfig.auth_method.asString().contains("key")) {
            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.start();
                System.out.println("Connecting to SSH server...");

                ConnectFuture connectFuture = client.connect(sshConfig.username.asString(), "localhost", Integer.parseInt(sshConfig.port.asString()));
                connectFuture.await();

                try (ClientSession session = connectFuture.getSession()) {
                    KeyPair keyPair = loadKeyPair();
                    session.addPublicKeyIdentity(keyPair);
                    AuthFuture authFuture = session.auth();
                    authFuture.await(5, TimeUnit.SECONDS);

                    if (authFuture.isSuccess()) {
                        System.out.println("Logged in with public key for username " + sshConfig.username.asString());
                        testCommandResponse(session);
                    } else {
                        System.err.println("Authentication with key failed.");
                        System.err.println("Authentication result: " + authFuture.isDone() + ", " + authFuture.isSuccess());
                    }
                }
            } catch (Exception e) {
                System.err.println("Exception during SSH connection or authentication: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public String sendAndReceiveCommand(ClientSession session, String command) throws IOException {
        String response = "";
        try (ChannelExec channel = session.createExecChannel(command)) {
            try (ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
                 ByteArrayOutputStream errorStream = new ByteArrayOutputStream()) {

                channel.setOut(responseStream);
                channel.setErr(errorStream);
                channel.open().verify(5, TimeUnit.SECONDS);

                channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(5));

                String errorResponse = errorStream.toString(StandardCharsets.UTF_8);
                if (!errorResponse.isEmpty()) {
                    System.err.println("Error executing command: " + errorResponse);
                    return errorResponse;
                }

                response = responseStream.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Exception in sending command: " + e.getMessage());
            throw e;
        }
        return response;
    }

    private String stripAnsiCodes(String input) {
        if (input == null) return null;
        return input.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    public void testCommandResponse(ClientSession session) {
        try {
            String command = ".ping";
            String desiredResponse = "Pong!";
            String response = sendAndReceiveCommand(session, command);
            String cleanResponse = stripAnsiCodes(response);

            System.out.println(cleanResponse);

            if (cleanResponse.contains(desiredResponse)) {
                System.out.println("Test successful: Received response ends with " + desiredResponse);
            } else {
                System.out.println("Test failed: Received response does not end with " + desiredResponse);
            }
        } catch (IOException e) {
            System.err.println("Error during SSH command response test: " + e.getMessage());
        }
    }

    private KeyPair loadKeyPair() throws IOException, GeneralSecurityException {
        Path privateKeyPath = Paths.get("autoplug/test_key");

        if (!Files.exists(privateKeyPath)) {
            throw new IOException("Private key path does not exist: " + privateKeyPath);
        }

        Collection<KeyPair> keyPairs = OpenSSHKeyPairResourceParser.INSTANCE.loadKeyPairs(null, new PathResource(privateKeyPath), null);
        if (keyPairs.isEmpty()) {
            throw new GeneralSecurityException("No key pairs found in the specified private key file.");
        }

        return keyPairs.iterator().next();
    }
}
