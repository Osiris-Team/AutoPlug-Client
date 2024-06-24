package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.console.Commands;
import com.osiris.autoplug.client.network.online.connections.ConAutoPlugConsoleSend;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.LogFileWriter;
import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.Server;

import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SSHServerConsoleReceive implements Command {

    private static final Set<SSHServerConsoleReceive> activeConnections = ConcurrentHashMap.newKeySet();

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback exitCallback;
    private ExecutorService executor;

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.exitCallback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
        AL.info("SSH session started.");
        executor = Executors.newSingleThreadExecutor();
        out.write("Welcome to AutoPlug SSH Console!\r\n".getBytes(StandardCharsets.UTF_8));
        out.flush();

        activeConnections.add(this);

        executor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder commandBuffer = new StringBuilder();
                int c;
                while ((c = reader.read()) != -1) {
                    if (c == '\r' || c == '\n') { 
                        if (commandBuffer.length() > 0) {
                            String userInput = commandBuffer.toString().trim();
                            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            AL.info("Received SSH command: " + userInput);
                            commandBuffer.setLength(0);
                            handleUserInput(userInput);
                        }
                    } else if (c == 3) {
                        exitCallback.onExit(0, "Connection closed by user.");
                        break;
                    } else if (c == 127 || c == 8) {
                        if (commandBuffer.length() > 0) {
                            commandBuffer.setLength(commandBuffer.length() - 1);
                            out.write("\b \b".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        }
                    } else {
                        commandBuffer.append((char) c);
                        out.write(c);
                        out.flush();
                    }
                }
            } catch (IOException e) {
                AL.warn(e);
            } finally {
                try {
                    out.flush();
                } catch (IOException e) {
                    AL.warn(e);
                } finally {
                    activeConnections.remove(this);
                    exitCallback.onExit(0);
                }
            }
        });
    }

    private void handleUserInput(String userInput) {
        try {
            boolean isAutoPlugCommand = Commands.execute(userInput);
            if (isAutoPlugCommand) {
                try {
                    LogFileWriter.writeToLog(userInput);
                } catch (Exception e) {
                    AL.warn(e, "Failed to write command to log file.");
                }
            } else {
                if (Server.isRunning()) {
                    Server.submitCommand(userInput);
                } else {
                    AL.warn("Failed to submit command '" + userInput + "' because server is not running!");
                }
            }

        } catch (Exception e) {
            AL.warn(e);
        }
    }

    public void broadcast(String message) {
        try {
            out.write((message).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            AL.warn(e);
        }
    }

    @Override
    public void destroy(ChannelSession channel) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        activeConnections.remove(this);
    }

    public static void broadcastToAll(String message) { // this is for sending messages to all active connections
        for (SSHServerConsoleReceive connection : activeConnections) {
            if (!message.toString().endsWith("\n")) {
                message += "\n";
            }
            connection.broadcast(message);
        }
    }
}
