package com.osiris.autoplug.client.ui.client;

import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.clientapp.NativeClientService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public final class ServerBrowserPanel extends DashboardPanel {
    private final NativeClientService service;
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Server", "Address", "Version", "Players", "Latency", "MOTD", "Local loader"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<SavedServer> servers = new ArrayList<>();
    public ServerBrowserPanel(NativeClientService service) {
        this.service = service;
        table.setRowHeight(32); table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getAccessibleContext().setAccessibleName("Saved Minecraft servers");
        JScrollPane scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader()); add(scroll, BorderLayout.CENTER);
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focus, int row, int column) {
                putClientProperty("html.disable", Boolean.TRUE);
                return super.getTableCellRendererComponent(owner, value, selected, focus, row, column);
            }
        });
        JButton add = new JButton("Add server"), edit = new JButton("Edit"), remove = new JButton("Remove"), refresh = new JButton("Refresh status"), play = new JButton("Play"), importBtn = new JButton("Import Minecraft list"), export = new JButton("Export to Minecraft");
        add.addActionListener(event -> editServer(null)); edit.addActionListener(event -> { SavedServer selected = selected(); if (selected != null) editServer(selected); });
        remove.addActionListener(event -> { SavedServer selected = selected(); if (selected != null && confirm("Remove " + selected.name + " from AutoPlug's saved servers?")) work("Saving servers…", () -> service.servers.remove(selected), this::showServers); });
        refresh.addActionListener(event -> refresh());
        play.addActionListener(event -> { SavedServer selected = selected(); if (selected != null) work("Resolving profile and preparing Minecraft…", () -> {
            ServerStatus ping = ServerStatus.ping(selected); if (!ping.online) throw new IllegalStateException("Server is unavailable: " + ping.failure);
            return service.autoPlay(ping, this::confirm);
        }, process -> status.setText(process == null ? "Play cancelled" : "Minecraft process started")); });
        importBtn.addActionListener(event -> { JFileChooser chooser = chooser(); if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) work("Importing servers…", () -> { int imported = service.servers.importVanilla(chooser.getSelectedFile().toPath()); return imported; }, count -> { status.setText("Imported " + count + " servers"); load(); }); });
        export.addActionListener(event -> { JFileChooser chooser = chooser(); if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION && confirm("Replace the chosen Minecraft servers.dat with this list? A backup is created if it exists. Retained icons and other tags are preserved.")) work("Exporting servers…", () -> service.servers.exportVanilla(chooser.getSelectedFile().toPath()), backup -> status.setText(backup == null ? "Exported list" : "Exported; backup: " + backup.getFileName())); });
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(new JLabel("Saved servers • Play matches a local profile or clones and migrates a template"), BorderLayout.NORTH);
        top.add(buttons(add, edit, remove, refresh, play), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        JPanel bottom = new JPanel(new BorderLayout(0, 8)); bottom.add(buttons(importBtn, export), BorderLayout.NORTH); bottom.add(status, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        // No automatic network access on construction: server status is user initiated.
        try { showServers(service.servers.list()); } catch (Exception exception) { status.setText("Could not read servers: " + exception.getMessage()); }
    }
    private JFileChooser chooser() { JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(ServerCatalog.vanillaPath().toFile()); return chooser; }
    private SavedServer selected() { int row = table.getSelectedRow(); if (row < 0) { status.setText("Select a server first"); return null; } return servers.get(table.convertRowIndexToModel(row)); }
    private void load() { work("Loading servers…", () -> service.servers.list(), this::showServers); }
    /** Tray opening imports saved data without initiating status pings or changing the vanilla file. */
    public void importDefaultIfPresent() {
        Path vanilla = ServerCatalog.vanillaPath();
        if (java.nio.file.Files.isRegularFile(vanilla)) work("Importing your Minecraft server list…", () -> { service.servers.importVanilla(vanilla); return service.servers.list(); }, this::showServers);
    }
    private void showServers(List<SavedServer> list) { servers = list; model.setRowCount(0); for (SavedServer server : list) model.addRow(new Object[]{server.name, server.address, "Not checked", "—", "—", "—", server.loaderHint}); }
    private void refresh() {
        work("Reloading saved servers…", () -> service.servers.list(), latest -> { showServers(latest); refreshStatuses(new ArrayList<>(latest)); });
    }
    private void refreshStatuses(List<SavedServer> snapshot) {
        work("Checking server status…", () -> { List<ServerStatus> result = new ArrayList<>(); for (SavedServer server : snapshot) result.add(ServerStatus.ping(server)); return result; }, statuses -> {
            if (!servers.equals(snapshot)) return;
            for (int row = 0; row < statuses.size(); row++) {
                ServerStatus ping = statuses.get(row);
                model.setValueAt(ping.online ? ping.version + " (" + ping.protocol + ")" : ping.failure, row, 2);
                model.setValueAt(ping.online ? ping.players + "/" + ping.capacity : "—", row, 3);
                model.setValueAt(ping.online ? ping.latency + " ms" : "—", row, 4);
                model.setValueAt(ping.online ? ping.motd : "Unavailable", row, 5);
            }
        });
    }
    private void editServer(SavedServer previous) {
        JTextField name = new JTextField(previous == null ? "" : previous.name, 24), address = new JTextField(previous == null ? "" : previous.address.toString(), 24);
        JComboBox<String> loader = new JComboBox<>(new String[]{"VANILLA", "FABRIC", "QUILT", "FORGE", "NEOFORGE"}); loader.setSelectedItem(previous == null ? "VANILLA" : previous.loaderHint);
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6)); form.add(new JLabel("Name")); form.add(name); form.add(new JLabel("Address (host:port or [IPv6]:port)")); form.add(address); form.add(new JLabel("Local loader choice (server pings do not report mods)")); form.add(loader);
        if (JOptionPane.showConfirmDialog(this, form, previous == null ? "Add server" : "Edit server", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try { SavedServer server = new SavedServer(name.getText(), ServerAddress.parse(address.getText()), (String)loader.getSelectedItem()); work("Saving server…", () -> service.servers.edit(previous, server), this::showServers); }
        catch (Exception exception) { JOptionPane.showMessageDialog(this, exception.getMessage(), "Invalid server", JOptionPane.ERROR_MESSAGE); }
    }
}
