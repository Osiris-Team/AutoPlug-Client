package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.ui.LauncherActions.WorldInfo;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Console and lifecycle controls always address the selected world's isolated AutoPlug instance. */
final class ManagedWorldPanel extends JPanel implements AutoCloseable {
    private final LauncherActions actions;
    private final Consumer<WorldInfo> start;
    private final Consumer<String> open;
    private final JComboBox<WorldInfo> worlds = new JComboBox<>();
    private final JTextArea output = new JTextArea();
    private final JTextField command = new JTextField();
    private final JLabel state = new JLabel("Choose a managed world");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "AutoPlug-world-console"); t.setDaemon(true); return t; });
    private final AtomicBoolean reading = new AtomicBoolean(), closed = new AtomicBoolean();
    private final Timer timer = new Timer(1800, e -> { if (isShowing()) refreshLog(); });
    private String wanted;
    private boolean updatingChoices;
    ManagedWorldPanel(LauncherActions actions, Consumer<WorldInfo> start, Consumer<String> open) {
        super(new BorderLayout(0, 10)); this.actions = actions; this.start = start; this.open = open;
        setName("managed-world-console"); setBorder(new EmptyBorder(12, 4, 4, 4)); setOpaque(false);
        worlds.setName("managed-world-selector"); worlds.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, "", i, selected, focus);
                label.putClientProperty("html.disable", Boolean.TRUE);
                if (value instanceof WorldInfo) { WorldInfo w = (WorldInfo) value; label.setText(w.name + (w.running ? " · Running" : " · Stopped")); }
                return label;
            }
        });
        JPanel top = new JPanel(new BorderLayout(8, 8)); top.setOpaque(false); top.add(worlds); top.add(button("Refresh", this::refresh), BorderLayout.EAST);
        JPanel controls = new JPanel(new GridLayout(2, 4, 6, 6)); controls.setOpaque(false);
        controls.add(button("Play / start", () -> { WorldInfo w = selected(); if (w != null) start.accept(w); }));
        controls.add(button("Stop", () -> action(w -> actions.stopWorld(w.id))));
        controls.add(button("Restart", () -> action(w -> actions.restartWorld(w.id))));
        controls.add(button("Back up", () -> send(".backup")));
        controls.add(button("AutoPlug help", () -> send(".help")));
        controls.add(button("Server status", () -> send("list")));
        controls.add(button("Configuration", () -> { WorldInfo w = selected(); if (w != null) open.accept(new java.io.File(w.directory, "autoplug").toString()); }));
        controls.add(button("World folder", () -> { WorldInfo w = selected(); if (w != null) open.accept(w.directory); }));
        top.add(controls, BorderLayout.SOUTH); add(top, BorderLayout.NORTH);
        output.setName("managed-world-output"); output.setEditable(false); output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setText("Each world has its own AutoPlug copy, console, configuration and server process.\nChoose a world and Play / start.\nServer and mod versions are managed through Profiles.");
        add(new JScrollPane(output));
        JPanel bottom = new JPanel(new BorderLayout(8, 6)); bottom.setOpaque(false);
        command.setName("managed-world-command"); command.putClientProperty("JTextField.placeholderText", "Server command or .help for AutoPlug commands");
        command.addActionListener(e -> submit()); bottom.add(command); bottom.add(button("Send", this::submit), BorderLayout.EAST);
        state.putClientProperty("html.disable", Boolean.TRUE); bottom.add(state, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        worlds.addActionListener(e -> { if (!updatingChoices) { output.setText(""); refreshLog(); } }); timer.start();
    }
    private JButton button(String text, Runnable action) { JButton b = new JButton(text); b.addActionListener(e -> action.run()); return b; }
    private WorldInfo selected() { return (WorldInfo) worlds.getSelectedItem(); }
    void select(String id) { wanted = id; refresh(); }
    void refresh() {
        submitWork(() -> { List<WorldInfo> result = new ArrayList<>(); for (WorldInfo w : actions.worlds()) if (!w.local) result.add(w); return result; }, result -> {
            String previous = wanted != null ? wanted : selected() == null ? null : selected().id;
            updatingChoices = true;
            try { worlds.removeAllItems(); for (WorldInfo w : result) { worlds.addItem(w); if (w.id.equals(previous)) worlds.setSelectedItem(w); } }
            finally { updatingChoices = false; }
            wanted = null; if (result.isEmpty()) state.setText("Create a managed world in Worlds to get started."); refreshLog();
        });
    }
    private void submit() { String value = command.getText().trim(); if (value.isEmpty()) return; send(value); command.setText(""); }
    private void send(String text) { action(w -> actions.serverCommand(w.id, text)); }
    private interface Operation { void run(WorldInfo world) throws Exception; }
    private void action(Operation operation) {
        WorldInfo world = selected(); if (world == null) return;
        state.setText("Working on " + world.name + "…");
        submitWork(() -> { operation.run(world); return world.id; }, id -> { if (selected() != null && selected().id.equals(id)) state.setText("Command completed"); refresh(); },
                e -> { if (selected() != null && selected().id.equals(world.id)) state.setText("Could not complete action: " + e.getMessage()); });
    }
    void refreshLog() {
        WorldInfo world = selected(); if (world == null || !reading.compareAndSet(false, true)) return;
        try { worker.submit(() -> {
            String text;
            try { text = actions.serverLog(world.id); } catch (Exception e) { text = "Console unavailable: " + e.getMessage(); }
            String log = text; SwingUtilities.invokeLater(() -> { reading.set(false); if (closed.get() || selected() == null) return;
                if (!selected().id.equals(world.id)) { refreshLog(); return; }
                if (!output.getText().equals(log)) { boolean follow = output.getCaretPosition() >= Math.max(0, output.getDocument().getLength() - 2); output.setText(log); if (follow) output.setCaretPosition(output.getDocument().getLength()); }
                state.setText(world.name + (world.running ? " · Running" : " · Stopped") + " · latest 64 KiB of console output");
            });
        }); } catch (RejectedExecutionException e) { reading.set(false); }
    }
    private <T> void submitWork(Callable<T> task, Consumer<T> success) {
        submitWork(task, success, e -> state.setText("Could not complete action: " + e.getMessage()));
    }
    private <T> void submitWork(Callable<T> task, Consumer<T> success, Consumer<Exception> failure) {
        if (closed.get()) return;
        try { worker.submit(() -> { try { T result = task.call(); SwingUtilities.invokeLater(() -> { if (!closed.get()) success.accept(result); }); }
            catch (Exception e) { SwingUtilities.invokeLater(() -> { if (!closed.get()) failure.accept(e); }); } }); }
        catch (RejectedExecutionException ignored) { }
    }
    @Override public void close() { closed.set(true); timer.stop(); worker.shutdownNow(); }
}
