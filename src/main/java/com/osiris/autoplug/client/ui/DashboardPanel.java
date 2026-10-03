package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.ui.LauncherActions.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Five-view Swing dashboard. Network, launcher and disk operations never run on the EDT. */
public final class DashboardPanel extends JPanel implements AutoCloseable {
    private final LauncherActions actions;
    private final ServerBrowserService browser;
    private final ExecutorService workers = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "AutoPlug-Dashboard"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger running = new AtomicInteger();
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final JLabel status = new JLabel("Ready");
    private final DefaultTableModel serverModel = model("Name", "Address", "Status", "MOTD", "Players", "Version", "Latency");
    private final JTable serverTable = table(serverModel);
    private final DefaultTableModel profileModel = model("Name", "Minecraft", "Loader", "Type", "Template", "Ready");
    private final JTable profileTable = table(profileModel);
    private final JPanel worldCards = new JPanel();
    private final Map<String, ServerStatus> serverStatuses = new HashMap<>();
    private List<SavedServer> servers = Collections.emptyList();
    private List<ProfileInfo> profiles = Collections.emptyList();
    private List<WorldInfo> worlds = Collections.emptyList();
    private int pingGeneration;
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[]{"All profiles", "MODS", "PLUGINS", "MODS_SERVER"});
    private List<ProfileInfo> visibleProfiles = Collections.emptyList();
    private final JTextArea profileDetails = textArea(3);
    private final JTextField java8 = new JTextField(), java17 = new JTextField(), java21 = new JTextField();
    private final JTextField extraJava = new JTextField(), clientId = new JTextField(), offlineName = new JTextField();
    private final JComboBox<ProfileInfo> defaultProfile = new JComboBox<>();
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(25565, 1, 65535, 1));
    private final JCheckBox upnp = new JCheckBox("Use UPnP when I explicitly share a world");
    private final JCheckBox rememberAccount = new JCheckBox("Remember Microsoft account on this computer");
    private final JLabel account = new JLabel("Loading account…");
    private ServerConsolePanel console;
    private SettingsInfo loadedSettings;
    private JDialog signInDialog;

    public DashboardPanel(LauncherActions actions, ServerBrowserService browser) { this(actions, browser, true); }

    /** The legacy controls can be omitted for a standalone preview without initializing a server. */
    public DashboardPanel(LauncherActions actions, ServerBrowserService browser, boolean includeLegacyControls) {
        super(new BorderLayout(0, 0));
        this.actions = Objects.requireNonNull(actions); this.browser = Objects.requireNonNull(browser);
        setPreferredSize(new Dimension(1100, 720));
        JPanel navigation = new JPanel(); navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBorder(new EmptyBorder(24, 16, 20, 16)); navigation.setPreferredSize(new Dimension(195, 600));
        JLabel brand = new JLabel("AutoPlug"); brand.setFont(brand.getFont().deriveFont(Font.BOLD, 25f));
        navigation.add(brand); navigation.add(Box.createVerticalStrut(5));
        JLabel subtitle = new JLabel("PLAY · MANAGE · SHARE"); subtitle.setFont(subtitle.getFont().deriveFont(10f));
        navigation.add(subtitle); navigation.add(Box.createVerticalStrut(30));
        ButtonGroup group = new ButtonGroup();
        String[] names = {"Server Browser", "Virtual Worlds", "Profiles", "Server Manager", "Settings"};
        JPanel[] views = {serverPage(), worldsPage(), profilesPage(), managerPage(includeLegacyControls), settingsPage()};
        for (int i = 0; i < names.length; i++) {
            String name = names[i]; pages.add(views[i], name);
            JToggleButton button = new JToggleButton(name); button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setPreferredSize(new Dimension(170, 43));
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43)); button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(e -> { cards.show(pages, name); if (name.equals("Virtual Worlds")) refreshWorlds(); });
            group.add(button); navigation.add(button); navigation.add(Box.createVerticalStrut(8));
            if (i == 0) button.setSelected(true);
        }
        navigation.add(Box.createVerticalGlue());
        JLabel footer = new JLabel("Your worlds. Your profiles."); footer.setFont(footer.getFont().deriveFont(11f)); navigation.add(footer);
        add(navigation, BorderLayout.WEST); add(pages, BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(8, 16, 8, 16)); add(status, BorderLayout.SOUTH);
        refreshProfiles(); refreshWorlds(); refreshSettings();
        run("Importing Minecraft favorites", () -> { browser.importVanilla(); return browser.list(); }, this::showServers);
    }

    private JPanel serverPage() {
        JPanel page = page("Server Browser", "Your Minecraft favorites, with live status and matching local profiles.");
        JPanel content = content();
        content.add(toolbar(button("Add server", this::addServer), button("Import Minecraft", () -> run("Importing favorites", () -> {
            int count = browser.importVanilla(); return count;
        }, count -> { status.setText("Imported " + count + " new favorites from " + browser.vanillaFile()); refreshServers(); })),
                button("Refresh status", this::refreshServers), button("Join selected", this::joinSelected),
                button("Remove", this::removeServer)), BorderLayout.NORTH);
        serverTable.getColumnModel().getColumn(3).setPreferredWidth(220);
        content.add(tableScroll(serverTable), BorderLayout.CENTER);
        JLabel note = new JLabel("Server pings show version and players. Mods and loaders come from your local profiles.");
        note.setBorder(new EmptyBorder(12, 0, 0, 0)); content.add(note, BorderLayout.SOUTH);
        page.add(content, BorderLayout.CENTER); return page;
    }

    private void addServer() {
        JTextField name = new JTextField(), address = new JTextField();
        if (!form("Add a Minecraft server", fields("Name", name, "Address", address))) return;
        try {
            SavedServer server = new SavedServer(name.getText(), address.getText());
            run("Saving favorite", () -> { browser.add(server.name, server.address); return browser.list(); }, this::showServers);
        } catch (IllegalArgumentException e) { error(e); }
    }
    private void removeServer() {
        SavedServer selected = selectedServer(); if (selected == null) return;
        if (!confirm("Remove favorite", "Remove “" + selected.name + "” from AutoPlug favorites?\nMinecraft's servers.dat remains unchanged.")) return;
        run("Removing favorite", () -> { browser.remove(selected.address); return browser.list(); }, this::showServers);
    }
    private void refreshServers() { run("Loading favorites", browser::list, this::showServers); }
    private void showServers(List<SavedServer> result) {
        int generation = ++pingGeneration;
        servers = result; serverModel.setRowCount(0);
        for (SavedServer server : servers) serverModel.addRow(new Object[]{server.name, server.address, "Checking…", "", "", "", ""});
        for (SavedServer server : servers) run("Checking " + server.name, () -> browser.ping(server), ping -> {
            if (generation != pingGeneration) return;
            serverStatuses.put(server.address, ping);
            for (int i = 0; i < servers.size(); i++) if (servers.get(i).address.equals(server.address)) {
                serverModel.setValueAt(ping.online ? "Online" : "Unavailable", i, 2);
                serverModel.setValueAt(ping.online && ping.motd != null ? ping.motd.replace('\n', ' ') : ping.message, i, 3);
                serverModel.setValueAt(ping.online ? ping.players + " / " + ping.capacity : "—", i, 4);
                serverModel.setValueAt(ping.online ? ping.version : "—", i, 5);
                serverModel.setValueAt(ping.online ? ping.latency + " ms" : "—", i, 6);
            }
        });
    }
    private SavedServer selectedServer() {
        int row = serverTable.getSelectedRow();
        if (row < 0) { status.setText("Select a server first."); return null; }
        return servers.get(serverTable.convertRowIndexToModel(row));
    }
    private void joinSelected() {
        SavedServer server = selectedServer(); if (server == null) return;
        ServerStatus ping = serverStatuses.get(server.address);
        List<ProfileInfo> clients = clientProfiles();
        if (clients.isEmpty()) { information("Create a client profile", "Create a MODS profile in Profiles before joining a server."); return; }
        ProfileInfo base = preferredBase(clients);
        String targetVersion = ping != null && ping.online ? gameVersionFromStatus(ping.version) : "";
        if (ping != null && ping.online) {
            ProfileInfo match = matchingClientProfile(clients, targetVersion, base.loader);
            if (match != null) { launchOnServer(server, match); return; }
            JComboBox<ProfileInfo> baseChoice = new JComboBox<>(clients.toArray(new ProfileInfo[0])); baseChoice.setSelectedItem(base);
            JTextField target = new JTextField(targetVersion);
            JPanel details = fields("Server version", new JLabel(ping.version), "Base profile / loader", baseChoice, "Target Minecraft version", target);
            Object[] options = {"Clone and join", "Choose existing", "Cancel"};
            int option = JOptionPane.showOptionDialog(this, details, "No matching " + base.loader + " profile", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (option == 0) {
                ProfileInfo selectedBase = (ProfileInfo) baseChoice.getSelectedItem(); String version = target.getText().trim();
                if (selectedBase == null || version.isEmpty()) return;
                String name = selectedBase.name + " " + version;
                run("Preparing a matching profile", () -> actions.cloneProfile(selectedBase.id, name, version, selectedBase.loader), cloned -> finishJoinMigration(server, cloned));
                return;
            }
            if (option != 1) return;
        }
        JComboBox<ProfileInfo> choice = new JComboBox<>(clients.toArray(new ProfileInfo[0])); choice.setSelectedItem(base);
        if (!form("Join " + server.name, fields("Local client profile", choice))) return;
        ProfileInfo selected = (ProfileInfo) choice.getSelectedItem();
        if (!ready(selected)) return;
        if (ping != null && ping.online && !versionMatches(ping.version, selected.gameVersion)
                && !confirm("Version differs", "The server reports " + ping.version + " but this profile uses " + selected.gameVersion + ".\nAttempt to connect with this profile?")) return;
        launchOnServer(server, selected);
    }
    private ProfileInfo preferredBase(List<ProfileInfo> clients) {
        if (loadedSettings != null) for (ProfileInfo profile : clients) if (profile.id.equals(loadedSettings.defaultProfile)) return profile;
        for (ProfileInfo profile : clients) if (profile.template) return profile;
        return clients.get(0);
    }
    private void finishJoinMigration(SavedServer server, ProfileInfo cloned) {
        refreshProfiles();
        if (cloned.launchable) { launchOnServer(server, cloned); return; }
        run("Planning compatible assets", () -> actions.checkProfile(cloned.id), plan -> {
            if (!confirmText("Review migration before joining", plan + "\n\nApply this migration, then join “" + server.name + "”?\nCancel keeps the new profile pending for later review.")) return;
            run("Applying profile migration", () -> actions.updateProfile(cloned.id), summary -> {
                information("Migration summary", summary);
                run("Verifying migrated profile", actions::profiles, updated -> {
                    profiles = updated; showProfiles(); refreshDefaultProfiles();
                    for (ProfileInfo profile : updated) if (profile.id.equals(cloned.id)) { launchOnServer(server, profile); return; }
                    information("Profile unavailable", "The migrated profile could not be found.");
                });
            });
        });
    }
    private void launchOnServer(SavedServer server, ProfileInfo profile) {
        if (!ready(profile)) return;
        ServerAddress address = ServerAddress.parse(server.address);
        run("Launching " + profile.name, () -> { actions.launchProfile(profile.id, address.host, address.port); return null; }, ignored -> status.setText("Launch requested for " + server.name));
    }
    static String gameVersionFromStatus(String version) {
        if (version == null) return "";
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?<![0-9A-Za-z.])(?:[0-9]{2}w[0-9]{2}[a-z]|[0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:-(?:pre|rc)[0-9]+)?)(?![0-9A-Za-z.-])").matcher(version);
        String result = "";
        while (match.find()) { if (!result.isEmpty() && !result.equals(match.group())) return ""; result = match.group(); }
        return result;
    }
    static ProfileInfo matchingClientProfile(List<ProfileInfo> profiles, String version, String loader) {
        if (version == null || version.isEmpty() || loader == null) return null;
        for (ProfileInfo profile : profiles) if ("MODS".equalsIgnoreCase(profile.type) && profile.gameVersion.equals(version)
                && profile.loader.equalsIgnoreCase(loader) && profile.launchable && !profile.template) return profile;
        return null;
    }
    static boolean versionMatches(String serverVersion, String profileVersion) {
        if (serverVersion == null || profileVersion == null || profileVersion.isEmpty()) return false;
        return java.util.regex.Pattern.compile("(?<![0-9A-Za-z.-])" + java.util.regex.Pattern.quote(profileVersion) + "(?![0-9A-Za-z.-])").matcher(serverVersion).find();
    }

    private JPanel profilesPage() {
        JPanel page = page("Profiles", "Isolated modpacks and pluginpacks, reusable across worlds and servers.");
        JPanel content = content();
        typeFilter.addActionListener(e -> showProfiles());
        content.add(toolbar(typeFilter, button("Create", this::createProfile), button("Clone / migrate", () -> cloneProfile(selectedProfile(), null)),
                button("Check", () -> checkOrUpdate(false)), button("Update", () -> checkOrUpdate(true)), button("Refresh", this::refreshProfiles)), BorderLayout.NORTH);
        content.add(tableScroll(profileTable), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBorder(new EmptyBorder(12, 0, 0, 0)); bottom.add(new JScrollPane(profileDetails), BorderLayout.CENTER);
        bottom.add(toolbar(button("Launch client", () -> {
            ProfileInfo profile = selectedProfile(); if (!ready(profile)) return;
            if (!"MODS".equalsIgnoreCase(profile.type)) { information("Client profiles", "Choose a MODS profile to launch the Minecraft client."); return; }
            run("Launching client", () -> { actions.launchProfile(profile.id, null, 25565); return null; }, ignored -> {});
        }), button("Toggle template", () -> {
            ProfileInfo profile = selectedProfile(); if (profile == null) return;
            run("Saving template", () -> { actions.setTemplate(profile.id, !profile.template); return null; }, ignored -> refreshProfiles());
        }), button("Add JAR", this::addArtifact), button("Open folder", () -> {
            ProfileInfo profile = selectedProfile(); if (profile != null) openFolder(profile.directory);
        }), button("Delete", () -> {
            ProfileInfo profile = selectedProfile(); if (profile == null) return;
            if (!confirm("Delete profile", "Move “" + profile.name + "” and its isolated profile directory to AutoPlug's trash?")) return;
            run("Deleting profile", () -> { actions.deleteProfile(profile.id); return null; }, ignored -> refreshProfiles());
        })), BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH); page.add(content, BorderLayout.CENTER);
        profileTable.getSelectionModel().addListSelectionListener(e -> {
            int row = profileTable.getSelectedRow();
            if (row >= 0 && row < visibleProfiles.size()) {
                ProfileInfo profile = visibleProfiles.get(profileTable.convertRowIndexToModel(row));
                profileDetails.setText(profile.directory + "\n" + (profile.launchable ? "Ready to launch" : "Needs migration review")
                        + (profile.migrationSummary == null || profile.migrationSummary.isEmpty() ? "" : "\n" + profile.migrationSummary));
            }
        }); return page;
    }
    private void createProfile() {
        JTextField name = new JTextField(), version = new JTextField("1.21.1");
        JComboBox<String> loader = loaders("VANILLA"), type = new JComboBox<>(new String[]{"MODS", "PLUGINS", "MODS_SERVER"});
        JCheckBox template = new JCheckBox("Use as a reusable template");
        if (!form("Create profile", fields("Name", name, "Minecraft version", version, "Loader", loader, "Asset type", type, "Template", template))) return;
        if (!required(name, version)) return;
        String profileName = name.getText().trim(), gameVersion = version.getText().trim(), loaderName = String.valueOf(loader.getSelectedItem()), typeName = String.valueOf(type.getSelectedItem());
        boolean isTemplate = template.isSelected();
        run("Creating profile", () -> actions.createProfile(profileName, gameVersion, loaderName, typeName, isTemplate), this::profileCreated);
    }
    private void cloneProfile(ProfileInfo source, String suggestedVersion) {
        if (source == null) return;
        JComboBox<ProfileInfo> base = new JComboBox<>(profiles.toArray(new ProfileInfo[0])); base.setSelectedItem(source);
        JTextField name = new JTextField(source.name + " copy"), version = new JTextField(suggestedVersion == null ? source.gameVersion : suggestedVersion);
        JComboBox<String> loader = loaders(source.loader);
        if (!form("Clone and migrate profile", fields("Base profile", base, "New name", name, "Target version", version, "Target loader", loader))) return;
        if (!required(name, version)) return;
        ProfileInfo chosen = (ProfileInfo) base.getSelectedItem(); if (chosen == null) return;
        if (!confirm("Confirm profile migration", "Clone “" + chosen.name + "” for " + version.getText().trim() + " / " + loader.getSelectedItem() + "?\nAutoPlug will check upgrades and downgrades. Applying the migration may disable incompatible assets.\nReview and apply the migration before launching.")) return;
        String profileName = name.getText().trim(), gameVersion = version.getText().trim(), loaderName = String.valueOf(loader.getSelectedItem());
        run("Cloning and checking assets", () -> actions.cloneProfile(chosen.id, profileName, gameVersion, loaderName), this::profileCreated);
    }
    private void profileCreated(ProfileInfo profile) {
        refreshProfiles();
        if (profile != null) information("Profile created: " + profile.name,
                (profile.migrationSummary == null || profile.migrationSummary.trim().isEmpty() ? "Created isolated profile at:\n" + profile.directory : profile.migrationSummary)
                        + (profile.launchable ? "" : "\n\nReview and apply updates to finish migration. This profile cannot launch until its migration is resolved."));
    }
    private void checkOrUpdate(boolean update) {
        ProfileInfo profile = selectedProfile(); if (profile == null) return;
        if (update) {
            run("Checking profile before update", () -> actions.checkProfile(profile.id), summary -> {
                if (!confirmText("Apply profile update", summary + "\n\nApply these changes? Incompatible assets may be disabled.")) return;
                run("Updating profile", () -> actions.updateProfile(profile.id), result -> { information("Update summary", result); refreshProfiles(); });
            });
        } else run("Checking profile", () -> actions.checkProfile(profile.id), summary -> information("Compatibility and updates", summary));
    }
    private void addArtifact() {
        ProfileInfo profile = selectedProfile(); if (profile == null) return;
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Add a JAR to " + profile.name);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Minecraft mod or plugin (*.jar)", "jar"));
        chooser.setAcceptAllFileFilterUsed(false); chooser.setMultiSelectionEnabled(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        JTextField modrinth = new JTextField();
        if (!form("Add " + file.getName(), fields("Destination profile", new JLabel(profile.name), "Modrinth project ID / slug (optional)", modrinth))) return;
        importArtifact(profile, file.getAbsolutePath(), modrinth.getText().trim());
    }
    void importArtifact(ProfileInfo profile, String jarPath, String modrinthId) {
        run("Adding mod or plugin", () -> { actions.addArtifact(profile.id, jarPath, modrinthId.isEmpty() ? null : modrinthId); return null; }, ignored -> {
            refreshProfiles(); status.setText("Added " + new File(jarPath).getName() + " to " + profile.name + ". Use Check to review compatibility and updates.");
        });
    }
    private void refreshProfiles() { run("Loading profiles", actions::profiles, result -> { profiles = result; showProfiles(); refreshDefaultProfiles(); }); }
    private void showProfiles() {
        String filter = String.valueOf(typeFilter.getSelectedItem());
        visibleProfiles = new ArrayList<>(); profileModel.setRowCount(0); profileDetails.setText("");
        for (ProfileInfo profile : profiles) if (filter.equals("All profiles") || profile.type.replace('-', '_').equalsIgnoreCase(filter)) {
            visibleProfiles.add(profile); profileModel.addRow(new Object[]{profile.name, profile.gameVersion, profile.loader, profile.type, profile.template ? "Yes" : "", profile.launchable ? "Ready" : "Review needed"});
        }
    }
    private ProfileInfo selectedProfile() {
        int row = profileTable.getSelectedRow(); if (row < 0) { status.setText("Select a profile first."); return null; }
        return visibleProfiles.get(profileTable.convertRowIndexToModel(row));
    }
    private boolean ready(ProfileInfo profile) {
        if (profile == null) return false;
        if (!profile.launchable) { information("Migration needs review", profile.migrationSummary + "\nResolve the listed issues before launching this profile."); return false; }
        if (profile.template) { information("Template profile", "Clone this template into a working profile before launching it."); return false; }
        return true;
    }
    private List<ProfileInfo> clientProfiles() {
        List<ProfileInfo> result = new ArrayList<>(); for (ProfileInfo p : profiles) if ("MODS".equalsIgnoreCase(p.type)) result.add(p); return result;
    }

    private JPanel worldsPage() {
        JPanel page = page("Virtual Worlds", "Singleplayer worlds powered by their own local dedicated server.");
        JPanel content = content(); content.add(toolbar(button("Create world", this::createWorld), button("Refresh", this::refreshWorlds)), BorderLayout.NORTH);
        worldCards.setLayout(new BoxLayout(worldCards, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(worldCards); scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);
        JLabel note = new JLabel("Local play stays on your PC. Sharing is optional and always requires confirmation.");
        note.setBorder(new EmptyBorder(12, 0, 0, 0)); content.add(note, BorderLayout.SOUTH); page.add(content, BorderLayout.CENTER); return page;
    }
    private void createWorld() {
        List<ProfileInfo> serverProfiles = new ArrayList<>();
        for (ProfileInfo p : profiles) if (!"MODS".equalsIgnoreCase(p.type) && p.launchable) serverProfiles.add(p);
        List<ProfileInfo> clientProfiles = clientProfiles(); clientProfiles.removeIf(p -> !p.launchable || p.template);
        if (serverProfiles.isEmpty() || clientProfiles.isEmpty()) { information("World profiles", "Create a server profile (PLUGINS or MODS_SERVER) and a matching client MODS profile first."); return; }
        JTextField name = new JTextField(); JComboBox<ProfileInfo> server = new JComboBox<>(serverProfiles.toArray(new ProfileInfo[0]));
        JComboBox<ProfileInfo> client = new JComboBox<>(clientProfiles.toArray(new ProfileInfo[0]));
        JCheckBox eula = new JCheckBox("I accept the Minecraft EULA");
        if (!form("Create virtual world", fields("World name", name, "Server profile", server, "Client profile", client, "Minecraft EULA", toolbar(eula, button("Read EULA", this::openEula))))) return;
        if (!required(name)) return;
        ProfileInfo serverProfile = (ProfileInfo) server.getSelectedItem(), clientProfile = (ProfileInfo) client.getSelectedItem();
        if (serverProfile == null || clientProfile == null) return;
        boolean accepted = eula.isSelected();
        String worldName = name.getText().trim();
        run("Creating isolated world", () -> {
            WorldInfo world = actions.createWorld(worldName, serverProfile.id, clientProfile.id);
            if (accepted) actions.setWorldEulaAccepted(world.id, true); return world;
        }, world -> refreshWorlds());
    }
    private void refreshWorlds() { run("Loading worlds", actions::worlds, result -> { worlds = result; showWorlds(); }); }
    private void showWorlds() {
        worldCards.removeAll();
        if (worlds.isEmpty()) {
            JPanel empty = new JPanel(new BorderLayout()); empty.setBorder(new EmptyBorder(60, 24, 60, 24));
            JLabel title = new JLabel("A world of your own", SwingConstants.CENTER); title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
            empty.add(title, BorderLayout.NORTH); empty.add(new JLabel("Create a world from a server pack and a matching client profile.", SwingConstants.CENTER), BorderLayout.CENTER);
            worldCards.add(empty);
        }
        for (WorldInfo world : worlds) {
            JPanel card = new JPanel(new BorderLayout(18, 8)); card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")), new EmptyBorder(16, 16, 16, 16)));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
            JLabel image = new JLabel("WORLD", SwingConstants.CENTER); image.setOpaque(true); image.setBackground(new Color(0x284C40)); image.setForeground(Color.WHITE); image.setPreferredSize(new Dimension(105, 95));
            if (world.thumbnail != null && !world.thumbnail.isEmpty()) run("Loading world thumbnail", () -> {
                File file = new File(world.thumbnail); if (!file.isFile() || file.length() > 8 * 1024 * 1024) return null;
                java.awt.image.BufferedImage bitmap = javax.imageio.ImageIO.read(file);
                if (bitmap == null) return null; return new ImageIcon(bitmap.getScaledInstance(105, 95, Image.SCALE_SMOOTH));
            }, icon -> { if (icon != null) { image.setText(""); image.setIcon(icon); } });
            card.add(image, BorderLayout.WEST);
            JPanel details = new JPanel(new BorderLayout(0, 6)); JLabel name = new JLabel(world.name + (world.running ? "  •  Running" : "")); name.setFont(name.getFont().deriveFont(Font.BOLD, 18f));
            details.add(name, BorderLayout.NORTH); JTextArea description = textArea(2);
            description.setText("Server: " + profileName(world.serverProfileId) + "   ·   Client: " + profileName(world.clientProfileId) + "\n" + world.directory);
            details.add(description, BorderLayout.CENTER);
            details.add(toolbar(button("Play locally", () -> run("Starting world " + world.name, () -> { actions.launchWorld(world.id, false); return null; }, ignored -> refreshWorlds())),
                    button("Share", () -> {
                        if (!confirm("Share this world", "Share “" + world.name + "” beyond this PC?\nThis may open a router port using UPnP and expose the server to the internet.\nOnly share the join address with people you trust.")) return;
                        run("Preparing world sharing", () -> {
                            if (!world.running) actions.launchWorld(world.id, true);
                            return actions.shareWorld(world.id);
                        }, address -> { showShare(address); refreshWorlds(); });
                    }), button("Open folder", () -> openFolder(world.directory)), button("Minecraft EULA", () -> {
                        JCheckBox accept = new JCheckBox("I accept the Minecraft EULA");
                        if (!form("Minecraft EULA", fields("Read the terms", button("Open Minecraft EULA", this::openEula), "Your agreement", accept)) || !accept.isSelected()) return;
                        run("Saving EULA acceptance", () -> { actions.setWorldEulaAccepted(world.id, true); return null; }, ignored -> {});
                    })), BorderLayout.SOUTH);
            card.add(details, BorderLayout.CENTER); worldCards.add(card); worldCards.add(Box.createVerticalStrut(12));
        }
        worldCards.add(Box.createVerticalGlue()); worldCards.revalidate(); worldCards.repaint();
    }
    private String profileName(String id) { for (ProfileInfo profile : profiles) if (profile.id.equals(id)) return profile.name; return id; }
    private void showShare(String text) {
        JTextArea area = textArea(6); area.setText(text); JPanel panel = new JPanel(new BorderLayout(0, 8)); panel.add(new JScrollPane(area));
        panel.add(button("Copy details", () -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null)), BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, panel, "World sharing", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel managerPage(boolean includeLegacyControls) {
        JPanel page = page("Server Manager", "The existing AutoPlug server, console, update tasks and backups.");
        JPanel content = content();
        content.add(toolbar(commandButton("Start", ".start"), commandButton("Stop", ".stop"), commandButton("Restart", ".restart"),
                commandButton("Run tasks", ".run tasks"), commandButton("Back up", ".backup")), BorderLayout.NORTH);
        if (includeLegacyControls) {
            JTabbedPane tabs = new JTabbedPane(); console = new ServerConsolePanel(tabs); tabs.addTab("Console & task output", console);
            try { tabs.addTab("Plugins, mods & server settings", new ServerPanel(tabs)); }
            catch (Exception e) { JTextArea problem = textArea(5); problem.setText("Existing server panels could not load: " + e.getMessage()); tabs.addTab("Server settings", new JScrollPane(problem)); }
            JPanel maintenance = new JPanel(); maintenance.setLayout(new BoxLayout(maintenance, BoxLayout.Y_AXIS)); maintenance.setBorder(new EmptyBorder(20, 20, 20, 20));
            maintenance.add(new JLabel("Run the same update tasks available in the AutoPlug console.")); maintenance.add(Box.createVerticalStrut(16));
            maintenance.add(toolbar(commandButton("Check server", ".check server"), commandButton("Check plugins", ".check plugins"), commandButton("Check mods", ".check mods"), commandButton("Check Java", ".check java")));
            maintenance.add(Box.createVerticalStrut(24)); maintenance.add(new JLabel("Backups use your existing backup settings and require the server to be stopped."));
            maintenance.add(toolbar(commandButton("Create backup", ".backup"), button("Open configuration", () -> openFolder(new File(System.getProperty("user.dir"), "autoplug").getAbsolutePath()))));
            maintenance.add(Box.createVerticalGlue()); tabs.addTab("Tasks & backups", maintenance); content.add(tabs, BorderLayout.CENTER);
        } else content.add(new JLabel("Server controls are available in the running AutoPlug application.", SwingConstants.CENTER), BorderLayout.CENTER);
        page.add(content, BorderLayout.CENTER); return page;
    }
    private JButton commandButton(String label, String command) {
        return button(label, () -> {
            if ((command.equals(".stop") || command.equals(".restart")) && !confirm(label + " server", label + " the existing AutoPlug server?")) return;
            run(label, () -> { ServerConsolePanel.executeCommand(command); return null; }, ignored -> {});
        });
    }

    private JPanel settingsPage() {
        JPanel page = page("Settings", "Runtime choices, default profiles, local networking and Minecraft accounts.");
        JPanel content = new JPanel(); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel runtime = section("Java runtimes"); content.add(runtime);
        content.add(fields("Java 8 executable", java8, "Java 17 executable", java17, "Java 21 executable", java21,
                "Other runtimes (major=path; …)", extraJava));
        content.add(Box.createVerticalStrut(16)); content.add(section("Defaults and networking"));
        content.add(fields("Default client profile", defaultProfile, "Preferred server port", port, "Sharing", upnp));
        content.add(toolbar(button("Save settings", this::saveSettings), button("Reload", this::refreshSettings)));
        content.add(Box.createVerticalStrut(24)); content.add(section("Minecraft account"));
        content.add(fields("Current account", account, "Microsoft app client ID", clientId, "Account storage", rememberAccount, "Offline player name", offlineName));
        content.add(toolbar(button("Sign in with Microsoft", () -> {
            if (!confirm("Microsoft sign-in", "Start Microsoft device sign-in?\nYou will authorize the displayed app in your browser. AutoPlug never asks for your Microsoft password.")) return;
            if (clientId.getText().trim().isEmpty()) { information("Microsoft client ID", "Enter and save the public client ID of your Microsoft application before signing in."); return; }
            SettingsInfo settings;
            try { settings = settingsFromForm(); } catch (IllegalArgumentException e) { error(e); return; }
            run("Waiting for Microsoft sign-in", () -> {
                actions.saveSettings(settings);
                actions.signInMicrosoft(instructions -> SwingUtilities.invokeLater(() -> { if (!closed.get()) showSignIn(instructions); })); return null;
            }, ignored -> { if (signInDialog != null) signInDialog.dispose(); refreshSettings(); });
        }), button("Use offline name", () -> {
            String name = offlineName.getText().trim();
            if (!name.matches("[A-Za-z0-9_]{1,16}")) { information("Offline player name", "Use 1–16 letters, numbers or underscores."); return; }
            run("Switching to offline account", () -> { actions.useOfflineAccount(name); return null; }, ignored -> refreshSettings());
        })));
        JTextArea note = textArea(3); note.setText("Offline mode is for local/offline-enabled play. Online servers usually require a licensed Microsoft account.\nUPnP is used only after you explicitly choose Share; unsupported routers need manual forwarding or a tunnel.");
        note.setBorder(new EmptyBorder(12, 0, 0, 0)); content.add(note); content.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(content); scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(16); page.add(scroll, BorderLayout.CENTER); return page;
    }
    private void refreshSettings() { run("Loading settings", actions::settings, this::showSettings); }
    private void showSettings(SettingsInfo settings) {
        loadedSettings = settings; java8.setText(settings.javaPaths.getOrDefault(8, settings.java8)); java17.setText(settings.javaPaths.getOrDefault(17, settings.java17)); java21.setText(settings.javaPaths.getOrDefault(21, settings.java21));
        StringJoiner extra = new StringJoiner("; "); settings.javaPaths.forEach((major, path) -> { if (major != 8 && major != 17 && major != 21) extra.add(major + "=" + path); }); extraJava.setText(extra.toString());
        clientId.setText(settings.microsoftClientId); account.setText(settings.account); port.setValue(Math.max(1, Math.min(65535, settings.port))); upnp.setSelected(settings.upnp); rememberAccount.setSelected(settings.rememberAccount); refreshDefaultProfiles();
    }
    private void refreshDefaultProfiles() {
        String selected = loadedSettings == null ? "" : loadedSettings.defaultProfile;
        defaultProfile.removeAllItems(); defaultProfile.addItem(null);
        for (ProfileInfo p : clientProfiles()) { defaultProfile.addItem(p); if (p.id.equals(selected)) defaultProfile.setSelectedItem(p); }
    }
    private SettingsInfo settingsFromForm() {
        SettingsInfo settings = new SettingsInfo(); settings.java8 = java8.getText().trim(); settings.java17 = java17.getText().trim(); settings.java21 = java21.getText().trim();
        if (!settings.java8.isEmpty()) settings.javaPaths.put(8, settings.java8); if (!settings.java17.isEmpty()) settings.javaPaths.put(17, settings.java17); if (!settings.java21.isEmpty()) settings.javaPaths.put(21, settings.java21);
        for (String entry : extraJava.getText().split(";")) {
            if (entry.trim().isEmpty()) continue; String[] pair = entry.trim().split("=", 2);
            try { int major = Integer.parseInt(pair[0].trim()); if (major < 1 || pair.length != 2 || pair[1].trim().isEmpty()) throw new IllegalArgumentException(); settings.javaPaths.put(major, pair[1].trim()); }
            catch (RuntimeException e) { throw new IllegalArgumentException("Other runtimes must use major=path, separated by semicolons."); }
        }
        ProfileInfo profile = (ProfileInfo) defaultProfile.getSelectedItem(); settings.defaultProfile = profile == null ? "" : profile.id;
        settings.port = (Integer) port.getValue(); settings.upnp = upnp.isSelected(); settings.rememberAccount = rememberAccount.isSelected(); settings.microsoftClientId = clientId.getText().trim(); settings.account = account.getText(); return settings;
    }
    private void saveSettings() {
        try { SettingsInfo settings = settingsFromForm(); run("Saving settings", () -> { actions.saveSettings(settings); return settings; }, this::showSettings); }
        catch (IllegalArgumentException e) { error(e); }
    }
    private void showSignIn(String instructions) {
        if (signInDialog != null) signInDialog.dispose();
        signInDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Authorize Microsoft sign-in", Dialog.ModalityType.MODELESS);
        JTextArea text = textArea(8); text.setText(instructions); text.setBorder(new EmptyBorder(20, 20, 20, 20));
        signInDialog.add(new JScrollPane(text)); signInDialog.setSize(560, 300); signInDialog.setLocationRelativeTo(this); signInDialog.setVisible(true);
    }

    private <T> void run(String description, Callable<T> work, Consumer<T> success) {
        if (closed.get()) return; running.incrementAndGet(); status.setText(description + "…");
        try { workers.submit(() -> {
            try {
                T result = work.call(); SwingUtilities.invokeLater(() -> { if (!closed.get()) success.accept(result); });
            } catch (Exception e) { SwingUtilities.invokeLater(() -> { if (!closed.get()) { status.setText(description + " failed: " + message(e)); error(e); } }); }
            finally { running.decrementAndGet(); SwingUtilities.invokeLater(() -> { if (!closed.get()) { int remaining = running.get(); if (remaining > 0) status.setText(remaining + " background operation(s) running…"); else if (status.getText().endsWith("…")) status.setText("Ready"); } }); }
        }); } catch (RejectedExecutionException ignored) { running.decrementAndGet(); }
    }
    private void openFolder(String directory) { run("Opening folder", () -> { if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("Opening folders is unavailable on this system."); Desktop.getDesktop().open(new File(directory)); return null; }, ignored -> {}); }
    private void openEula() { run("Opening Minecraft EULA", () -> { if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("Open https://aka.ms/MinecraftEULA in your browser."); Desktop.getDesktop().browse(java.net.URI.create("https://aka.ms/MinecraftEULA")); return null; }, ignored -> {}); }
    private void error(Exception e) { information("Could not complete action", message(e)); }
    private static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private void information(String title, String message) { JTextArea text = textArea(9); text.setText(message); text.setCaretPosition(0); JScrollPane scroll = new JScrollPane(text); scroll.setPreferredSize(new Dimension(590, 260)); JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.INFORMATION_MESSAGE); }
    private boolean confirm(String title, String message) { return JOptionPane.showConfirmDialog(this, message, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION; }
    private boolean confirmText(String title, String message) { JTextArea text = textArea(12); text.setText(message); JScrollPane scroll = new JScrollPane(text); scroll.setPreferredSize(new Dimension(650, 350)); return JOptionPane.showConfirmDialog(this, scroll, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION; }
    private boolean form(String title, JPanel fields) { fields.setPreferredSize(new Dimension(530, Math.max(160, fields.getPreferredSize().height))); return JOptionPane.showConfirmDialog(this, fields, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION; }
    private boolean required(JTextField... fields) { for (JTextField field : fields) if (field.getText().trim().isEmpty()) { information("Missing details", "Complete all required fields before continuing."); return false; } return true; }
    private static JComboBox<String> loaders(String selected) { JComboBox<String> result = new JComboBox<>(new String[]{"VANILLA", "FABRIC", "QUILT", "FORGE", "NEOFORGE", "PAPER", "SPIGOT", "PURPUR"}); result.setSelectedItem(selected); return result; }
    private static JLabel section(String title) { JLabel label = new JLabel(title); label.setFont(label.getFont().deriveFont(Font.BOLD, 16f)); label.setBorder(new EmptyBorder(0, 0, 10, 0)); label.setAlignmentX(Component.LEFT_ALIGNMENT); return label; }
    private static JPanel page(String title, String description) {
        JPanel page = new JPanel(new BorderLayout(0, 22)); page.setBorder(new EmptyBorder(26, 22, 16, 22));
        JPanel heading = new JPanel(new BorderLayout(0, 7)); JLabel label = new JLabel(title); label.setFont(label.getFont().deriveFont(Font.BOLD, 27f));
        heading.add(label, BorderLayout.NORTH); JLabel subtitle = new JLabel(description); subtitle.setFont(subtitle.getFont().deriveFont(12f)); heading.add(subtitle, BorderLayout.SOUTH); page.add(heading, BorderLayout.NORTH); return page;
    }
    private static JPanel content() { return new JPanel(new BorderLayout(0, 12)); }
    private static JPanel toolbar(JComponent... components) { JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3)); for (JComponent component : components) panel.add(component); panel.setAlignmentX(Component.LEFT_ALIGNMENT); return panel; }
    private static JButton button(String text, Runnable action) { JButton button = new JButton(text); button.addActionListener(e -> action.run()); return button; }
    private static JTextArea textArea(int rows) { JTextArea area = new JTextArea(rows, 30); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setFont(UIManager.getFont("Label.font")); return area; }
    private static DefaultTableModel model(String... columns) { return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } }; }
    private static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model); table.setRowHeight(35); table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true); table.getTableHeader().setReorderingAllowed(false);
        javax.swing.table.DefaultTableCellRenderer text = new javax.swing.table.DefaultTableCellRenderer();
        text.putClientProperty("html.disable", Boolean.TRUE); table.setDefaultRenderer(Object.class, text);
        return table;
    }
    private static JScrollPane tableScroll(JTable table) { JScrollPane scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader()); return scroll; }
    private static JPanel fields(Object... fields) {
        JPanel panel = new JPanel(new GridBagLayout()); panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < fields.length; i += 2) {
            GridBagConstraints label = new GridBagConstraints(); label.gridx = 0; label.gridy = i / 2; label.anchor = GridBagConstraints.WEST; label.insets = new Insets(5, 0, 5, 14);
            panel.add(new JLabel(String.valueOf(fields[i])), label);
            GridBagConstraints value = new GridBagConstraints(); value.gridx = 1; value.gridy = i / 2; value.weightx = 1; value.fill = GridBagConstraints.HORIZONTAL; value.insets = new Insets(5, 0, 5, 0);
            panel.add((Component) fields[i + 1], value);
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 16)); return panel;
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return; workers.shutdownNow();
        if (console != null) console.close(); if (signInDialog != null) signInDialog.dispose();
    }
}
