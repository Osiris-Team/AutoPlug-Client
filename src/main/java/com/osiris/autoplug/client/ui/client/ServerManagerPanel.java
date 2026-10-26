package com.osiris.autoplug.client.ui.client;

import com.osiris.autoplug.client.console.Commands;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.ui.ServerPanel;
import com.osiris.jlib.logger.AL;
import com.osiris.jlib.logger.MessageFormatter;
import javax.swing.*;
import java.awt.*;

/** Keeps existing task/configuration panels and server commands in the combined dashboard. */
public final class ServerManagerPanel extends DashboardPanel {
    private Runnable unsubscribe = () -> {};
    public ServerManagerPanel() throws Exception {
        JTextArea console = new JTextArea(20, 80); console.setEditable(false); console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        listen(AL.actionsOnMessageEvent, message -> {
            String text = MessageFormatter.formatForFile(message);
            SwingUtilities.invokeLater(() -> {
                console.append(text + "\n");
                if (console.getDocument().getLength() > 128 * 1024) console.replaceRange("", 0, console.getDocument().getLength() - 96 * 1024);
                console.setCaretPosition(console.getDocument().getLength());
            });
        });
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Console", new JScrollPane(console)); tabs.addTab("Existing updates and settings", new ServerPanel(tabs)); add(tabs, BorderLayout.CENTER);
        String[] names = {"Start", "Restart", "Stop", "Backup", "Run tasks"}, commands = {".start", ".restart", ".stop", ".backup", ".run tasks"};
        JButton[] controls = new JButton[names.length];
        for (int i = 0; i < controls.length; i++) { final String command = commands[i]; controls[i] = new JButton(names[i]); controls[i].addActionListener(event -> work("Running " + command + "…", () -> Commands.execute(command), handled -> status.setText("Command dispatched; see console"))); }
        add(buttons(controls), BorderLayout.NORTH);
        JTextField input = new JTextField(); input.setToolTipText("Server command or AutoPlug command (for example .check plugins)");
        JButton send = new JButton("Execute command"); Runnable execute = () -> { String command = input.getText().trim(); if (command.isEmpty()) return; input.setText(""); work("Executing command…", () -> { if (!Commands.execute(command)) Server.submitCommand(command); return true; }, handled -> status.setText("Command dispatched; see console")); };
        input.addActionListener(event -> execute.run()); send.addActionListener(event -> execute.run());
        JPanel commandRow = new JPanel(new BorderLayout(8, 0)); commandRow.add(input, BorderLayout.CENTER); commandRow.add(send, BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout(0, 8)); bottom.add(commandRow, BorderLayout.NORTH); bottom.add(status, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
    }
    private <T> void listen(java.util.List<com.osiris.jlib.events.MessageEvent<T>> listeners, com.osiris.jlib.events.MessageEvent<T> listener) { listeners.add(listener); unsubscribe = () -> listeners.remove(listener); }
    public void release() { unsubscribe.run(); unsubscribe = () -> {}; }
}
