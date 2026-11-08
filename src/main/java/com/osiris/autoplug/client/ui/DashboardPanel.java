package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.launcher.DownloadProgress;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Five-view Swing dashboard. Network, launcher and disk operations never run on the EDT. */
public final class DashboardPanel extends JPanel implements AutoCloseable {
    // FlatLaf caches shared borders when the first controls are created.
    { DashboardTheme.installDefaults(); }
    private final LauncherActions actions;
    private final Consumer<LaunchChoicePanel> launchChoicePresenter;
    private final ServerBrowserService browser;
    private final ExecutorService workers = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "AutoPlug-Dashboard"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger running = new AtomicInteger();
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, JToggleButton> navigationButtons = new LinkedHashMap<>();
    private JTabbedPane serverTabs;
    private ManagedWorldPanel managedWorldPanel;
    private final JLabel status = new JLabel("Ready");
    private final JLabel navigationTitle = new JLabel("Server Browser");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final OverallProgress overall = new OverallProgress();
    private final javax.swing.Timer progressClock = new javax.swing.Timer(400, e -> {
        if (running.get() > 0 || this.activeDownloads > 0) progress.setValue(overall.tick());
    });
    private boolean operationFailed;
    private final JTextField sourceUrl = new JTextField();
    private final JTextArea activity = textArea(5);
    private final JLabel serverEmpty = new JLabel("Add a server or import Minecraft favorites to get started.", SwingConstants.CENTER);
    private final ServerGrid serverCards = new ServerGrid();
    private final JTextField serverSearch = new JTextField(18);
    private final JComboBox<String> serverSort = new JComboBox<>(new String[]{"Last joined", "Favorite order", "Name", "Address", "Status", "MOTD", "Players", "Version", "Latency"});
    private final JToggleButton reverseServerSort = new JToggleButton("Reverse order");
    private final Map<String, ServerCard> serverViews = new LinkedHashMap<>();
    private String selectedServerAddress;
    private final DefaultTableModel profileModel = model("Name", "Minecraft", "Loader", "Type", "Template", "Ready");
    private final JTable profileTable = table(profileModel);
    private final JPanel worldCards = new WorldList();
    private final Map<String, ServerStatus> serverStatuses = new HashMap<>();
    private final Map<String, Long> serverPingRequests = new HashMap<>();
    private long nextServerPingRequest;
    private List<SavedServer> servers = Collections.emptyList();
    private List<ProfileInfo> profiles = Collections.emptyList();
    private List<WorldInfo> worlds = Collections.emptyList();
    private int pingGeneration;
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[]{"All profiles", "MODS", "PLUGINS", "MODS_SERVER"});
    private List<ProfileInfo> visibleProfiles = Collections.emptyList();
    private final JTextArea profileDetails = textArea(6);
    private final JTextField java8 = new JTextField(), java17 = new JTextField(), java21 = new JTextField();
    private final JTextField extraJava = new JTextField(), clientId = new JTextField(), offlineName = new JTextField();
    private final JComboBox<ProfileInfo> defaultProfile = new JComboBox<>();
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(25565, 1, 65535, 1));
    private final JCheckBox upnp = new JCheckBox("Use UPnP when I explicitly share a world");
    private final JCheckBox rememberAccount = new JCheckBox("Remember Microsoft account on this computer");
    private final JCheckBox fullscreen = new JCheckBox("Launch Minecraft in fullscreen");
    private final JLabel account = new JLabel("Loading account…");
    private ServerConsolePanel console;
    private SettingsInfo loadedSettings;
    private JDialog signInDialog;
    private JDialog launchDialog;
    private LaunchChoicePanel launchChoices;
    private final AutoCloseable downloadSubscription;
    private int activeDownloads;

    public DashboardPanel(LauncherActions actions, ServerBrowserService browser) { this(actions, browser, true); }

    /** The legacy controls can be omitted for a standalone preview without initializing a server. */
    public DashboardPanel(LauncherActions actions, ServerBrowserService browser, boolean includeLegacyControls) {
        this(actions, browser, includeLegacyControls, null);
    }
    DashboardPanel(LauncherActions actions, ServerBrowserService browser, boolean includeLegacyControls, Consumer<LaunchChoicePanel> launchChoicePresenter) {
        super(new BorderLayout(0, 0));
        this.launchChoicePresenter = launchChoicePresenter;
        this.actions = Objects.requireNonNull(actions); this.browser = Objects.requireNonNull(browser);
        setBorder(new EmptyBorder(12, 12, 10, 12)); pages.setOpaque(false);
        setPreferredSize(new Dimension(1100, 720));
        JPanel navigation = DashboardTheme.surface(null, 16); navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBorder(new EmptyBorder(24, 16, 20, 16)); navigation.setPreferredSize(new Dimension(195, 600));
        JLabel brand = new JLabel("AutoPlug"); brand.setFont(brand.getFont().deriveFont(Font.BOLD, 25f));
        navigation.add(brand); navigation.add(Box.createVerticalStrut(5));
        navigationTitle.setName("navigation-title"); navigationTitle.setFont(navigationTitle.getFont().deriveFont(Font.BOLD, 13f));
        navigationTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24)); navigationTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        DashboardTheme.tint(navigationTitle, true);
        navigation.add(navigationTitle); navigation.add(Box.createVerticalStrut(30));
        ButtonGroup group = new ButtonGroup();
        String[] names = {"Server Browser", "Worlds", "Profiles", "Server Manager", "Settings"};
        JPanel[] views = {serverPage(), worldsPage(), profilesPage(), managerPage(includeLegacyControls), settingsPage()};
        for (int i = 0; i < names.length; i++) {
            String name = names[i]; pages.add(views[i], name);
            JToggleButton button = new JToggleButton(name); button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setIcon(DashboardTheme.icon(name)); button.setToolTipText(name);
            DashboardTheme.navigation(button);
            button.setPreferredSize(new Dimension(170, 43));
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43)); button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(e -> { showPage(name); if (name.equals("Worlds")) refreshWorlds(); });
            navigationButtons.put(name, button);
            group.add(button); navigation.add(button); navigation.add(Box.createVerticalStrut(8));
            if (i == 0) button.setSelected(true);
        }
        navigation.add(Box.createVerticalGlue());
        JLabel footer = new JLabel("Your worlds. Your profiles."); footer.setFont(footer.getFont().deriveFont(11f)); navigation.add(footer);
        add(navigation, BorderLayout.WEST); add(pages, BorderLayout.CENTER);
        JPanel statusBar = DashboardTheme.surface(new BorderLayout(10, 4), 8);
        status.putClientProperty("html.disable", Boolean.TRUE); status.setName("activity-step");
        progress.setName("activity-progress"); progress.setPreferredSize(new Dimension(140, 34));
        progress.setLayout(new BorderLayout()); progress.setStringPainted(false);
        progress.setForeground(new Color(DashboardTheme.dark() ? 0x254B78 : 0xC9DEFF));
        status.setBorder(new EmptyBorder(5, 10, 5, 10)); progress.add(status);
        progress.setToolTipText("Overall progress is estimated until all steps finish; the activity details show measured downloads.");
        JPanel step = DashboardTheme.transparent(new BorderLayout(10, 0)); step.add(progress);
        sourceUrl.setName("download-source"); sourceUrl.setEditable(false); sourceUrl.setVisible(false);
        sourceUrl.setToolTipText("Current download source — select and copy the full URL"); sourceUrl.getAccessibleContext().setAccessibleName("Download source URL");
        JButton details = iconButton("Activity details", "activity", () -> information("Activity & download sources", activity.getText()));
        statusBar.add(step, BorderLayout.CENTER); statusBar.add(details, BorderLayout.EAST); statusBar.add(sourceUrl, BorderLayout.SOUTH);
        add(statusBar, BorderLayout.SOUTH);
        actions.onProgress(this::receiveProgress);
        actions.onProgressValue(this::receiveProgressValue);
        downloadSubscription = DownloadProgress.subscribe(this::receiveDownloadProgress);
        refreshProfiles(); refreshWorlds(); refreshSettings();
        run("Importing Minecraft favorites", () -> { browser.importVanilla(); return browser.list(); }, this::showServers);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); DashboardTheme.canvas(graphics, getWidth(), getHeight());
    }
    @Override public void updateUI() { super.updateUI(); DashboardTheme.refreshColors(this); }

    private JPanel serverPage() {
        JPanel page = page("Server Browser", "Your Minecraft favorites, with live status and matching local profiles.");
        JPanel content = DashboardTheme.transparent(new BorderLayout(0, 12));
        JPanel controls = DashboardTheme.surface(new BorderLayout(0, 6), 10);
        controls.add(toolbar(primaryButton("Add server", this::addServer), button("Import Minecraft", () -> run("Importing favorites", () -> {
            int count = browser.importVanilla(); return count;
        }, count -> { status.setText("Imported " + count + " new favorites from " + browser.vanillaFile()); refreshServers(); })),
                button("Refresh status", this::refreshServers)), BorderLayout.NORTH);
        serverSearch.setName("server-search"); serverSearch.setToolTipText("Search favorites by name, address or server message");
        serverSearch.getAccessibleContext().setAccessibleName("Search servers");
        serverSearch.putClientProperty("JTextField.placeholderText", "Search your favorites");
        serverSort.setName("server-sort"); serverSort.getAccessibleContext().setAccessibleName("Sort servers");
        reverseServerSort.setName("server-sort-descending"); reverseServerSort.setIcon(DashboardTheme.icon("sort"));
        reverseServerSort.setToolTipText("Reverse the selected sort order");
        controls.add(toolbar(new JLabel("Search"), serverSearch, new JLabel("Sort"), serverSort, reverseServerSort), BorderLayout.SOUTH);
        serverSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { arrangeServerCards(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { arrangeServerCards(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { arrangeServerCards(); }
        });
        serverSort.addActionListener(e -> arrangeServerCards()); reverseServerSort.addActionListener(e -> arrangeServerCards());
        content.add(controls, BorderLayout.NORTH);
        JPanel list = DashboardTheme.transparent(new BorderLayout(0, 10));
        serverCards.setName("server-cards");
        DashboardTheme.tint(serverEmpty, false); serverEmpty.setBorder(new EmptyBorder(40, 12, 40, 12));
        list.add(serverEmpty, BorderLayout.NORTH); list.add(DashboardTheme.scroll(serverCards)); content.add(list, BorderLayout.CENTER);
        JTextArea note = textArea(1); note.setText("Server details come from live pings. Join uses your local profiles and keeps version checks in place.");
        DashboardTheme.tint(note, false); content.add(note, BorderLayout.SOUTH);
        serverTabs = new JTabbedPane(); serverTabs.setName("server-browser-tabs");
        managedWorldPanel = new ManagedWorldPanel(actions, world -> launchManagedWorld(world, false), this::openFolder);
        serverTabs.addTab("Favorites", content); serverTabs.addTab("Managed worlds & consoles", managedWorldPanel);
        serverTabs.addChangeListener(e -> { if (serverTabs.getSelectedIndex() == 1) managedWorldPanel.refresh(); });
        page.add(serverTabs, BorderLayout.CENTER); return page;
    }
    private void showPage(String name) {
        cards.show(pages, name); navigationTitle.setText(name);
        JToggleButton selected = navigationButtons.get(name); if (selected != null) selected.setSelected(true);
    }
    private void manageWorld(WorldInfo world) {
        showPage("Server Browser"); serverTabs.setSelectedIndex(1); managedWorldPanel.select(world.id);
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
    private void copyServerAddress() {
        SavedServer server = selectedServer(); if (server == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(server.address), null);
        status.setText("Copied " + server.address);
    }
    private void editServer() {
        SavedServer selected = selectedServer(); if (selected == null) return;
        JTextField name = new JTextField(selected.name), address = new JTextField(selected.address);
        if (!form("Edit favorite", fields("Name", name, "Address", address))) return;
        try {
            SavedServer edited = new SavedServer(name.getText(), address.getText());
            run("Saving favorite", () -> {
                browser.add(edited.name, edited.address);
                if (!selected.address.equalsIgnoreCase(edited.address)) browser.remove(selected.address);
                return browser.list();
            }, this::showServers);
        } catch (IllegalArgumentException e) { error(e); }
    }
    private void pingSelected() {
        SavedServer selected = selectedServer(); if (selected == null) return;
        int generation = pingGeneration; long request = beginServerPing(selected);
        run("Checking " + selected.name, () -> browser.ping(selected), ping -> showPing(generation, selected, request, ping));
    }
    private void showServers(List<SavedServer> result) {
        displayServers(result, Collections.emptyMap());
        int generation = pingGeneration;
        for (SavedServer server : servers) {
            long request = beginServerPing(server);
            run("Checking " + server.name, () -> browser.ping(server), ping -> showPing(generation, server, request, ping));
        }
    }
    /** Render a snapshot without network work; also used by deterministic preview fixtures. */
    int displayServers(List<SavedServer> result, Map<String, ServerStatus> statuses) {
        ++pingGeneration; servers = new ArrayList<>(result); serverStatuses.clear(); serverStatuses.putAll(statuses); serverPingRequests.clear();
        serverCards.removeAll(); serverViews.clear();
        for (SavedServer server : servers) {
            ServerCard card = new ServerCard(server); serverViews.put(server.address, card); serverCards.add(card.panel);
            card.update(serverStatuses.get(server.address));
        }
        arrangeServerCards();
        return pingGeneration;
    }
    void showPing(int generation, SavedServer server, ServerStatus ping) {
        if (closed.get() || generation != pingGeneration) return;
        ServerCard card = serverViews.get(server.address); if (card == null) return;
        serverStatuses.put(server.address, ping); card.update(ping); arrangeServerCards();
    }
    /** Called on the EDT; a newer manual check supersedes an in-flight check of the same favorite. */
    long beginServerPing(SavedServer server) {
        long request = ++nextServerPingRequest; serverPingRequests.put(server.address, request);
        serverStatuses.remove(server.address);
        ServerCard card = serverViews.get(server.address); if (card != null) card.update(null);
        return request;
    }
    void showPing(int generation, SavedServer server, long request, ServerStatus ping) {
        if (!Objects.equals(serverPingRequests.get(server.address), request)) return;
        showPing(generation, server, ping);
    }
    private SavedServer selectedServer() {
        ServerCard selected = serverViews.get(selectedServerAddress);
        if (selected != null && selected.panel.isVisible()) return selected.server;
        status.setText("Select a server first."); return null;
    }
    private void arrangeServerCards() {
        String query = serverSearch.getText().trim().toLowerCase(Locale.ROOT);
        List<ServerCard> ordered = new ArrayList<>(serverViews.values());
        String sort = String.valueOf(serverSort.getSelectedItem());
        Comparator<ServerCard> order = (left, right) -> 0;
        if ("Last joined".equals(sort)) order = Comparator.<ServerCard>comparingLong(card -> card.server.lastJoinedAt).reversed();
        else if ("Name".equals(sort)) order = Comparator.comparing(card -> card.server.name, String.CASE_INSENSITIVE_ORDER);
        else if ("Address".equals(sort)) order = Comparator.comparing(card -> card.server.address, String.CASE_INSENSITIVE_ORDER);
        else if ("Status".equals(sort)) order = Comparator.comparing(card -> card.state.getText());
        else if ("MOTD".equals(sort)) order = Comparator.comparing(card -> card.message.getText(), String.CASE_INSENSITIVE_ORDER);
        else if ("Version".equals(sort)) order = Comparator.comparing(card -> card.version.getText(), String.CASE_INSENSITIVE_ORDER);
        else if ("Players".equals(sort)) order = Comparator.comparingInt(card -> {
            ServerStatus value = serverStatuses.get(card.server.address); return value != null && value.online ? value.players : -1;
        });
        else if ("Latency".equals(sort)) order = Comparator.comparingLong(card -> {
            ServerStatus value = serverStatuses.get(card.server.address); return value != null && value.online ? value.latency : Long.MAX_VALUE;
        });
        if (!"Favorite order".equals(sort)) ordered.sort(order.thenComparing(card -> card.server.address));
        if (reverseServerSort.isSelected()) Collections.reverse(ordered);
        ServerCard firstVisible = null; int index = 0;
        for (ServerCard card : ordered) {
            serverCards.setComponentZOrder(card.panel, index++);
            String text = card.server.name + " " + card.server.address + " " + card.message.getText() + " " + card.version.getText();
            boolean visible = query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query);
            card.panel.setVisible(visible); if (visible && firstVisible == null) firstVisible = card;
        }
        ServerCard selected = serverViews.get(selectedServerAddress);
        if (selected == null || !selected.panel.isVisible()) selectedServerAddress = firstVisible == null ? null : firstVisible.server.address;
        for (ServerCard card : ordered) card.selectionChanged();
        serverEmpty.setText(servers.isEmpty() ? "Add a server or import Minecraft favorites to get started." : "No favorites match your search.");
        serverEmpty.setVisible(firstVisible == null);
        serverCards.revalidate(); serverCards.repaint();
    }
    private void selectServer(SavedServer server, boolean focus) {
        selectedServerAddress = server.address;
        for (ServerCard card : serverViews.values()) card.selectionChanged();
        ServerCard selected = serverViews.get(server.address);
        if (selected != null && focus) {
            selected.select.requestFocusInWindow();
            serverCards.scrollRectToVisible(selected.panel.getBounds());
        }
    }
    private void navigateServer(SavedServer server, int offset) {
        List<ServerCard> visible = new ArrayList<>();
        for (Component component : serverCards.getComponents()) if (component.isVisible())
            for (ServerCard card : serverViews.values()) if (card.panel == component) visible.add(card);
        for (int i = 0; i < visible.size(); i++) if (visible.get(i).server.address.equals(server.address)) {
            int next = Math.max(0, Math.min(visible.size() - 1, i + offset)); selectServer(visible.get(next).server, true); return;
        }
    }
    private final class ServerCard {
        final SavedServer server;
        final JPanel panel = DashboardTheme.surface(new BorderLayout(18, 8), 16);
        final JToggleButton select;
        final JLabel state = serverStatusBadge();
        final JLabel icon = new JLabel(DashboardTheme.serverIcon(), SwingConstants.CENTER);
        final JTextArea message = textArea(2);
        final JLabel players = serverDetail("Players: —", "players"), version = serverDetail("Version: —", "version"), latency = serverDetail("Latency: —", "ping");
        ServerCard(SavedServer server) {
            this.server = server; panel.setName("server-" + server.address);
            panel.getAccessibleContext().setAccessibleName("Favorite server " + server.name);
            JPanel thumbnail = DashboardTheme.transparent(new GridBagLayout());
            icon.setName("server-icon"); icon.setPreferredSize(new Dimension(96, 96));
            icon.setToolTipText("Server icon (shown when supplied by the server)");
            icon.getAccessibleContext().setAccessibleName("Server icon for " + server.name);
            thumbnail.add(icon); panel.add(thumbnail, BorderLayout.WEST);
            JPanel details = DashboardTheme.transparent(new BorderLayout(0, 6));
            JPanel heading = DashboardTheme.transparent(new BorderLayout(0, 4));
            JPanel stateRow = DashboardTheme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
            state.setName("server-status"); stateRow.add(state); heading.add(stateRow, BorderLayout.NORTH);
            JPanel identity = DashboardTheme.transparent(new BorderLayout(0, 2));
            select = new JToggleButton(); select.putClientProperty("html.disable", Boolean.TRUE); select.setText(server.name);
            select.setName("select-server-" + server.address); select.setHorizontalAlignment(SwingConstants.LEFT);
            select.setFont(select.getFont().deriveFont(Font.BOLD, 18f)); select.setMargin(new Insets(2, 5, 2, 5));
            select.setToolTipText("Server: " + server.name + " — " + server.address); select.getAccessibleContext().setAccessibleName("Select server " + server.name);
            select.getAccessibleContext().setAccessibleDescription("Arrow keys move between cards; Enter joins this server; Space selects it.");
            select.addActionListener(e -> selectServer(server, false));
            select.addFocusListener(new java.awt.event.FocusAdapter() { @Override public void focusGained(java.awt.event.FocusEvent e) { selectServer(server, false); } });
            identity.add(select, BorderLayout.NORTH);
            JLabel address = serverDetail(server.address, "address"); address.setName("server-address"); identity.add(address, BorderLayout.SOUTH);
            heading.add(identity, BorderLayout.CENTER); details.add(heading, BorderLayout.NORTH);
            JPanel body = DashboardTheme.transparent(new BorderLayout(0, 2));
            message.setName("server-message"); message.setFocusable(false); message.setPreferredSize(new Dimension(100, 40)); DashboardTheme.tint(message, false);
            body.add(message, BorderLayout.CENTER);
            JPanel facts = toolbar(players, version, latency);
            players.setName("server-players"); version.setName("server-version"); latency.setName("server-latency");
            body.add(facts, BorderLayout.SOUTH); details.add(body, BorderLayout.CENTER);
            JPanel controls = toolbar(primaryButton("Join", () -> { selectServer(server, false); joinSelected(); }),
                    iconButton("Ping selected", "ping", () -> { selectServer(server, false); pingSelected(); }),
                    iconButton("Copy address", "copy", () -> { selectServer(server, false); copyServerAddress(); }),
                    iconButton("Edit server", "edit", () -> { selectServer(server, false); editServer(); }),
                    iconButton("Remove", "delete", () -> { selectServer(server, false); removeServer(); }));
            for (Component child : controls.getComponents()) if (child instanceof AbstractButton) {
                AbstractButton action = (AbstractButton) child;
                action.getAccessibleContext().setAccessibleDescription(action.getToolTipText() + " for " + server.name + " at " + server.address);
                action.addFocusListener(new java.awt.event.FocusAdapter() { @Override public void focusGained(java.awt.event.FocusEvent e) { selectServer(server, false); } });
            }
            details.add(controls, BorderLayout.SOUTH); panel.add(details, BorderLayout.CENTER);
            bindServerKey("LEFT", "previous-server", () -> navigateServer(server, -1));
            bindServerKey("RIGHT", "next-server", () -> navigateServer(server, 1));
            bindServerKey("UP", "previous-server-row", () -> navigateServer(server, -serverCards.columns()));
            bindServerKey("DOWN", "next-server-row", () -> navigateServer(server, serverCards.columns()));
            bindServerKey("ENTER", "join-server", () -> { selectServer(server, false); joinSelected(); });
            java.awt.event.MouseAdapter click = new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { selectServer(server, true); }
            };
            for (Component child : new Component[]{panel, thumbnail, heading, icon, address, body, stateRow, state, message, facts, players, version, latency}) child.addMouseListener(click);
        }
        private void bindServerKey(String key, String name, Runnable action) {
            select.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
            select.getActionMap().put(name, new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); } });
        }
        void selectionChanged() {
            boolean selected = server.address.equals(selectedServerAddress); select.setSelected(selected);
            panel.putClientProperty("AutoPlug.selected", selected); panel.repaint();
        }
        void update(ServerStatus ping) {
            state.setText(ping == null ? "Checking…" : ping.online ? "Online" : "Offline · Unavailable");
            state.putClientProperty("server-online", ping == null ? null : ping.online);
            state.setIcon(DashboardTheme.icon(ping != null && ping.online ? "online" : ping == null ? "ping" : "offline-server"));
            icon.setIcon(ping != null && ping.icon != null ? new ImageIcon(ping.icon) : DashboardTheme.serverIcon());
            String detail = ping == null ? "Checking this server’s status…" : ping.online ? Objects.toString(ping.motd, "") : Objects.toString(ping.message, "Status unavailable");
            message.setText(detail); message.setCaretPosition(0); message.setToolTipText("Server message: " + detail); message.getAccessibleContext().setAccessibleName("Server message: " + detail);
            players.setText("Players: " + (ping != null && ping.online ? ping.players + " / " + ping.capacity : "—"));
            version.setText("Minecraft: " + (ping != null && ping.online ? Objects.toString(ping.version, "—") : "—"));
            latency.setText("Latency: " + (ping != null && ping.online ? ping.latency + " ms" : "—"));
            for (JLabel fact : new JLabel[]{players, version, latency}) fact.setToolTipText(fact.getText());
        }
    }
    private static JLabel serverStatusBadge() {
        JLabel badge = new JLabel("Checking…") {
            @Override protected void paintComponent(Graphics graphics) {
                Boolean online = (Boolean) getClientProperty("server-online");
                boolean dark = DashboardTheme.dark();
                Color foreground = online == null ? DashboardTheme.muted() : online
                        ? new Color(dark ? 0x94E2B0 : 0x17643D) : new Color(dark ? 0xFFB0B0 : 0xA5262F);
                Color background = online == null ? new Color(dark ? 0x303D50 : 0xEDF1F6) : online
                        ? new Color(dark ? 0x173E2B : 0xE1F5E8) : new Color(dark ? 0x51272C : 0xFDE7E9);
                if (!foreground.equals(getForeground())) setForeground(foreground);
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(background); g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                } finally { g.dispose(); }
                super.paintComponent(graphics);
            }
        };
        badge.setBorder(new EmptyBorder(3, 9, 3, 9)); badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));
        return badge;
    }
    private static JLabel serverDetail(String text, String icon) {
        JLabel label = new JLabel(); label.putClientProperty("html.disable", Boolean.TRUE); label.setText(text);
        label.setIcon(DashboardTheme.icon(icon)); label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setToolTipText("Details: " + text); DashboardTheme.tint(label, false); return label;
    }
    private static final class ServerGrid extends JPanel implements Scrollable {
        private static final int GAP = 12, CARD_HEIGHT = 246;
        private int previousColumns;
        ServerGrid() { super(null); setOpaque(false); }
        int columns() { return 1; }
        @Override public void doLayout() {
            int columns = columns(), width = Math.max(0, (getWidth() - GAP * (columns - 1)) / columns), index = 0;
            for (Component card : getComponents()) if (card.isVisible()) {
                card.setBounds((index % columns) * (width + GAP), (index / columns) * (CARD_HEIGHT + GAP), width, CARD_HEIGHT); index++;
            }
            if (previousColumns != columns) { previousColumns = columns; revalidate(); }
        }
        @Override public Dimension getPreferredSize() {
            int count = 0; for (Component card : getComponents()) if (card.isVisible()) count++;
            int rows = (count + columns() - 1) / columns(); return new Dimension(0, Math.max(0, rows * (CARD_HEIGHT + GAP) - GAP));
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(800, 520); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(24, visible.height - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private void joinSelected() {
        SavedServer server = selectedServer(); if (server == null) return;
        joinServer(server);
    }
    private void joinServer(SavedServer server) {
        ServerStatus ping = serverStatuses.get(server.address);
        String targetVersion = ping != null && ping.online ? gameVersionFromStatus(ping.version) : "";
        List<ProfileInfo> clients = clientProfiles();
        if (clients.isEmpty()) {
            run("Preparing your default Minecraft profile", () -> { actions.ensureDefaultProfiles(targetVersion); return actions.profiles(); }, result -> {
                profiles = result; showProfiles(); refreshDefaultProfiles();
                if (clientProfiles().isEmpty()) information("Default profile unavailable", "AutoPlug could not prepare the default client. Check the activity details and try again.");
                else joinServer(server);
            }); return;
        }
        ProfileInfo base = preferredBase(clients);
        if (ping != null && ping.online) {
            ProfileInfo match = matchingClientProfile(clients, targetVersion, base.loader);
            List<ProfileInfo> compatible = new ArrayList<>();
            for (ProfileInfo p : clients) if (p.launchable && !p.template && targetVersion.equals(p.gameVersion)) compatible.add(p);
            if (!compatible.isEmpty()) {
                chooseProfile("server:" + server.address, "Join " + server.name, compatible, match, selected -> launchOnServer(server, selected)); return;
            }
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
        chooseProfile("server:" + server.address, "Join " + server.name, clients, base, selected -> {
            if (!ready(selected)) return;
            if (ping != null && ping.online && !versionMatches(ping.version, selected.gameVersion)
                    && !confirm("Version differs", "The server reports " + ping.version + " but this profile uses " + selected.gameVersion + ".\nAttempt to connect with this profile?")) return;
            launchOnServer(server, selected);
        });
    }
    private void chooseProfile(String target, String title, List<ProfileInfo> candidates, ProfileInfo fallback, Consumer<ProfileInfo> selected) {
        List<ProfileInfo> available = new ArrayList<>(candidates); available.removeIf(p -> !p.launchable || p.template);
        if (available.isEmpty()) { information("No ready profile", "Create or finish migrating a compatible client profile first."); return; }
        run("Loading your previous launch choice", () -> actions.preferredProfile(target), remembered -> {
            ProfileInfo preferred = null;
            for (ProfileInfo p : available) if (p.id.equals(remembered)) preferred = p;
            if (preferred == null && loadedSettings != null) for (ProfileInfo p : available) if (p.id.equals(loadedSettings.defaultProfile)) preferred = p;
            boolean auto = preferred != null;
            if (preferred == null && fallback != null && available.stream().anyMatch(p -> p.id.equals(fallback.id))) {
                preferred = fallback; if (target.startsWith("world:")) auto = true;
            }
            if (launchDialog != null) launchDialog.dispose(); if (launchChoices != null) launchChoices.close();
            if (launchChoicePresenter != null) {
                launchChoices = new LaunchChoicePanel(available, preferred, auto, selected);
                launchChoicePresenter.accept(launchChoices); return;
            }
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), title, Dialog.ModalityType.APPLICATION_MODAL);
            launchDialog = dialog;
            LaunchChoicePanel panel = new LaunchChoicePanel(available, preferred, auto, profile -> {
                dialog.dispose(); selected.accept(profile);
            });
            launchChoices = panel; dialog.add(panel); dialog.pack(); dialog.setResizable(true); dialog.setLocationRelativeTo(this);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosed(java.awt.event.WindowEvent e) { panel.close(); } });
            dialog.setVisible(true); panel.close();
        });
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
        run("Launching " + profile.name, () -> {
            actions.launchProfile(profile.id, address.host, address.port);
            try { actions.rememberProfile("server:" + server.address, profile.id); browser.recordJoined(server.address); }
            catch (Exception e) { receiveProgress("Minecraft started, but the last-used choice could not be saved: " + message(e)); }
            return null;
        }, ignored -> { status.setText("Launch requested for " + server.name); refreshServers(); });
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
        profileTable.setName("profile-table"); profileDetails.setName("profile-details");
        JPanel page = page("Profiles", "Isolated modpacks and pluginpacks, reusable across worlds and servers.");
        JPanel content = content();
        typeFilter.addActionListener(e -> showProfiles());
        content.add(toolbar(typeFilter, primaryButton("Create", this::createProfile), button("Clone / migrate", () -> cloneProfile(selectedProfile(), null)),
                button("Check", () -> checkOrUpdate(false)), button("Update", () -> checkOrUpdate(true)), button("Refresh", this::refreshProfiles)), BorderLayout.NORTH);
        content.add(tableScroll(profileTable), BorderLayout.CENTER);
        JPanel bottom = DashboardTheme.transparent(new BorderLayout(0, 8));
        bottom.add(toolbar(button("Modrinth", () -> openLink("https://modrinth.com/mods")),
                button("CurseForge", () -> openLink("https://www.curseforge.com/minecraft")),
                button("AutoPlug mods", () -> openLink("https://autoplug.one/installer/mods"))), BorderLayout.NORTH);
        bottom.setBorder(new EmptyBorder(12, 0, 0, 0)); bottom.add(DashboardTheme.scroll(profileDetails), BorderLayout.CENTER);
        bottom.add(toolbar(primaryButton("Launch client", () -> {
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
                profileDetails.setCaretPosition(0);
                String assetFolder = "PLUGINS".equalsIgnoreCase(profile.type) ? "plugins" : "mods";
                run("Checking profile content", () -> {
                    File folder = new File(profile.directory, assetFolder);
                    String[] jars = folder.list((parent, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
                    return !folder.exists() || (jars != null && jars.length == 0);
                }, empty -> {
                    int selected = profileTable.getSelectedRow();
                    if (empty && selected >= 0 && selected < visibleProfiles.size()
                            && visibleProfiles.get(profileTable.convertRowIndexToModel(selected)).id.equals(profile.id)) {
                        profileDetails.append("\nNo " + assetFolder + " added yet. Use Add JAR to build your pack, or keep this profile empty.");
                        profileDetails.setCaretPosition(0);
                    }
                });
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
        JPanel page = page("Worlds", "Your Minecraft saves and managed worlds, together in one place.");
        JPanel content = DashboardTheme.transparent(new BorderLayout(0, 12));
        JPanel controls = DashboardTheme.surface(new BorderLayout(), 8);
        controls.add(toolbar(primaryButton("Create world", this::createWorld), button("Refresh", this::refreshWorlds)));
        content.add(controls, BorderLayout.NORTH);
        worldCards.setLayout(DashboardTheme.verticalStack());
        worldCards.setOpaque(false);
        JScrollPane scroll = DashboardTheme.scroll(worldCards);
        content.add(scroll, BorderLayout.CENTER);
        JPanel note = DashboardTheme.surface(new BorderLayout(), 12);
        note.add(new JLabel("Singleplayer opens your original save. Managed worlds support optional sharing."));
        content.add(note, BorderLayout.SOUTH); page.add(content, BorderLayout.CENTER); return page;
    }
    private void createWorld() { createWorld(false); }
    private void createWorld(boolean defaultsChecked) {
        List<ProfileInfo> serverProfiles = new ArrayList<>();
        for (ProfileInfo p : profiles) if (!"MODS".equalsIgnoreCase(p.type) && p.launchable) serverProfiles.add(p);
        List<ProfileInfo> clientProfiles = clientProfiles(); clientProfiles.removeIf(p -> !p.launchable || p.template);
        if (serverProfiles.isEmpty() || clientProfiles.isEmpty()) {
            if (defaultsChecked) { information("Default profiles unavailable", "AutoPlug could not prepare matching defaults. Check the activity details and try again."); return; }
            run("Preparing default world profiles", () -> { actions.ensureDefaultProfiles(""); return actions.profiles(); }, result -> {
                profiles = result; showProfiles(); refreshDefaultProfiles(); createWorld(true);
            }); return;
        }
        JTextField name = new JTextField(); JComboBox<ProfileInfo> server = new JComboBox<>(serverProfiles.toArray(new ProfileInfo[0]));
        JComboBox<ProfileInfo> client = new JComboBox<>(clientProfiles.toArray(new ProfileInfo[0]));
        JCheckBox eula = new JCheckBox("I accept the Minecraft EULA");
        if (!form("Create managed world", fields("World name", name, "Server profile", server, "Client profile", client, "Minecraft EULA", toolbar(eula, button("Read EULA", this::openEula))))) return;
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
            JPanel empty = DashboardTheme.surface(new BorderLayout(0, 12), 24); empty.setBorder(new EmptyBorder(60, 24, 60, 24));
            JLabel title = new JLabel("A world of your own", SwingConstants.CENTER); title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
            empty.add(title, BorderLayout.NORTH); empty.add(new JLabel("Minecraft saves appear here automatically, or create a managed world.", SwingConstants.CENTER), BorderLayout.CENTER);
            worldCards.add(empty);
        }
        for (WorldInfo world : worlds) {
            JPanel card = DashboardTheme.surface(new BorderLayout(18, 8), 16);
            card.setName("world-" + world.id); card.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel image = DashboardTheme.worldThumbnail(); image.setToolTipText(world.name);
            image.getAccessibleContext().setAccessibleName("World icon for " + world.name);
            if (world.thumbnail != null && !world.thumbnail.isEmpty()) run("Loading world thumbnail", () -> {
                File file = new File(world.thumbnail); if (!file.isFile() || file.length() > 8 * 1024 * 1024) return null;
                java.awt.image.BufferedImage bitmap = javax.imageio.ImageIO.read(file);
                if (bitmap == null) return null;
                double scale = 96.0 / Math.max(bitmap.getWidth(), bitmap.getHeight());
                return new ImageIcon(bitmap.getScaledInstance(Math.max(1, (int) (bitmap.getWidth() * scale)), Math.max(1, (int) (bitmap.getHeight() * scale)), Image.SCALE_SMOOTH));
            }, icon -> { if (icon != null) { image.setText(""); image.setIcon(icon); } });
            JPanel thumbnail = DashboardTheme.transparent(new GridBagLayout()); thumbnail.add(image);
            card.add(thumbnail, BorderLayout.WEST);
            JPanel details = DashboardTheme.transparent(new BorderLayout(0, 6));
            JPanel heading = DashboardTheme.transparent(new BorderLayout(0, 4));
            JLabel kind = DashboardTheme.badge(world.local ? "Singleplayer" : "Managed world" + (world.running ? "  ·  Running" : ""));
            JPanel badgeRow = DashboardTheme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0)); badgeRow.add(kind);
            if (world.metadataAvailable && world.quickPlayEligible) {
                JLabel quickPlay = DashboardTheme.badge("Quick Play"); quickPlay.setToolTipText("This version supports direct singleplayer Quick Play.");
                badgeRow.add(Box.createHorizontalStrut(6)); badgeRow.add(quickPlay);
            }
            JLabel name = new JLabel(); name.putClientProperty("html.disable", Boolean.TRUE); name.setText(world.name); name.setFont(name.getFont().deriveFont(Font.BOLD, 18f));
            heading.add(badgeRow, BorderLayout.NORTH); heading.add(name, BorderLayout.CENTER);
            details.add(heading, BorderLayout.NORTH); JTextArea description = textArea(world.metadataAvailable ? 4 : 3);
            DashboardTheme.tint(description, false);
            description.setText((world.local ? "Minecraft " + (knownWorldVersion(world) ? world.gameVersion : "version unknown — choose before launch")
                    : "Minecraft " + (knownWorldVersion(world) ? world.gameVersion : "version unknown") + " · Server: " + profileName(world.serverProfileId) + " · Client: " + profileName(world.clientProfileId))
                    + "\n" + worldMetadata(world) + "\n" + world.directory);
            description.setToolTipText(world.directory + (world.dataPacks.isEmpty() ? "" : " | Datapacks: " + String.join(", ", world.dataPacks)));
            details.add(description, BorderLayout.CENTER);
            if (world.local) details.add(toolbar(primaryButton("Launch", () -> launchLocalWorld(world)), button("Open folder", () -> openFolder(world.directory))), BorderLayout.SOUTH);
            else details.add(toolbar(primaryButton("Play locally", () -> launchManagedWorld(world, false)),
                    button("Server console", () -> manageWorld(world)),
                    button("Share", () -> {
                        if (!confirm("Share this world", "Share “" + world.name + "” beyond this PC?\nThis may open a router port using UPnP and expose the server to the internet.\nOnly share the join address with people you trust.")) return;
                        if (!world.running) { launchManagedWorld(world, true); return; }
                        run("Preparing world sharing", () -> {
                            return actions.shareWorld(world.id);
                        }, address -> { showShare(address); refreshWorlds(); });
                    }), button("Open folder", () -> openFolder(world.directory)), button("Minecraft EULA", () -> {
                        JCheckBox accept = new JCheckBox("I accept the Minecraft EULA");
                        if (!form("Minecraft EULA", fields("Read the terms", button("Open Minecraft EULA", this::openEula), "Your agreement", accept)) || !accept.isSelected()) return;
                        run("Saving EULA acceptance", () -> { actions.setWorldEulaAccepted(world.id, true); return null; }, ignored -> {});
                    })), BorderLayout.SOUTH);
            card.add(details, BorderLayout.CENTER);
            nameWorldActions(card, world.name);
            worldCards.add(card); worldCards.add(Box.createVerticalStrut(12));
        }
        GridBagConstraints remaining = new GridBagConstraints();
        remaining.gridx = 0; remaining.gridy = GridBagConstraints.RELATIVE; remaining.weightx = 1; remaining.weighty = 1;
        remaining.fill = GridBagConstraints.BOTH;
        worldCards.add(Box.createVerticalGlue(), remaining); worldCards.revalidate(); worldCards.repaint();
    }

    static String worldMetadata(WorldInfo world) {
        if (!world.metadataAvailable) return "Additional save metadata unavailable";
        String played = world.lastPlayed <= 0 ? "Unknown" : DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(world.lastPlayed));
        String size = world.sizeBytes < 0 ? "Unknown" : (world.sizeComplete ? "" : "At least ") + readableSize(world.sizeBytes, !world.sizeComplete);
        return "Last played: " + played + "  ·  Size: " + size + "\n"
                + (world.modded ? "Modded" : "Vanilla") + "  ·  Cheats: " + (world.cheats ? "On" : "Off")
                + "  ·  Hardcore: " + (world.hardcore ? "On" : "Off") + "  ·  Datapacks: " + world.dataPacks.size();
    }
    static String readableSize(long bytes) { return readableSize(bytes, false); }
    private static String readableSize(long bytes, boolean lowerBound) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes; String[] units = {"B", "KiB", "MiB", "GiB", "TiB"}; int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", lowerBound ? Math.floor(value * 10) / 10 : value, units[unit]);
    }
    private static void nameWorldActions(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (component instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) component;
                button.getAccessibleContext().setAccessibleName(button.getText() + " — " + name);
                button.setToolTipText(button.getText() + " — " + name);
            }
            if (component instanceof Container) nameWorldActions((Container) component, name);
        }
    }

    private static boolean knownWorldVersion(WorldInfo world) { return world.gameVersion != null && !world.gameVersion.trim().isEmpty(); }
    private void launchManagedWorld(WorldInfo world, boolean share) {
        List<ProfileInfo> compatible = new ArrayList<>(); ProfileInfo server = null, prior = null;
        for (ProfileInfo p : profiles) { if (p.id.equals(world.serverProfileId)) server = p; if (p.id.equals(world.clientProfileId)) prior = p; }
        for (ProfileInfo p : clientProfiles()) {
            String version = server == null ? world.gameVersion : server.gameVersion;
            if (version != null && !version.isEmpty() && !version.equals(p.gameVersion)) continue;
            if (server != null && "MODS_SERVER".equals(server.type) && !"VANILLA".equals(server.loader) && !server.loader.equals(p.loader)) continue;
            compatible.add(p);
        }
        if (world.running) { manageWorld(world); return; }
        chooseProfile("world:" + world.id, "Play " + world.name, compatible, prior, chosen ->
            run("Starting world " + world.name, () -> { actions.launchWorld(world.id, share, chosen.id); return share ? actions.shareWorld(world.id) : ""; }, result -> {
                if (share) showShare(result); refreshWorlds(); if (managedWorldPanel != null) managedWorldPanel.refresh();
            }));
    }
    private void launchLocalWorld(WorldInfo world) {
        String version = world.gameVersion;
        if (!knownWorldVersion(world)) {
            JTextField choice = new JTextField();
            JTextArea explanation = textArea(3);
            explanation.setText("This save has no recorded Minecraft version. Enter the version you last used for it. Choose carefully: opening a save in another version can upgrade or damage it. Back up the save first.");
            JPanel prompt = new JPanel(new BorderLayout(0, 12)); prompt.add(explanation, BorderLayout.NORTH);
            prompt.add(fields("Minecraft version", choice), BorderLayout.CENTER);
            if (!form("Choose this save's existing version", prompt) || !required(choice)) return;
            version = choice.getText().trim();
        }
        final String selectedVersion = version;
        run("Preparing compatible local-world profiles", () -> { actions.ensureDefaultProfiles(selectedVersion); return actions.profiles(); }, result -> {
            profiles = result; showProfiles(); refreshDefaultProfiles();
            List<ProfileInfo> compatible = new ArrayList<>();
            for (ProfileInfo p : clientProfiles()) if (selectedVersion.equals(p.gameVersion) && "VANILLA".equals(p.loader)) compatible.add(p);
            chooseProfile("world:" + world.id, "Launch " + world.name, compatible, null, chosen ->
                run("Launching " + world.name + " with Minecraft " + selectedVersion,
                    () -> actions.launchLocalWorld(world.id, selectedVersion, chosen.id), text -> { status.setText(text); status.setToolTipText(text); }));
        });
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
        JPanel identity = settingsGroup("Minecraft account"); identity.setName("settings-account");
        identity.add(stackedFields("Current account", account, "Microsoft app client ID", clientId, "Offline player name", offlineName));
        rememberAccount.setText("Remember my Microsoft account"); identity.add(rememberAccount);
        identity.add(toolbar(primaryButton("Sign in with Microsoft", () -> {
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
        JTextArea note = textArea(2); note.setText("Offline mode is for local/offline-enabled play. Online servers usually require a licensed Microsoft account.");
        DashboardTheme.tint(note, false); note.setBorder(new EmptyBorder(10, 0, 0, 0)); identity.add(note);
        JPanel defaults = settingsGroup("Defaults and sharing"); defaults.setName("settings-defaults");
        defaults.add(stackedFields("Default client profile", defaultProfile, "Preferred server port", port));
        defaults.add(fullscreen);
        upnp.setText("Use UPnP when I choose Share"); defaults.add(upnp);
        JTextArea sharing = textArea(2); sharing.setText("Sharing is always explicit. UPnP opens a router port only after you choose Share.");
        DashboardTheme.tint(sharing, false); sharing.setBorder(new EmptyBorder(10, 0, 0, 0)); defaults.add(sharing);
        JPanel advanced = settingsGroup(null); advanced.setName("settings-advanced");
        JToggleButton disclosure = new JToggleButton("Advanced"); disclosure.setIcon(DashboardTheme.icon("advanced"));
        disclosure.setToolTipText("Show optional Java runtime overrides"); disclosure.getAccessibleContext().setAccessibleName("Advanced runtime settings");
        disclosure.setAlignmentX(Component.LEFT_ALIGNMENT); advanced.add(disclosure);
        JPanel runtime = DashboardTheme.transparent(new BorderLayout(0, 8)); runtime.setName("advanced-runtime-fields");
        JTextArea hint = textArea(2); hint.setText("Java is resolved automatically. Override paths only when you need a specific installation."); DashboardTheme.tint(hint, false);
        runtime.add(hint, BorderLayout.NORTH);
        runtime.add(stackedFields("Java 8 executable", java8, "Java 17 executable", java17, "Java 21 executable", java21, "Other runtimes (major=path; …)", extraJava));
        runtime.setVisible(false); advanced.add(runtime);
        alignGroup(identity); alignGroup(defaults); alignGroup(advanced);
        disclosure.addActionListener(e -> { runtime.setVisible(disclosure.isSelected()); disclosure.setText(disclosure.isSelected() ? "Advanced −" : "Advanced"); page.revalidate(); });
        JPanel right = DashboardTheme.transparent(DashboardTheme.verticalStack());
        right.add(defaults); right.add(Box.createVerticalStrut(12)); right.add(advanced);
        ResponsiveSettings settings = new ResponsiveSettings(identity, right); settings.setName("responsive-settings");
        page.add(DashboardTheme.scroll(settings), BorderLayout.CENTER);
        JPanel save = DashboardTheme.surface(new BorderLayout(), 8);
        save.add(toolbar(primaryButton("Save settings", this::saveSettings), button("Reload", this::refreshSettings)));
        page.add(save, BorderLayout.SOUTH); return page;
    }

    private static JPanel settingsGroup(String title) {
        JPanel panel = DashboardTheme.surface(DashboardTheme.verticalStack(), 16);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (title != null) panel.add(section(title));
        return panel;
    }
    private static void alignGroup(JPanel panel) {
        for (Component child : panel.getComponents()) if (child instanceof JComponent) ((JComponent) child).setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static JPanel stackedFields(Object... fields) {
        JPanel panel = DashboardTheme.transparent(new GridBagLayout()); panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < fields.length; i += 2) {
            JComponent control = (JComponent) fields[i + 1];
            JLabel label = new JLabel(String.valueOf(fields[i])); label.setLabelFor(control); DashboardTheme.tint(label, false);
            GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = i; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(i == 0 ? 0 : 8, 0, 4, 0);
            panel.add(label, c); c.gridy++; c.insets = new Insets(0, 0, 0, 0); panel.add(control, c);
            control.setMinimumSize(new Dimension(80, control.getPreferredSize().height));
        }
        return panel;
    }

    /** Reflows real controls, preserving edits; vertical scrolling remains available at narrow sizes. */
    private static final class ResponsiveSettings extends JPanel implements Scrollable {
        ResponsiveSettings(JPanel left, JPanel right) { super(null); setOpaque(false); add(left); add(right); }
        private boolean wide(int width) { return width >= 780; }
        @Override public void doLayout() {
            int width = getWidth(); int column = wide(width) ? (width - 12) / 2 : width; int y = 0;
            for (int i = 0; i < getComponentCount(); i++) {
                Component child = getComponent(i); child.setSize(column, child.getPreferredSize().height);
                int height = child.getPreferredSize().height;
                child.setBounds(wide(width) ? i * (column + 12) : 0, wide(width) ? 0 : y, column, height); y += height + 12;
            }
        }
        @Override public Dimension getPreferredSize() {
            int width = getParent() == null ? 800 : getParent().getWidth(); int height = 0;
            for (Component child : getComponents()) height = wide(width) ? Math.max(height, child.getPreferredSize().height) : height + child.getPreferredSize().height + 12;
            return new Dimension(600, height);
        }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height - 16; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private static final class WorldList extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height - 16; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private void refreshSettings() { run("Loading settings", actions::settings, this::showSettings); }
    private void showSettings(SettingsInfo settings) {
        loadedSettings = settings; java8.setText(settings.javaPaths.getOrDefault(8, settings.java8)); java17.setText(settings.javaPaths.getOrDefault(17, settings.java17)); java21.setText(settings.javaPaths.getOrDefault(21, settings.java21));
        StringJoiner extra = new StringJoiner("; "); settings.javaPaths.forEach((major, path) -> { if (major != 8 && major != 17 && major != 21) extra.add(major + "=" + path); }); extraJava.setText(extra.toString());
        clientId.setText(settings.microsoftClientId); account.setText(settings.account); port.setValue(Math.max(1, Math.min(65535, settings.port))); upnp.setSelected(settings.upnp); rememberAccount.setSelected(settings.rememberAccount); fullscreen.setSelected(settings.fullscreen); refreshDefaultProfiles();
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
        settings.port = (Integer) port.getValue(); settings.upnp = upnp.isSelected(); settings.rememberAccount = rememberAccount.isSelected(); settings.fullscreen = fullscreen.isSelected(); settings.microsoftClientId = clientId.getText().trim(); settings.account = account.getText(); return settings;
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
        if (closed.get()) return;
        int active = running.incrementAndGet();
        if (active == 1) { sourceUrl.setVisible(false); setActivity(description + "…"); }
        if (active == 1 && activeDownloads == 0) { overall.start(); operationFailed = false; progress.setValue(overall.value()); progressClock.start(); }
        progress.setVisible(true);
        try { workers.submit(() -> {
            try {
                T result = work.call(); SwingUtilities.invokeLater(() -> {
                    if (closed.get()) return;
                    String before = status.getText();
                    try { success.accept(result); }
                    finally { finishOperation(); if (running.get() == 0 && Objects.equals(before, status.getText())) setActivity(description + " complete"); }
                });
            } catch (Exception e) { SwingUtilities.invokeLater(() -> {
                if (closed.get()) return;
                operationFailed = true; finishOperation(); setActivity(description + " failed: " + message(e)); error(e);
            }); }
        }); } catch (RejectedExecutionException ignored) { finishOperation(); }
    }
    private void finishOperation() {
        if (running.decrementAndGet() <= 0 && activeDownloads == 0) { progressClock.stop(); if (!operationFailed) progress.setValue(overall.complete()); }
    }
    private void receiveProgress(String message) {
        if (message == null || message.trim().isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            if (closed.get()) return;
            setActivity(message);
            java.util.regex.Matcher url = java.util.regex.Pattern.compile("https?://[^\\s<>]+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(message);
            if (url.find()) { sourceUrl.setText(url.group()); sourceUrl.setCaretPosition(0); sourceUrl.setVisible(true); revalidate(); }
        });
    }
    private void receiveDownloadProgress(DownloadProgress.Event event) {
        SwingUtilities.invokeLater(() -> {
            if (closed.get()) return;
            int before = activeDownloads; activeDownloads = event.activeTransfers;
            if (before == 0 && activeDownloads > 0 && running.get() == 0) { overall.start(); operationFailed = false; progress.setValue(overall.value()); progressClock.start(); }
            String label = event.message + (event.complete ? " — complete" : event.finished ? " — stopped" : "");
            if (!Objects.equals(status.getText(), label)) setActivity(label);
            if (event.sourceUrl != null && !event.sourceUrl.isEmpty()) {
                if (!Objects.equals(sourceUrl.getText(), event.sourceUrl)) activity.append("Source: " + event.sourceUrl + "\n");
                sourceUrl.setText(event.sourceUrl); sourceUrl.setCaretPosition(0); sourceUrl.setVisible(true);
            }
            boolean measured = event.totalBytes > 0 && event.downloadedBytes >= 0;
            if (measured) {
                int percent = (int) Math.min(100, 100.0 * event.downloadedBytes / event.totalBytes);
                progress.setToolTipText("Overall progress is estimated. Current file: " + percent + "% · " + readableSize(event.downloadedBytes) + " / " + readableSize(event.totalBytes));
            }
            if (event.finished && !event.complete && running.get() == 0) operationFailed = true;
            if (event.finished && running.get() == 0 && activeDownloads == 0) { progressClock.stop(); if (!operationFailed) progress.setValue(overall.complete()); }
            revalidate();
        });
    }
    private void receiveProgressValue(Integer percent) {
        SwingUtilities.invokeLater(() -> {
            if (closed.get() || running.get() == 0) return;
            boolean measured = percent != null && percent >= 0 && percent <= 100;
            if (measured) progress.setToolTipText("Overall progress is estimated. Current step: " + percent + "%");
        });
    }
    private void setActivity(String message) {
        status.setText(message); status.setToolTipText(message);
        activity.append(message + "\n");
        if (activity.getDocument().getLength() > 100000) activity.setText(activity.getText().substring(activity.getText().length() - 80000));
    }
    private void openFolder(String directory) { run("Opening folder", () -> { if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("Opening folders is unavailable on this system."); Desktop.getDesktop().open(new File(directory)); return null; }, ignored -> {}); }
    private void openLink(String url) { run("Opening mod downloads", () -> { if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("Open " + url + " in your browser."); Desktop.getDesktop().browse(java.net.URI.create(url)); return null; }, ignored -> {}); }
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
        JPanel page = DashboardTheme.transparent(new BorderLayout(0, 16)); page.setBorder(new EmptyBorder(0, 18, 12, 0));
        page.setName("page-" + title); page.getAccessibleContext().setAccessibleName(title); page.getAccessibleContext().setAccessibleDescription(description); return page;
    }
    private static JPanel content() { return DashboardTheme.surface(new BorderLayout(0, 12), 16); }
    private static JPanel toolbar(JComponent... components) { JPanel panel = DashboardTheme.transparent(new WrapLayout()); for (JComponent component : components) panel.add(component); panel.setAlignmentX(Component.LEFT_ALIGNMENT); return panel; }
    private static JButton button(String text, Runnable action) { JButton button = new JButton(text, DashboardTheme.icon("Add server".equals(text) ? "favorite" : text)); button.setToolTipText(text); button.getAccessibleContext().setAccessibleName(text); button.setMargin(new Insets(6, 10, 6, 10)); button.addActionListener(e -> action.run()); return button; }
    private static JButton iconButton(String text, String icon, Runnable action) { JButton button = button(text, action); button.setText(""); button.setIcon(DashboardTheme.icon(icon)); return button; }
    private static JButton primaryButton(String text, Runnable action) { return DashboardTheme.primary(button(text, action)); }
    private static JTextArea textArea(int rows) { JTextArea area = new JTextArea(rows, 30); area.setEditable(false); area.setOpaque(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setFont(UIManager.getFont("Label.font")); return area; }
    private static DefaultTableModel model(String... columns) { return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } }; }
    private static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model); table.setRowHeight(35); table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true); table.getTableHeader().setReorderingAllowed(false);
        javax.swing.table.DefaultTableCellRenderer text = new javax.swing.table.DefaultTableCellRenderer();
        text.putClientProperty("html.disable", Boolean.TRUE); table.setDefaultRenderer(Object.class, text);
        return table;
    }
    private static JScrollPane tableScroll(JTable table) { JScrollPane scroll = DashboardTheme.scroll(table); scroll.setColumnHeaderView(table.getTableHeader()); return scroll; }
    private static JPanel fields(Object... fields) {
        JPanel panel = DashboardTheme.transparent(new GridBagLayout()); panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < fields.length; i += 2) {
            GridBagConstraints label = new GridBagConstraints(); label.gridx = 0; label.gridy = i / 2; label.anchor = GridBagConstraints.WEST; label.insets = new Insets(5, 0, 5, 14);
            panel.add(new JLabel(String.valueOf(fields[i])), label);
            GridBagConstraints value = new GridBagConstraints(); value.gridx = 1; value.gridy = i / 2; value.weightx = 1; value.fill = GridBagConstraints.HORIZONTAL; value.insets = new Insets(5, 0, 5, 0);
            panel.add((Component) fields[i + 1], value);
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 16)); return panel;
    }
    private static final class WrapLayout extends FlowLayout {
        private int lastWidth = -1;
        WrapLayout() { super(FlowLayout.LEFT, 6, 3); }
        @Override public void layoutContainer(Container target) {
            super.layoutContainer(target);
            if (lastWidth != target.getWidth()) {
                lastWidth = target.getWidth();
                if (target.getParent() instanceof JComponent) ((JComponent) target.getParent()).revalidate();
            }
        }
        @Override public Dimension preferredLayoutSize(Container target) {
            int width = target.getWidth();
            if (width <= 0) return super.preferredLayoutSize(target);
            Insets inset = target.getInsets(); int available = width - inset.left - inset.right - getHgap() * 2;
            int rowWidth = 0, rowHeight = 0, height = getVgap() * 2, maxWidth = 0;
            for (Component child : target.getComponents()) if (child.isVisible()) {
                Dimension size = child.getPreferredSize();
                if (rowWidth > 0 && rowWidth + getHgap() + size.width > available) { height += rowHeight + getVgap(); maxWidth = Math.max(maxWidth, rowWidth); rowWidth = 0; rowHeight = 0; }
                rowWidth += (rowWidth == 0 ? 0 : getHgap()) + size.width; rowHeight = Math.max(rowHeight, size.height);
            }
            return new Dimension(Math.max(maxWidth, rowWidth) + inset.left + inset.right + getHgap() * 2, height + rowHeight + inset.top + inset.bottom);
        }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return; workers.shutdownNow();
        progressClock.stop(); if (launchChoices != null) launchChoices.close(); if (launchDialog != null) launchDialog.dispose();
        if (managedWorldPanel != null) managedWorldPanel.close();
        progress.setIndeterminate(false); progress.setVisible(false);
        actions.onProgress(null); actions.onProgressValue(null);
        try { downloadSubscription.close(); } catch (Exception ignored) { }
        if (console != null) console.close(); if (signInDialog != null) signInDialog.dispose();
    }
}
