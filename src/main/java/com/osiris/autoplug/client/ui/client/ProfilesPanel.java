package com.osiris.autoplug.client.ui.client;

import com.osiris.autoplug.client.clientapp.NativeClientService;
import com.osiris.autoplug.client.profiles.*;
import com.osiris.autoplug.client.tasks.updater.mods.TaskModsUpdater;
import com.osiris.autoplug.client.tasks.updater.plugins.TaskPluginsUpdater;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class ProfilesPanel extends DashboardPanel {
    private final NativeClientService service;
    private final ProfileUpdater updater;
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Profile", "Type", "Minecraft", "Loader", "Template", "Directory"}, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    private final JTable table = new JTable(model);
    private List<Profile> profiles = new ArrayList<>();
    public ProfilesPanel(NativeClientService service) {
        this.service = service; updater = new ProfileUpdater(service.profiles.getCache());
        table.setRowHeight(32); table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader()); add(scroll, BorderLayout.CENTER);
        JButton create = new JButton("Create"), clone = new JButton("Clone / migrate"), remove = new JButton("Delete"), template = new JButton("Toggle template"), check = new JButton("Check updates"), update = new JButton("Apply updates"), launch = new JButton("Launch client"), refresh = new JButton("Refresh"), activate = new JButton("Use for default updates");
        create.addActionListener(event -> create()); clone.addActionListener(event -> cloneProfile());
        remove.addActionListener(event -> { Profile profile = selected(); if (profile != null && confirm("Delete profile '" + profile.id + "' and all its local files? Shared cached artifacts are retained.")) work("Deleting profile…", () -> { service.profiles.delete(profile.id); return service.profiles.list(); }, this::showProfiles); });
        template.addActionListener(event -> { Profile profile = selected(); if (profile != null) work("Saving template selection…", () -> { service.profiles.setTemplate(profile.id, !profile.isTemplate); return service.profiles.list(); }, this::showProfiles); });
        check.addActionListener(event -> updates(false)); update.addActionListener(event -> updates(true));
        launch.addActionListener(event -> { Profile profile = selected(); if (profile != null) work("Preparing Minecraft…", () -> service.launch(profile.id, null), process -> status.setText("Minecraft process started")); });
        refresh.addActionListener(event -> load());
        activate.addActionListener(event -> { Profile profile = selected(); if (profile != null) work("Selecting default update profile…", () -> service.profiles.setActive(profile.id), active -> status.setText("Default " + active.type + " updates use " + active.id)); });
        JPanel top = new JPanel(new BorderLayout(0, 8)); top.add(new JLabel("Isolated client/server mods and server plugins • Shared cached JARs"), BorderLayout.NORTH); top.add(buttons(create, clone, template, remove, refresh), BorderLayout.CENTER); add(top, BorderLayout.NORTH);
        JPanel bottom = new JPanel(new BorderLayout(0, 8)); bottom.add(buttons(check, update, launch, activate), BorderLayout.NORTH); bottom.add(status, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        try { showProfiles(service.profiles.list()); } catch (Exception exception) { status.setText("Could not read profiles: " + exception.getMessage()); }
    }
    private Profile selected() { int row = table.getSelectedRow(); if (row < 0) { status.setText("Select a profile first"); return null; } return profiles.get(table.convertRowIndexToModel(row)); }
    private void load() { work("Loading profiles…", () -> service.profiles.list(), this::showProfiles); }
    private void showProfiles(List<Profile> list) { profiles = list; model.setRowCount(0); for (Profile profile : list) model.addRow(new Object[]{profile.id, profile.type, profile.gameVersion, profile.loader, profile.isTemplate ? "Yes" : "No", profile.directoryPath}); }
    private ProfileUpdater.Plan check(Profile profile) throws Exception {
        return profile.type == Profile.Type.PLUGINS ? TaskPluginsUpdater.processProfile(profile, updater, false, false, ignored -> {}) : TaskModsUpdater.processProfile(profile, updater, false, false, ignored -> {});
    }
    private void updates(boolean apply) {
        Profile profile = selected(); if (profile == null) return;
        work("Resolving compatible releases…", () -> check(profile), plan -> {
            if (!apply) { JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(plan.summary(), 18, 65)), "Profile update plan", JOptionPane.INFORMATION_MESSAGE); return; }
            if (!plan.errors.isEmpty()) { JOptionPane.showMessageDialog(this, plan.summary(), "Cannot apply updates", JOptionPane.ERROR_MESSAGE); return; }
            if (confirm(plan.summary() + "\nApply this plan? Incompatible files listed as DISABLE become .jar.disabled.")) work("Applying checked profile updates…", () -> { updater.apply(plan, !plan.disabled().isEmpty()); return service.profiles.list(); }, this::showProfiles);
        });
    }
    private void create() {
        JTextField id = new JTextField(24), version = new JTextField("1.20.4", 24);
        JComboBox<Profile.Type> type = new JComboBox<>(Profile.Type.values());
        JComboBox<String> loader = new JComboBox<>(new String[]{"VANILLA", "FABRIC", "QUILT", "FORGE", "NEOFORGE"});
        type.addActionListener(event -> { loader.removeAllItems(); for (String value : type.getSelectedItem() == Profile.Type.PLUGINS ? new String[]{"SPIGOT", "PAPER", "PURPUR", "BUKKIT"} : new String[]{"VANILLA", "FABRIC", "QUILT", "FORGE", "NEOFORGE"}) loader.addItem(value); });
        JCheckBox template = new JCheckBox("Use as a base template"); JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.add(new JLabel("Unique profile ID")); form.add(id); form.add(new JLabel("Type")); form.add(type); form.add(new JLabel("Minecraft version")); form.add(version); form.add(new JLabel("Loader")); form.add(loader); form.add(template);
        if (JOptionPane.showConfirmDialog(this, form, "Create profile", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            String name = id.getText(), target = version.getText(), selectedLoader = (String)loader.getSelectedItem(); Profile.Type selectedType = (Profile.Type)type.getSelectedItem(); boolean isTemplate = template.isSelected();
            work("Creating isolated profile…", () -> { service.profiles.create(name, selectedType, target, selectedLoader, isTemplate); return service.profiles.list(); }, this::showProfiles);
        }
    }
    private void cloneProfile() {
        Profile source = selected(); if (source == null) return;
        String version = JOptionPane.showInputDialog(this, "Target Minecraft version", source.gameVersion); if (version == null) return;
        work("Cloning and resolving compatible releases…", () -> {
            Profile cloned = service.profiles.clone(source.id, version);
            try { ProfileUpdater.Plan plan = check(cloned); if (!plan.errors.isEmpty()) throw new IllegalStateException(plan.summary());
                if (!confirm(plan.summary() + "\nComplete migration? Incompatible files listed as DISABLE become .jar.disabled.")) { service.profiles.delete(cloned.id); return service.profiles.list(); }
                updater.apply(plan, !plan.disabled().isEmpty()); return service.profiles.list();
            } catch (Exception failure) { try { service.profiles.delete(cloned.id); } catch (Exception cleanup) { failure.addSuppressed(cleanup); } throw failure; }
        }, this::showProfiles);
    }
}
