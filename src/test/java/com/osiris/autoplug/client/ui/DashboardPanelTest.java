package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.osiris.autoplug.client.browser.ServerBrowserService;
import com.osiris.autoplug.client.browser.SavedServer;
import com.osiris.autoplug.client.browser.ServerStatus;
import com.osiris.autoplug.client.ui.LauncherActions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class DashboardPanelTest {
    @TempDir Path directory;

    @Test void firstLaunchUtilitySummaryAndActionsFitCompactProfilesPage() throws Exception {
        String summary = "Client utility mods install on first launch: Fabric API, Sodium, Entity Culling, ImmediatelyFast and Mod Menu, with required libraries. Exact-version availability is checked before launch.";
        ProfileInfo profile = new ProfileInfo("fabric", "Default (FABRIC)", "1.21.1", "FABRIC", "MODS", directory.toString(), false, summary, true);
        LauncherActions actions = new LauncherActions() { @Override public List<ProfileInfo> profiles() { return Collections.singletonList(profile); } };
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { FlatLightLaf.setup(); panel.set(new DashboardPanel(actions,
                new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)); });
        try {
            awaitUi(() -> ((JProgressBar)findNamed(panel.get(), "activity-progress")).getValue() == 100);
            SwingUtilities.invokeAndWait(() -> {
                findButton(panel.get(), "Profiles").doClick(0);
                ((JTable)findNamed(panel.get(), "profile-table")).setRowSelectionInterval(0, 0);
            });
            awaitUi(() -> ((JTextArea)findNamed(panel.get(), "profile-details")).getText().contains("No mods added yet."));
            SwingUtilities.invokeAndWait(() -> {
                panel.get().setSize(950, 620); for (int i = 0; i < 8; i++) layout(panel.get());
                JTextArea details = (JTextArea)findNamed(panel.get(), "profile-details");
                assertEquals(0, details.getCaretPosition(), "The background empty-collection note must not hide the beginning");
                assertTrue(details.getText().contains(summary)); assertTrue(details.getText().contains("Ready to launch"));
                JViewport viewport = (JViewport)SwingUtilities.getAncestorOfClass(JViewport.class, details);
                try {
                    Rectangle first = details.modelToView(0), last = details.modelToView(details.getDocument().getLength());
                    assertTrue(viewport.getViewRect().contains(first), "Directory and readiness remain visible");
                    assertTrue(viewport.getViewRect().contains(last), "The complete first-launch summary and empty-collection note fit");
                } catch (javax.swing.text.BadLocationException e) { throw new AssertionError(e); }
                assertTrue(findNamed(panel.get(), "profile-table").getHeight() >= 100, "Profile list remains useful");
                for (String label : new String[]{"Launch client", "Add JAR", "Modrinth", "CurseForge", "AutoPlug mods"}) {
                    AbstractButton action = findButton(panel.get(), label);
                    Rectangle bounds = SwingUtilities.convertRectangle(action.getParent(), action.getBounds(), panel.get());
                    assertTrue(bounds.x >= 0 && bounds.y >= 0 && bounds.width > 0 && bounds.height > 0
                            && bounds.x + bounds.width <= 950 && bounds.y + bounds.height <= 620, label + " stays visible");
                }
            });
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void progressKeepsLiveStepThroughOtherRefreshAndReleasesSubscriptions() throws Exception {
        AtomicReference<Consumer<String>> listener = new AtomicReference<>();
        AtomicReference<Consumer<Integer>> measured = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger settingsCalls = new AtomicInteger();
        LauncherActions actions = new LauncherActions() {
            @Override public void onProgress(Consumer<String> callback) { listener.set(callback); }
            @Override public void onProgressValue(Consumer<Integer> callback) { measured.set(callback); }
            @Override public SettingsInfo settings() { SettingsInfo result = new SettingsInfo(); result.account = "Account " + settingsCalls.incrementAndGet(); return result; }
            @Override public void addArtifact(String id, String path, String project) throws Exception { started.countDown(); assertTrue(release.await(4, TimeUnit.SECONDS)); }
        };
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(actions, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)));
        try {
            awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
            SwingUtilities.invokeAndWait(() -> panel.get().importArtifact(new ProfileInfo("test", "Test", "1.21.1", "VANILLA", "MODS", "", false), "example.jar", ""));
            assertTrue(started.await(4, TimeUnit.SECONDS));
            listener.get().accept("Resolving libraries https://example.invalid/library.jar");
            awaitUi(() -> containsText(panel.get(), "Resolving libraries"));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(((JProgressBar) findNamed(panel.get(), "activity-progress")).isIndeterminate());
                assertTrue(((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() < 100);
                assertEquals("https://example.invalid/library.jar", ((JTextField) findNamed(panel.get(), "download-source")).getText());
                findButton(panel.get(), "Reload").doClick();
            });
            awaitUi(() -> containsText(panel.get(), "Account 2"));
            SwingUtilities.invokeAndWait(() -> assertTrue(containsText(panel.get(), "Resolving libraries")));
            measured.get().accept(37);
            awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getToolTipText().contains("Current step: 37%"));
            AtomicInteger beforeRefresh = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(((JProgressBar) findNamed(panel.get(), "activity-progress")).isIndeterminate());
                beforeRefresh.set(((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue());
                findButton(panel.get(), "Reload").doClick();
            });
            awaitUi(() -> containsText(panel.get(), "Account 3"));
            SwingUtilities.invokeAndWait(() -> {
                JProgressBar bar = (JProgressBar) findNamed(panel.get(), "activity-progress");
                assertFalse(bar.isIndeterminate()); assertTrue(bar.getValue() >= beforeRefresh.get()); assertTrue(bar.getValue() < 100);
            });
            release.countDown(); awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
            Consumer<String> stale = listener.get();
            SwingUtilities.invokeAndWait(() -> panel.get().close()); assertNull(listener.get()); assertNull(measured.get());
            stale.accept("Should not appear");
            SwingUtilities.invokeAndWait(() -> assertFalse(containsText(panel.get(), "Should not appear")));
        } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void navigationAndSettingsReflowWithoutDiscardingEdits() throws Exception {
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(new LauncherActions() {}, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)));
        try {
            awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
            SwingUtilities.invokeAndWait(() -> {
                for (String view : new String[]{"Server Browser", "Worlds", "Profiles", "Server Manager", "Settings"}) {
                    findButton(panel.get(), view).doClick();
                    assertEquals(view, ((JLabel) findNamed(panel.get(), "navigation-title")).getText());
                    assertTrue(findNamed(panel.get(), "page-" + view).isVisible());
                }
                panel.get().setSize(1200, 820); for (int i = 0; i < 3; i++) layout(panel.get());
                Container grid = findNamed(panel.get(), "responsive-settings");
                assertEquals(grid.getComponent(0).getY(), grid.getComponent(1).getY());
                assertTrue(grid.getComponent(1).getX() > grid.getComponent(0).getX());
                assertFalse(findNamed(panel.get(), "advanced-runtime-fields").isVisible());
                findButton(panel.get(), "Advanced").doClick();
                assertTrue(findNamed(panel.get(), "advanced-runtime-fields").isVisible());
                JTextField runtime = findTextField(findNamed(panel.get(), "advanced-runtime-fields")); runtime.setText("/chosen/java");
                panel.get().setSize(950, 620); for (int i = 0; i < 3; i++) layout(panel.get());
                assertEquals(grid.getComponent(0).getX(), grid.getComponent(1).getX());
                assertTrue(grid.getComponent(1).getY() >= grid.getComponent(0).getHeight());
                assertTrue(runtime.getWidth() >= 80); assertEquals("/chosen/java", runtime.getText());
                assertNotNull(findButton(panel.get(), "Save settings"));
                AbstractButton importer = findAccessibleButton(panel.get(), "Import Minecraft");
                assertNotNull(importer); assertNotNull(importer.getIcon()); assertEquals("Import Minecraft", importer.getToolTipText());
            });
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void settingsMeasurementSurvivesChildLayoutInvalidation() throws Exception {
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(new LauncherActions() {}, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)));
        try {
            awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
            SwingUtilities.invokeAndWait(() -> {
                findButton(panel.get(), "Settings").doClick();
                findButton(panel.get(), "Advanced").doClick();
                Container responsive = findNamed(panel.get(), "responsive-settings");
                JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, responsive);
                for (Container group : new Container[]{findNamed(panel.get(), "settings-account"), findNamed(panel.get(), "settings-defaults"), findNamed(panel.get(), "settings-advanced"), (Container) responsive.getComponent(1)}) {
                    AtomicBoolean invalidated = new AtomicBoolean();
                    // Wrapped text can synchronously invalidate an ancestor while Swing measures it.
                    // Force that lifecycle event once, including in headless CI.
                    JTextArea wrapped = new JTextArea("A wrapped text measurement invalidates its containing layout.") {
                        @Override public Dimension getPreferredSize() {
                            if (getParent() != null && invalidated.compareAndSet(false, true)) {
                                LayoutManager manager = getParent().getLayout();
                                if (manager instanceof LayoutManager2) ((LayoutManager2) manager).invalidateLayout(getParent());
                            }
                            return super.getPreferredSize();
                        }
                    };
                    wrapped.setLineWrap(true); group.add(wrapped);
                    scroll.setSize(620, 350); scroll.doLayout();
                    assertTrue(invalidated.get(), "Regression must actually invalidate during measurement");
                    assertTrue(responsive.getPreferredSize().height > 0);
                    group.remove(wrapped);
                }
            });
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void serverCardJoinKeepsAddressThroughRefreshDuringDefaultPreparation() throws Exception {
        CountDownLatch preparing = new CountDownLatch(1), release = new CountDownLatch(1), launched = new CountDownLatch(1);
        AtomicBoolean prepared = new AtomicBoolean(); AtomicReference<List<String>> request = new AtomicReference<>();
        ProfileInfo client = new ProfileInfo("ready", "Default", "1.21.1", "VANILLA", "MODS", "", false);
        LauncherActions actions = new LauncherActions() {
            @Override public List<ProfileInfo> profiles() { return prepared.get() ? Arrays.asList(client) : Collections.emptyList(); }
            @Override public ProfileInfo ensureDefaultProfiles(String version) throws Exception {
                assertFalse(SwingUtilities.isEventDispatchThread()); assertEquals("1.21.1", version);
                preparing.countDown(); assertTrue(release.await(4, TimeUnit.SECONDS)); prepared.set(true); return client;
            }
            @Override public void launchProfile(String id, String host, int port) {
                assertFalse(SwingUtilities.isEventDispatchThread()); request.set(Arrays.asList(id, host, String.valueOf(port))); launched.countDown();
            }
        };
        SavedServer first = new SavedServer("Same name", "first.example.invalid:25566"), second = new SavedServer("Same name", "second.example.invalid:25567");
        Map<String, ServerStatus> statuses = new LinkedHashMap<>(); statuses.put(first.address, onlineStatus("First", "1.21.1")); statuses.put(second.address, onlineStatus("Second", "1.21.1"));
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(actions, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false,
                choices -> ((AbstractButton)findNamed(choices, "launch-choice-ready")).doClick())));
        try {
            awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
            SwingUtilities.invokeAndWait(() -> {
                panel.get().displayServers(Arrays.asList(first, second), statuses);
                findButton(findNamed(panel.get(), "server-" + first.address), "Join").doClick();
            });
            assertTrue(preparing.await(4, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                panel.get().displayServers(Arrays.asList(second, first), statuses);
                ((AbstractButton) findNamed(panel.get(), "select-server-" + second.address)).doClick();
            });
            release.countDown(); assertTrue(launched.await(4, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("ready", "first.example.invalid", "25566"), request.get());
        } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void serverCardsRejectStalePingsAndKeepCurrentVersionForKeyboardJoin() throws Exception {
        CountDownLatch launched = new CountDownLatch(1); AtomicReference<String> launchedProfile = new AtomicReference<>();
        LauncherActions actions = new LauncherActions() {
            @Override public List<ProfileInfo> profiles() { return Arrays.asList(
                    new ProfileInfo("newer", "Newer", "1.21.1", "VANILLA", "MODS", "", false),
                    new ProfileInfo("older", "Older", "1.20.1", "VANILLA", "MODS", "", false)); }
            @Override public void launchProfile(String id, String host, int port) { launchedProfile.set(id); launched.countDown(); }
        };
        SavedServer first = new SavedServer("First", "first.example.invalid"), removed = new SavedServer("Removed", "removed.example.invalid");
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(actions, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false,
                choices -> ((AbstractButton)findNamed(choices, "launch-choice-older")).doClick())));
        try {
            awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
            SwingUtilities.invokeAndWait(() -> {
                int oldGeneration = panel.get().displayServers(Arrays.asList(first, removed), Collections.emptyMap());
                assertTrue(containsText(findNamed(panel.get(), "server-" + first.address), "Checking…"));
                int generation = panel.get().displayServers(Arrays.asList(first), Collections.emptyMap());
                panel.get().showPing(generation, first, onlineStatus("Current message", "1.20.1"));
                panel.get().showPing(oldGeneration, first, onlineStatus("Stale message", "1.21.1"));
                panel.get().showPing(oldGeneration, removed, onlineStatus("Removed result", "1.21.1"));
                Container card = findNamed(panel.get(), "server-" + first.address);
                assertTrue(containsText(card, "Current message")); assertTrue(containsText(card, "1.20.1"));
                assertFalse(containsText(card, "Stale message")); assertNull(findNamed(panel.get(), "server-" + removed.address));
                AbstractButton select = (AbstractButton) findNamed(card, "select-server-" + first.address);
                Object binding = select.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
                assertNotNull(binding); select.getActionMap().get(binding).actionPerformed(new java.awt.event.ActionEvent(select, 0, "keyboard"));
            });
            assertTrue(launched.await(4, TimeUnit.SECONDS)); assertEquals("older", launchedProfile.get());
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void serverCardsReflowFilterAndDisplayLiteralTextWithoutLosingControls() throws Exception {
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { FlatLightLaf.setup(); panel.set(new DashboardPanel(new LauncherActions() {}, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)); });
        try {
            awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
            SwingUtilities.invokeAndWait(() -> {
                SavedServer first = new SavedServer("<html><b>Literal 雪</b> — A very long Minecraft favorite name", "first.example.invalid"), second = new SavedServer("Other", "second.example.invalid");
                Map<String, ServerStatus> statuses = new LinkedHashMap<>();
                statuses.put(first.address, onlineStatus("<html><img src='https://example.invalid/not-a-logo'>\nWelcome 雪", "Paper 1.21.1"));
                statuses.put(second.address, new ServerStatus(false, "", "", 0, 0, 0, 0, "Connection refused"));
                panel.get().displayServers(Arrays.asList(first, second), statuses);
                Container firstCard = findNamed(panel.get(), "server-" + first.address), secondCard = findNamed(panel.get(), "server-" + second.address);
                AbstractButton title = (AbstractButton) findNamed(firstCard, "select-server-" + first.address);
                assertEquals(first.name, title.getText()); assertNull(title.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
                assertTrue(containsText(firstCard, "<html><img")); assertTrue(containsText(firstCard, "Players: 12 / 40"));
                assertTrue(containsText(secondCard, "Unavailable")); assertTrue(containsText(secondCard, "Connection refused"));
                assertTrue(containsText(secondCard, "Latency: —"));
                for (String action : new String[]{"Ping selected", "Copy address", "Edit server", "Remove"}) {
                    AbstractButton button = findAccessibleButton(firstCard, action); assertNotNull(button); assertNotNull(button.getIcon()); assertTrue(button.isFocusable());
                }
                panel.get().setSize(1200, 820); for (int i = 0; i < 5; i++) layout(panel.get());
                assertEquals(firstCard.getX(), secondCard.getX()); assertTrue(secondCard.getY() > firstCard.getY());
                ((AbstractButton) findNamed(secondCard, "select-server-" + second.address)).doClick();
                ((JComboBox<?>) findNamed(panel.get(), "server-sort")).setSelectedItem("Name");
                assertTrue(((AbstractButton) findNamed(secondCard, "select-server-" + second.address)).isSelected());
                panel.get().setSize(950, 620); for (int i = 0; i < 5; i++) layout(panel.get());
                assertEquals(firstCard.getX(), secondCard.getX()); assertNotEquals(firstCard.getY(), secondCard.getY());
                for (String action : new String[]{"Join", "Ping selected", "Copy address", "Edit server", "Remove"}) {
                    AbstractButton button = findAccessibleButton(firstCard, action);
                    Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), firstCard);
                    assertTrue(bounds.width > 0 && bounds.x >= 0 && bounds.x + bounds.width <= firstCard.getWidth(), action + " must fit within its card");
                }
                JTextField search = (JTextField) findNamed(panel.get(), "server-search"); search.setText("first.example");
                assertTrue(firstCard.isVisible()); assertFalse(secondCard.isVisible()); assertTrue(title.isSelected());
                search.setText("no matching favorite"); assertFalse(firstCard.isVisible()); assertTrue(containsText(panel.get(), "No favorites match"));
                search.setText(""); panel.get().displayServers(Collections.emptyList(), Collections.emptyMap());
                assertEquals(0, findNamed(panel.get(), "server-cards").getComponentCount()); assertTrue(containsText(panel.get(), "Add a server or import"));
            });
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    private static ServerStatus onlineStatus(String message, String version) { return new ServerStatus(true, message, version, 12, 40, 0, 38, "SUCCESS"); }

    @Test void parallelDownloadCompletionKeepsProgressVisibleUntilLastTransferEnds() throws Exception {
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(new LauncherActions() {}, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)));
        try {
            awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
            try (com.osiris.autoplug.client.launcher.DownloadProgress.Transfer first = com.osiris.autoplug.client.launcher.DownloadProgress.begin("First asset", "https://example.invalid/first.jar");
                 com.osiris.autoplug.client.launcher.DownloadProgress.Transfer second = com.osiris.autoplug.client.launcher.DownloadProgress.begin("Second asset", "https://example.invalid/second.jar")) {
                first.complete(20, 20);
                awaitUi(() -> containsText(panel.get(), "First asset — complete"));
                SwingUtilities.invokeAndWait(() -> {
                    JProgressBar bar = (JProgressBar) findNamed(panel.get(), "activity-progress"); assertTrue(bar.isVisible()); assertFalse(bar.isIndeterminate()); assertTrue(bar.getValue() < 100);
                });
                second.complete(30, 30);
                awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
                SwingUtilities.invokeAndWait(() -> assertEquals("https://example.invalid/second.jar", ((JTextField) findNamed(panel.get(), "download-source")).getText()));
            }
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void worldMetadataReportsUnknownAndBoundedSizeTruthfully() {
        WorldInfo unknown = new WorldInfo("unknown", "Unknown", "", "", "", "", false, true, "");
        assertEquals("Additional save metadata unavailable", DashboardPanel.worldMetadata(unknown));
        WorldInfo bounded = new WorldInfo("known", "Known", "", "", "", "", false, true, "1.21.1", true, -1, 1536, false, Arrays.asList("vanilla", "file/pack"), true, false, true);
        String detail = DashboardPanel.worldMetadata(bounded);
        assertTrue(detail.contains("Last played: Unknown")); assertTrue(detail.contains("Size: At least 1.5 KiB"));
        assertTrue(detail.contains("Modded")); assertTrue(detail.contains("Cheats: On")); assertTrue(detail.contains("Hardcore: Off")); assertTrue(detail.contains("Datapacks: 2"));
        WorldInfo partial = new WorldInfo("partial", "Partial", "", "", "", "", false, true, "1.21.1", false, -1, 1588, false, Arrays.asList("vanilla"), false, false, true);
        assertTrue(DashboardPanel.worldMetadata(partial).contains("At least 1.5 KiB"));
    }

    @Test void versionSelectionDoesNotConfuseVersionPrefixes() {
        assertTrue(DashboardPanel.versionMatches("Paper 1.21.1", "1.21.1"));
        assertFalse(DashboardPanel.versionMatches("Paper 1.21.11", "1.21.1"));
        assertFalse(DashboardPanel.versionMatches("1.21", "1.2"));
        assertFalse(DashboardPanel.versionMatches(null, "1.21"));
        assertEquals("1.21.1", DashboardPanel.gameVersionFromStatus("Paper 1.21.1"));
        assertEquals("", DashboardPanel.gameVersionFromStatus("1.8 - 1.21.1"));
        assertEquals("1.21.5-pre2", DashboardPanel.gameVersionFromStatus("Paper 1.21.5-pre2"));
        assertEquals("25w34a", DashboardPanel.gameVersionFromStatus("25w34a"));
        assertFalse(DashboardPanel.versionMatches("1.21.5-pre2", "1.21.5"));
    }

    @Test void automaticJoinRequiresClientTypeExactVersionLoaderAndReadyWorkingProfile() {
        ProfileInfo otherLoader = new ProfileInfo("wrong", "Forge", "1.21.1", "FORGE", "MODS", "", false);
        ProfileInfo template = new ProfileInfo("template", "Base", "1.21.1", "FABRIC", "MODS", "", true);
        ProfileInfo pending = new ProfileInfo("pending", "Pending", "1.21.1", "FABRIC", "MODS", "", false, "Review", false);
        ProfileInfo server = new ProfileInfo("server", "Server", "1.21.1", "FABRIC", "MODS_SERVER", "", false);
        ProfileInfo ready = new ProfileInfo("ready", "Client", "1.21.1", "FABRIC", "MODS", "", false);
        List<ProfileInfo> candidates = Arrays.asList(otherLoader, template, pending, server, ready);
        assertSame(ready, DashboardPanel.matchingClientProfile(candidates, "1.21.1", "fabric"));
        assertNull(DashboardPanel.matchingClientProfile(candidates, "1.21", "FABRIC"));
        assertNull(DashboardPanel.matchingClientProfile(candidates, "", "FABRIC"));
    }

    @Test void loadsServicesOffEventThreadAndBuildsAllFiveViews() throws Exception {
        AtomicBoolean onEventThread = new AtomicBoolean(); CountDownLatch calls = new CountDownLatch(3);
        LauncherActions actions = fixtures(onEventThread, calls);
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(actions, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)));
        try {
            assertTrue(calls.await(4, TimeUnit.SECONDS)); assertFalse(onEventThread.get());
            SwingUtilities.invokeAndWait(() -> {
                for (String view : new String[]{"Server Browser", "Worlds", "Profiles", "Server Manager", "Settings"})
                    assertNotNull(findButton(panel.get(), view), "Missing navigation: " + view);
                assertNotNull(findButton(panel.get(), "Create world"));
                assertNotNull(findButton(panel.get(), "Sign in with Microsoft"));
            });
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void localWorldLaunchUsesRecordedVersionWithoutProfileEulaOrSharing() throws Exception {
        CountDownLatch launched = new CountDownLatch(1);
        AtomicReference<List<String>> request = new AtomicReference<>(); AtomicBoolean onEventThread = new AtomicBoolean();
        String result = "Minecraft 1.12.2 started with automatic entry for Old cottage.";
        ProfileInfo vanilla = new ProfileInfo("local-vanilla", "Vanilla", "1.12.2", "VANILLA", "MODS", "", false);
        LauncherActions actions = new LauncherActions() {
            @Override public List<WorldInfo> worlds() {
                return Arrays.asList(new WorldInfo("local:cottage", "Old cottage", "", "", directory.toString(), "", false, true, "1.12.2"));
            }
            @Override public List<ProfileInfo> profiles() {
                return Arrays.asList(new ProfileInfo("newer-profile", "Different version", "1.21.1", "FABRIC", "MODS", "", false), vanilla);
            }
            @Override public ProfileInfo ensureDefaultProfiles(String version) { assertEquals("1.12.2", version); return vanilla; }
            @Override public String launchLocalWorld(String id, String version) {
                onEventThread.set(SwingUtilities.isEventDispatchThread()); request.set(Arrays.asList(id, version)); launched.countDown(); return result;
            }
            @Override public void launchWorld(String id, boolean share) { fail("A local save must not start a managed server"); }
        };
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(actions, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false,
                choices -> ((AbstractButton)findNamed(choices, "launch-choice-local-vanilla")).doClick())));
        try {
            awaitUi(() -> findNamed(panel.get(), "world-local:cottage") != null);
            SwingUtilities.invokeAndWait(() -> {
                Container card = findNamed(panel.get(), "world-local:cottage");
                assertTrue(containsText(card, "Singleplayer")); assertTrue(containsText(card, "Minecraft 1.12.2"));
                assertNull(findButton(card, "Share")); assertNull(findButton(card, "Minecraft EULA")); assertNull(findButton(card, "Play locally"));
                findButton(card, "Launch").doClick();
            });
            assertTrue(launched.await(4, TimeUnit.SECONDS)); assertFalse(onEventThread.get());
            assertEquals(Arrays.asList("local:cottage", "1.12.2"), request.get());
            awaitUi(() -> containsText(panel.get(), result));
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void unknownLocalVersionIsVisibleAndManagedWorldKeepsItsOwnControls() throws Exception {
        CountDownLatch managedLaunch = new CountDownLatch(1);
        ProfileInfo client = new ProfileInfo("client", "Client", "1.21.1", "VANILLA", "MODS", "", false);
        LauncherActions actions = new LauncherActions() {
            @Override public List<WorldInfo> worlds() {
                return Arrays.asList(new WorldInfo("local:old", "Old save", "", "", directory.toString(), "", false, true, ""),
                        new WorldInfo("managed", "Friends", "server", "client", directory.toString(), "", false, false, "1.21.1"));
            }
            @Override public List<ProfileInfo> profiles() { return Arrays.asList(client); }
            @Override public ProfileInfo ensureDefaultProfiles(String version) { return client; }
            @Override public void launchWorld(String id, boolean share) { assertEquals("managed", id); assertFalse(share); managedLaunch.countDown(); }
            @Override public String launchLocalWorld(String id, String version) { fail("Managed play must not launch a local save"); return ""; }
        };
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(actions, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false,
                choices -> ((AbstractButton)findNamed(choices, "launch-choice-client")).doClick())));
        try {
            awaitUi(() -> findNamed(panel.get(), "world-managed") != null);
            SwingUtilities.invokeAndWait(() -> {
                Container local = findNamed(panel.get(), "world-local:old");
                assertTrue(containsText(local, "version unknown")); assertNotNull(findButton(local, "Launch"));
                assertFalse(containsText(local, "Server:")); assertNull(findButton(local, "Share"));
                Container managed = findNamed(panel.get(), "world-managed");
                assertTrue(containsText(managed, "Managed world")); assertNotNull(findButton(managed, "Share"));
                assertNotNull(findButton(managed, "Minecraft EULA")); assertNull(findButton(managed, "Launch"));
                findButton(managed, "Play locally").doClick();
            });
            assertTrue(managedLaunch.await(4, TimeUnit.SECONDS));
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void consoleReceivesBackgroundLogsAndReleasesListenerOnClose() throws Exception {
        java.util.List<com.osiris.jlib.events.MessageEvent<com.osiris.jlib.logger.Message>> before =
                new java.util.ArrayList<>(com.osiris.jlib.logger.AL.actionsOnMessageEvent);
        AtomicReference<ServerConsolePanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new ServerConsolePanel(new JPanel())));
        try {
            java.util.List<com.osiris.jlib.events.MessageEvent<com.osiris.jlib.logger.Message>> added =
                    new java.util.ArrayList<>(com.osiris.jlib.logger.AL.actionsOnMessageEvent); added.removeAll(before);
            assertEquals(1, added.size());
            added.get(0).executeOnEvent(new com.osiris.jlib.logger.Message(com.osiris.jlib.logger.Message.Type.INFO, "Background task finished"));
            AtomicReference<String> output = new AtomicReference<>(""); long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!output.get().contains("Background task finished") && System.nanoTime() < deadline) {
                Thread.sleep(30); SwingUtilities.invokeAndWait(() -> output.set(panel.get().txtConsole.getText()));
            }
            assertTrue(output.get().contains("Background task finished"));
            SwingUtilities.invokeAndWait(() -> panel.get().close());
            assertFalse(com.osiris.jlib.logger.AL.actionsOnMessageEvent.contains(added.get(0)));
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void jarImportKeepsSelectedProfileAndOptionalProjectAndRunsOffEventThread() throws Exception {
        CountDownLatch imported = new CountDownLatch(1); AtomicBoolean onEventThread = new AtomicBoolean();
        AtomicReference<List<String>> request = new AtomicReference<>();
        LauncherActions actions = new LauncherActions() {
            @Override public void addArtifact(String id, String path, String project) {
                onEventThread.set(SwingUtilities.isEventDispatchThread()); request.set(Arrays.asList(id, path, project)); imported.countDown();
            }
        };
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        ProfileInfo selected = new ProfileInfo("selected-profile", "My pack", "1.21.1", "FABRIC", "MODS", directory.toString(), false);
        String jar = directory.resolve("a mod with spaces.jar").toString();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new DashboardPanel(actions, new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false));
            panel.get().importArtifact(selected, jar, "sodium");
        });
        try {
            assertTrue(imported.await(4, TimeUnit.SECONDS)); assertFalse(onEventThread.get());
            assertEquals(Arrays.asList("selected-profile", jar, "sodium"), request.get());
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    /** Offscreen fixture preview; does not initialize AutoPlug, sign in, or contact public servers. */
    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args.length == 0 ? "target/dashboard-preview" : args[0]); Files.createDirectories(output);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 1200;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 820;
        Path data = Files.createTempDirectory("autoplug-dashboard-preview-");
        AtomicReference<DashboardPanel> panel = new AtomicReference<>(); CountDownLatch calls = new CountDownLatch(3);
        SwingUtilities.invokeAndWait(() -> {
            if (args.length > 3 && "dark".equalsIgnoreCase(args[3])) FlatDarkLaf.setup(); else FlatLightLaf.setup();
            com.osiris.autoplug.client.utils.GD.TARGET = com.osiris.autoplug.client.Target.MINECRAFT_SERVER;
            panel.set(new DashboardPanel(fixtures(new AtomicBoolean(), calls), new ServerBrowserService(data.resolve("servers.json"), data.resolve("servers.dat")), true));
        });
        calls.await(4, TimeUnit.SECONDS);
        // The empty local import finishes before injecting render-only examples; no server is pinged.
        awaitUi(() -> ((JProgressBar) findNamed(panel.get(), "activity-progress")).getValue() == 100);
        SwingUtilities.invokeAndWait(() -> {
            SavedServer online = new SavedServer("Quiet Cove", "cove.example.invalid"), unavailable = new SavedServer("Skyline Survival", "skyline.example.invalid:25566"), checking = new SavedServer("Weekend Adventure", "weekend.example.invalid");
            Map<String, ServerStatus> statuses = new LinkedHashMap<>();
            statuses.put(online.address, onlineStatus("A relaxed place to build, explore, and meet friends.\nNew adventures every weekend.", "Paper 1.21.1"));
            statuses.put(unavailable.address, new ServerStatus(false, "", "", 0, 0, 0, 0, "Could not reach the server. Try again when it is online."));
            panel.get().displayServers(Arrays.asList(online, unavailable, checking), statuses);
        });
        try {
            for (String view : new String[]{"Server Browser", "Worlds", "Profiles", "Server Manager", "Settings"}) {
                SwingUtilities.invokeAndWait(() -> findButton(panel.get(), view).doClick());
                Thread.sleep(50);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                    panel.get().setSize(width, height); for (int pass = 0; pass < 3; pass++) layout(panel.get());
                    BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = bitmap.createGraphics();
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    panel.get().printAll(graphics); graphics.dispose();
                    ImageIO.write(bitmap, "png", output.resolve(view.toLowerCase().replace(' ', '-') + ".png").toFile());
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    private static LauncherActions fixtures(AtomicBoolean onEventThread, CountDownLatch calls) {
        return new LauncherActions() {
            private void called() { if (SwingUtilities.isEventDispatchThread()) onEventThread.set(true); calls.countDown(); }
            @Override public List<ProfileInfo> profiles() {
                called(); return Arrays.asList(
                        new ProfileInfo("vanilla", "Everyday Minecraft", "1.21.1", "VANILLA", "MODS", "/profiles/everyday", false),
                        new ProfileInfo("fabric", "Exploration pack", "1.21.1", "FABRIC", "MODS", "/profiles/exploration", false),
                        new ProfileInfo("paper", "Friends server", "1.21.1", "PAPER", "PLUGINS", "/profiles/friends", false),
                        new ProfileInfo("template", "My base template", "1.21.1", "FABRIC", "MODS", "/profiles/base", true));
            }
            @Override public List<WorldInfo> worlds() {
                called(); return Arrays.asList(new WorldInfo("local:cottage", "Lakeside cottage", "", "", "/.minecraft/saves/Lakeside cottage", "", false, true, "1.21.1", false, 1791072000000L, 184549376, true, Arrays.asList("vanilla"), false, false, true),
                        new WorldInfo("cove", "Quiet Cove", "paper", "vanilla", "/worlds/quiet-cove", "", false, false, "1.21.1", true, 1790990000000L, 597688320, true, Arrays.asList("vanilla", "file/terrain"), true, false, false),
                        new WorldInfo("local:archive", "An old adventure", "", "", "/.minecraft/saves/An old adventure", "", false, true, ""));
            }
            @Override public SettingsInfo settings() { called(); SettingsInfo settings = new SettingsInfo(); settings.account = "Offline · Alex"; settings.defaultProfile = "vanilla"; settings.java17 = "/runtimes/java-17/bin/java"; settings.java21 = "/runtimes/java-21/bin/java"; return settings; }
        };
    }
    private static AbstractButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof AbstractButton && text.equals(((AbstractButton) component).getText())) return (AbstractButton) component;
            if (component instanceof Container) { AbstractButton found = findButton((Container) component, text); if (found != null) return found; }
        }
        return null;
    }
    private static AbstractButton findAccessibleButton(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (component instanceof AbstractButton && name.equals(component.getAccessibleContext().getAccessibleName())) return (AbstractButton) component;
            if (component instanceof Container) { AbstractButton found = findAccessibleButton((Container) component, name); if (found != null) return found; }
        } return null;
    }
    private static JTextField findTextField(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTextField) return (JTextField) component;
            if (component instanceof Container) { JTextField found = findTextField((Container) component); if (found != null) return found; }
        } return null;
    }
    private static Container findNamed(Container container, String name) {
        if (name.equals(container.getName())) return container;
        for (Component child : container.getComponents()) if (child instanceof Container) {
            Container found = findNamed((Container) child, name); if (found != null) return found;
        }
        return null;
    }
    private static boolean containsText(Container container, String text) {
        if (container instanceof JLabel && ((JLabel) container).getText() != null && ((JLabel) container).getText().contains(text)) return true;
        if (container instanceof JTextArea && ((JTextArea) container).getText().contains(text)) return true;
        for (Component child : container.getComponents()) if (child instanceof Container && containsText((Container) child, text)) return true;
        return false;
    }
    private static void awaitUi(java.util.function.BooleanSupplier condition) throws Exception {
        AtomicBoolean satisfied = new AtomicBoolean(); long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!satisfied.get() && System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> satisfied.set(condition.getAsBoolean()));
            if (!satisfied.get()) Thread.sleep(20);
        }
        assertTrue(satisfied.get(), "Dashboard update did not arrive");
    }
    private static void layout(Container container) { container.doLayout(); for (Component component : container.getComponents()) if (component instanceof Container) layout((Container) component); }
}
