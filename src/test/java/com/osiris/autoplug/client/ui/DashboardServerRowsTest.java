package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.browser.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DashboardServerRowsTest {
    @TempDir Path directory;

    @Test void repeatedPingsKeepNewestStatusAndIconAndNeverUpdateReplacedCards() throws Exception {
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(new LauncherActions() {},
                new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)));
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean[] ready = {false};
            while (!ready[0] && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> ready[0] = ((JProgressBar)find(panel.get(), "activity-progress")).getValue() == 100);
                if (!ready[0]) Thread.sleep(15);
            }
            assertTrue(ready[0]);
            SwingUtilities.invokeAndWait(() -> {
                SavedServer server = new SavedServer("Fixture", "fixture.example.invalid");
                BufferedImage oldIcon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
                BufferedImage newIcon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
                ServerStatus old = new ServerStatus(true, "Older response", "1.20.1", 1, 10, 763, 50, "SUCCESS", oldIcon);
                ServerStatus fresh = new ServerStatus(true, "Newest response", "1.21.1", 2, 10, 767, 25, "SUCCESS", newIcon);
                int generation = panel.get().displayServers(Collections.singletonList(server), Collections.singletonMap(server.address, old));
                long first = panel.get().beginServerPing(server), second = panel.get().beginServerPing(server);
                JLabel icon = (JLabel)find(panel.get(), "server-icon");
                assertFalse(icon.getIcon() instanceof ImageIcon, "A pending check clears the old favicon");
                panel.get().showPing(generation, server, second, fresh);
                panel.get().showPing(generation, server, first, old);
                assertSame(newIcon, ((ImageIcon)icon.getIcon()).getImage());
                assertEquals("Newest response", ((JTextArea)find(panel.get(), "server-message")).getText());
                int replacement = panel.get().displayServers(Collections.singletonList(server), Collections.emptyMap());
                assertNotEquals(generation, replacement);
                panel.get().showPing(generation, server, second, fresh);
                assertEquals("Checking…", ((JLabel)find(panel.get(), "server-status")).getText());
                long latest = panel.get().beginServerPing(server);
                panel.get().showPing(replacement, server, latest, new ServerStatus(false, "", "", 0, 0, 0, 0, "Offline"));
                assertFalse(((JLabel)find(panel.get(), "server-icon")).getIcon() instanceof ImageIcon);
                panel.get().close();
                panel.get().showPing(replacement, server, latest, fresh);
                assertEquals("Offline · Unavailable", ((JLabel)find(panel.get(), "server-status")).getText());
            });
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void rememberedCompatibleLoaderIsOfferedEvenWhenGlobalDefaultHasAnotherVersion() throws Exception {
        LauncherActions.ProfileInfo global = new LauncherActions.ProfileInfo("global", "Global", "1.21.1", "VANILLA", "MODS", "", false);
        LauncherActions.ProfileInfo previous = new LauncherActions.ProfileInfo("previous", "Previous", "1.20.1", "FABRIC", "MODS", "", false);
        CountDownLatch launched = new CountDownLatch(1); AtomicReference<String> selected = new AtomicReference<>();
        LauncherActions actions = new LauncherActions() {
            @Override public java.util.List<ProfileInfo> profiles() { return Arrays.asList(global, previous); }
            @Override public SettingsInfo settings() { SettingsInfo result = new SettingsInfo(); result.defaultProfile = global.id; return result; }
            @Override public String preferredProfile(String target) { assertEquals("server:remembered.example.invalid", target); return previous.id; }
            @Override public void launchProfile(String id, String host, int port) { selected.set(id); launched.countDown(); }
            @Override public ProfileInfo cloneProfile(String id, String name, String version, String loader) { fail("A compatible remembered profile must not require migration"); return null; }
        };
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DashboardPanel(actions,
                new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false,
                choices -> ((AbstractButton)find(choices, "launch-choice-previous")).doClick(0))));
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean[] ready = {false};
            while (!ready[0] && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> ready[0] = ((JProgressBar)find(panel.get(), "activity-progress")).getValue() == 100);
                if (!ready[0]) Thread.sleep(15);
            }
            assertTrue(ready[0]);
            SwingUtilities.invokeAndWait(() -> {
                SavedServer server = new SavedServer("Remembered", "remembered.example.invalid");
                panel.get().displayServers(Collections.singletonList(server), Collections.singletonMap(server.address,
                        new ServerStatus(true, "Welcome", "1.20.1", 1, 20, 763, 20, "SUCCESS")));
                AbstractButton button = (AbstractButton)find(panel.get(), "select-server-" + server.address);
                Object key = button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
                button.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(button, 0, "join"));
            });
            assertTrue(launched.await(4, TimeUnit.SECONDS)); assertEquals(previous.id, selected.get());
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    @Test void rowsDefaultToMostRecentlyJoinedWithRealIconsAndDistinctStatusColors() throws Exception {
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { FlatLightLaf.setup(); panel.set(new DashboardPanel(new LauncherActions() {},
                new ServerBrowserService(directory.resolve("servers.json"), directory.resolve("servers.dat")), false)); });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean[] ready = {false};
            while (!ready[0] && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> ready[0] = ((JProgressBar) find(panel.get(), "activity-progress")).getValue() == 100);
                if (!ready[0]) Thread.sleep(15);
            }
            assertTrue(ready[0]);
            SwingUtilities.invokeAndWait(() -> {
                SavedServer older = new SavedServer("Minecraft Server", "older.example.invalid", 1000);
                SavedServer latest = new SavedServer("Recent favorite", "recent.example.invalid", 2000);
                BufferedImage favicon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB); favicon.setRGB(1, 1, 0xFF237C42);
                Map<String, ServerStatus> statuses = new HashMap<>();
                statuses.put(older.address, new ServerStatus(false, "", "", 0, 0, 0, 0, "Connection refused"));
                statuses.put(latest.address, new ServerStatus(true, "Welcome", "1.21.1", 3, 20, 767, 40, "SUCCESS", favicon));
                panel.get().displayServers(Arrays.asList(older, latest), statuses);
                assertEquals("Last joined", ((JComboBox<?>) find(panel.get(), "server-sort")).getSelectedItem());
                Container grid = find(panel.get(), "server-cards"), newest = find(panel.get(), "server-" + latest.address), oldest = find(panel.get(), "server-" + older.address);
                assertSame(newest, grid.getComponent(0));
                assertEquals("older.example.invalid", ((AbstractButton)find(oldest,"select-server-" + older.address)).getText());
                JLabel shownIcon = (JLabel)find(newest, "server-icon"); assertTrue(shownIcon.getIcon() instanceof ImageIcon);
                assertSame(favicon, ((ImageIcon)shownIcon.getIcon()).getImage());
                for (int width : new int[]{1200, 950}) {
                    panel.get().setSize(width, width == 950 ? 620 : 820); for (int i = 0; i < 6; i++) layout(panel.get());
                    assertEquals(newest.getX(), oldest.getX()); assertTrue(oldest.getY() >= newest.getY() + newest.getHeight());
                    assertTrue(newest.getWidth() > 500);
                    JLabel online = (JLabel)find(newest, "server-status"), offline = (JLabel)find(oldest, "server-status");
                    paint(online); paint(offline); assertNotEquals(online.getForeground(), offline.getForeground());
                    for (Component child : buttons(newest)) {
                        Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), newest);
                        assertTrue(bounds.width > 0 && bounds.height > 0 && bounds.x >= 0 && bounds.y >= 0
                                && bounds.x + bounds.width <= newest.getWidth() && bounds.y + bounds.height <= newest.getHeight(), "Controls remain within full-width card");
                    }
                }
                ((JTextField)find(panel.get(), "server-search")).setText("older"); assertFalse(newest.isVisible()); assertTrue(oldest.isVisible());
                ((JTextField)find(panel.get(), "server-search")).setText(""); assertSame(newest, grid.getComponent(0));
            });
        } finally { SwingUtilities.invokeAndWait(() -> panel.get().close()); }
    }

    private static void paint(JComponent component) {
        BufferedImage image = new BufferedImage(Math.max(1, component.getWidth()), Math.max(1, component.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics(); try { component.paint(graphics); } finally { graphics.dispose(); }
    }
    private static java.util.List<Component> buttons(Container container) {
        java.util.List<Component> result = new ArrayList<>();
        for (Component child : container.getComponents()) { if (child instanceof AbstractButton) result.add(child); if (child instanceof Container) result.addAll(buttons((Container)child)); }
        return result;
    }
    private static Container find(Container container, String name) {
        if (name.equals(container.getName())) return container;
        for (Component child : container.getComponents()) if (child instanceof Container) { Container found = find((Container)child, name); if (found != null) return found; }
        return null;
    }
    private static void layout(Container container) { container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container) layout((Container)child); }
}
