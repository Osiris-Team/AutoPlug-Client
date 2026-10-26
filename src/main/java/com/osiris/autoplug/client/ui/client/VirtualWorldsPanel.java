package com.osiris.autoplug.client.ui.client;

import com.osiris.autoplug.client.clientapp.*;
import com.osiris.autoplug.client.profiles.*;
import com.osiris.autoplug.client.worlds.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public final class VirtualWorldsPanel extends DashboardPanel {
    private final NativeClientService service;
    private final WorldClientContext context;
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"", "World", "Minecraft", "Server profile", "Client profile", "State"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
        public Class<?> getColumnClass(int column) { return column == 0 ? Icon.class : Object.class; }
    };
    private final JTable table = new JTable(model);
    private List<WorldDefinition> worlds = new ArrayList<>();
    private final javax.swing.Timer refresh;
    public VirtualWorldsPanel(NativeClientService service) throws java.io.IOException {
        this.service = service; context = WorldClientContext.of(service);
        table.setRowHeight(48); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setMaxWidth(48); table.getAccessibleContext().setAccessibleName("Virtual singleplayer worlds");
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() { public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focus, int row, int column) { putClientProperty("html.disable", Boolean.TRUE); return super.getTableCellRendererComponent(owner, value, selected, focus, row, column); } });
        JScrollPane scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader()); add(scroll, BorderLayout.CENTER);
        JButton create = new JButton("Create world"), client = new JButton("Choose client"), jar = new JButton("Server JAR"), eula = new JButton("Review EULA"), remove = new JButton("Delete");
        create.addActionListener(event -> create()); client.addActionListener(event -> chooseClient()); jar.addActionListener(event -> chooseJar()); eula.addActionListener(event -> eula()); remove.addActionListener(event -> remove());
        JPanel top = new JPanel(new BorderLayout(0, 8)); top.add(new JLabel("Play server plugins or server mods in an isolated local world"), BorderLayout.NORTH); top.add(buttons(create, client, jar, eula, remove), BorderLayout.CENTER); add(top, BorderLayout.NORTH);
        JButton play = new JButton("Play locally"), share = new JButton("Play & share"), copy = new JButton("Copy join address"), stop = new JButton("Stop world"), logs = new JButton("Server output");
        play.addActionListener(event -> launch(false)); share.addActionListener(event -> launch(true)); copy.addActionListener(event -> copyAddress());
        stop.addActionListener(event -> { WorldDefinition world = selected(); if (world != null) work("Stopping world…", () -> { VirtualWorldService.RunningWorld running = context.lifecycle.running(world.id); context.lifecycle.stop(world.id); return running; }, running -> { reload(); status.setText(running != null && running.cleanupFailure() != null ? "World stopped • " + running.cleanupFailure() : "World stopped"); }); });
        logs.addActionListener(event -> { WorldDefinition world = selected(); VirtualWorldService.RunningWorld running = world == null ? null : context.lifecycle.running(world.id); if (running != null) { JTextArea output = new JTextArea(String.join("\n", running.recentOutput()), 20, 70); output.setEditable(false); JOptionPane.showMessageDialog(this, new JScrollPane(output), "Local server output", JOptionPane.PLAIN_MESSAGE); } });
        JPanel bottom = new JPanel(new BorderLayout(0, 8)); bottom.add(buttons(play, share, copy, stop, logs), BorderLayout.NORTH); bottom.add(status, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        showWorlds(context.worlds.list()); refresh = new javax.swing.Timer(1500, event -> { if (isShowing()) reload(); }); refresh.start();
    }
    public void release() { refresh.stop(); }
    private WorldDefinition selected() { int row = table.getSelectedRow(); if (row < 0) { status.setText("Select a world first"); return null; } return worlds.get(table.convertRowIndexToModel(row)); }
    private void reload() { try { showWorlds(context.worlds.list()); } catch (Exception error) { status.setText("Could not read worlds: " + error.getMessage()); } }
    private void showWorlds(List<WorldDefinition> list) {
        String selected = table.getSelectedRow() >= 0 && table.getSelectedRow() < worlds.size() ? worlds.get(table.convertRowIndexToModel(table.getSelectedRow())).id : null;
        worlds = list; model.setRowCount(0);
        for (WorldDefinition world : worlds) { VirtualWorldService.RunningWorld running = context.lifecycle.running(world.id); String state = running != null && !running.isClosed() ? "Playing" : world.acceptedEula ? "Ready" : "Review EULA"; if (running != null && running.shareFailure() != null) state = "Playing; share failed"; model.addRow(new Object[]{thumbnail(world), world.name, world.gameVersion, world.profileId, world.clientProfileId == null ? world.profileType == Profile.Type.MODS_SERVER ? "Choose matching client" : "Automatic vanilla" : world.clientProfileId, state}); }
        if (selected != null) for (int row = 0; row < worlds.size(); row++) if (worlds.get(row).id.equals(selected)) { int view = table.convertRowIndexToView(row); table.setRowSelectionInterval(view, view); break; }
    }
    private static Icon thumbnail(WorldDefinition world) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB); Graphics2D draw = image.createGraphics(); draw.setColor(new Color(106, 173, 225)); draw.fillRect(0, 0, 32, 32); draw.setColor(new Color(91, 154, 65)); draw.fillRect(0, 13, 32, 7); draw.setColor(new Color(125, 91, 52)); draw.fillRect(0, 20, 32, 12); draw.setColor(Color.WHITE); draw.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15)); draw.drawString(world.name.substring(0, 1).toUpperCase(Locale.ROOT), 10, 26); draw.dispose(); return new ImageIcon(image);
    }
    private void create() {
        try { List<Profile> choices = new ArrayList<>(); for (Profile profile : service.profiles.list()) if (profile.type != Profile.Type.MODS) choices.add(profile); if (choices.isEmpty()) { status.setText("Create a PLUGINS or MODS_SERVER profile first"); return; }
            Profile profile = (Profile)JOptionPane.showInputDialog(this, "Server plugin/mod profile", "Create local world", JOptionPane.PLAIN_MESSAGE, null, choices.toArray(), choices.get(0)); if (profile == null) return;
            String name = JOptionPane.showInputDialog(this, "World name"); if (name != null) work("Creating world…", () -> { context.worlds.create(name, profile.id); return context.worlds.list(); }, this::showWorlds);
        } catch (Exception failure) { status.setText(failure.getMessage()); }
    }
    private void chooseClient() {
        WorldDefinition world = selected(); if (world == null) return;
        try { List<Profile> clients = new ArrayList<>(); for (Profile profile : service.profiles.list()) if (profile.type == Profile.Type.MODS && profile.gameVersion.equals(world.gameVersion) && (world.profileType != Profile.Type.MODS_SERVER || profile.loader.equals(world.loader))) clients.add(profile); if (clients.isEmpty()) { status.setText("Create a matching MODS client profile first"); return; }
            Profile profile = (Profile)JOptionPane.showInputDialog(this, "Matching client profile (server-only mods are never copied automatically)", "World client", JOptionPane.PLAIN_MESSAGE, null, clients.toArray(), clients.get(0)); if (profile != null) work("Saving client profile…", () -> { context.worlds.setClientProfile(world.id, profile.id); return context.worlds.list(); }, this::showWorlds);
        } catch (Exception failure) { status.setText(failure.getMessage()); }
    }
    private void chooseJar() { WorldDefinition world = selected(); if (world == null) return; JFileChooser chooser = new JFileChooser(); if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) work("Saving server executable…", () -> { context.worlds.setServerExecutable(world.id, chooser.getSelectedFile().toPath()); return context.worlds.list(); }, this::showWorlds); }
    private void eula() { WorldDefinition world = selected(); if (world != null && confirm("Review https://www.minecraft.net/en-us/eula before continuing.\nDo you accept the Minecraft EULA for this local world? This acceptance is saved only for this world.")) work("Saving your EULA choice…", () -> { context.worlds.acceptEula(world.id, true); return context.worlds.list(); }, this::showWorlds); }
    private void remove() { WorldDefinition world = selected(); if (world == null) return; if (context.lifecycle.isRunning(world.id)) { status.setText("Stop this world before deleting its save data"); return; } if (confirm("Permanently delete local world '" + world.name + "' and all its save data? The source profile and shared cache are retained.")) work("Deleting world…", () -> { context.worlds.delete(world.id); return context.worlds.list(); }, this::showWorlds); }
    private void launch(boolean share) {
        WorldDefinition world = selected(); if (world == null) return;
        if (share && !confirm("Play and share this world with friends?\nThe server will accept network connections and request a temporary TCP mapping from your router. Online Microsoft authentication is required. The mapping is removed when the world stops. An already running local-only world will be stopped and restarted.")) return;
        work(share ? "Preparing shared world…" : "Preparing local world…", () -> {
            VirtualWorldService.RunningWorld running = context.lifecycle.running(world.id);
            if (running != null && !running.isClosed()) { if (!share) return running; if (running.sharingEnabled) { context.lifecycle.share(world.id); return running; } running.close(); }
            return context.lifecycle.launch(world.id, share);
        }, running -> { reload(); status.setText(running.shareFailure() == null ? "World started • " + running.address : "Playing locally • " + running.shareFailure()); });
    }
    private void copyAddress() { WorldDefinition world = selected(); if (world == null) return; try { VirtualWorldService.RunningWorld running = context.lifecycle.running(world.id); String address = running == null ? null : running.publicAddress(); if (address == null) throw new IllegalStateException("Use Play & share first; no public mapping is active"); Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(address), null); status.setText("Copied " + address); } catch (Exception failure) { status.setText(failure.getMessage()); } }
}
