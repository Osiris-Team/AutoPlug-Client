/* Copyright (c) 2022-2026 Osiris-Team. Licensed under the MIT License. */
package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.console.Commands;
import com.osiris.autoplug.client.ui.utils.HintTextField;
import com.osiris.autoplug.client.utils.io.AsyncInputStream;
import com.osiris.jlib.events.MessageEvent;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.Message;
import com.osiris.jlib.logger.MessageFormatter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Bounded console for AutoPlug task messages and the current dedicated server output. */
public class ServerConsolePanel extends JPanel implements AutoCloseable {
    public final JLabel labelConsole = new JLabel("Console");
    public final JTextArea txtConsole = new JTextArea();
    public final HintTextField txtSendCommand = new HintTextField("Send command…");
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final MessageEvent<Message> logListener = message -> append(MessageFormatter.formatForFile(message));
    private final Consumer<String> serverListener = this::append;
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "AutoPlug-ConsoleCommands"); t.setDaemon(true); return t; });
    private final Timer timer;
    private AsyncInputStream observed;
    private volatile boolean closed;

    public ServerConsolePanel(Container parent) {
        super(new BorderLayout(0, 8)); setBorder(new EmptyBorder(12, 12, 12, 12));
        txtConsole.setEditable(false); txtConsole.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(txtConsole), BorderLayout.CENTER);
        JPanel command = new JPanel(new BorderLayout(8, 0)); command.add(txtSendCommand, BorderLayout.CENTER);
        JButton execute = new JButton("Send"); execute.addActionListener(e -> submit()); txtSendCommand.addActionListener(e -> submit());
        command.add(execute, BorderLayout.EAST); add(command, BorderLayout.SOUTH);
        AL.actionsOnMessageEvent.add(logListener);
        timer = new Timer(150, e -> drain()); timer.start();
    }
    public static void executeCommand(String command) throws Exception {
        if (command == null || command.trim().isEmpty()) return;
        if (!Commands.execute(command)) Server.submitCommand(command);
    }
    private void submit() {
        String command = txtSendCommand.getText(); if (command.trim().isEmpty() || closed) return;
        txtSendCommand.setText(""); append("> " + command);
        commandExecutor.submit(() -> { try { executeCommand(command); } catch (Exception e) { append("Command failed: " + e.getMessage()); } });
    }
    private void append(String text) {
        if (closed || text == null) return;
        if (queued.incrementAndGet() > 1000) { queued.decrementAndGet(); return; }
        if (text.length() > 16384) text = text.substring(0, 16384) + " [line truncated]";
        pending.add(text.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", ""));
    }
    private void drain() {
        if (closed) return;
        AsyncInputStream current = Server.ASYNC_SERVER_IN;
        if (observed != current) {
            if (observed != null) observed.listeners.remove(serverListener);
            observed = current; if (observed != null) observed.listeners.add(serverListener);
        }
        StringBuilder batch = new StringBuilder(); String line;
        while ((line = pending.poll()) != null) { queued.decrementAndGet(); batch.append(line).append('\n'); }
        if (batch.length() == 0) return;
        txtConsole.append(batch.toString());
        int excess = txtConsole.getDocument().getLength() - 250000;
        if (excess > 0) try { txtConsole.getDocument().remove(0, excess); } catch (javax.swing.text.BadLocationException ignored) {}
        txtConsole.setCaretPosition(txtConsole.getDocument().getLength());
    }
    @Override public void close() {
        closed = true; timer.stop(); AL.actionsOnMessageEvent.remove(logListener);
        if (observed != null) observed.listeners.remove(serverListener); observed = null;
        pending.clear(); queued.set(0); commandExecutor.shutdownNow();
    }
}
