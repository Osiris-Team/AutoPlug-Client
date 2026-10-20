package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.browser.ServerBrowserService;
import com.osiris.autoplug.client.profiles.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Native creation dialog plus actual persisted ProfileStore; no game, gateway or identity provider. */
class DashboardProfileCompatibilityTest {
    @TempDir Path temp;
    @Test void nativeCreateDialogFiltersTypeChangesAndPersistsSelectedPluginLoader() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ProfileStore store = new ProfileStore(temp.resolve("profiles")); CountDownLatch created = new CountDownLatch(1);
        AtomicReference<Profile> saved = new AtomicReference<>(); AtomicReference<JFrame> frame = new AtomicReference<>();
        AtomicReference<DashboardPanel> panel = new AtomicReference<>();
        LauncherActions actions = new LauncherActions() {
            @Override public ProfileInfo createProfile(String name, String version, String loader, String type, boolean template) throws Exception {
                assertFalse(SwingUtilities.isEventDispatchThread());
                Profile profile = store.create(name, version, loader, ProfileType.valueOf(type)); saved.set(profile); created.countDown();
                return null;
            }
        };
        try {
            SwingUtilities.invokeAndWait(() -> {
                FlatLightLaf.setup(); DashboardPanel p = new DashboardPanel(actions, new ServerBrowserService(temp.resolve("servers.json"), temp.resolve("servers.dat")), false);
                JFrame f = new JFrame("Profile fixture - no game"); f.setContentPane(p); f.setSize(950, 620); f.setVisible(true); panel.set(p); frame.set(f);
                DashboardAuthenticationLifecycleTest.button(p, "Profiles").doClick(0);
            });
            SwingUtilities.invokeLater(() -> DashboardAuthenticationLifecycleTest.button(panel.get(), "Create").doClick(0));
            AtomicReference<JDialog> dialog = new AtomicReference<>();
            DashboardAuthenticationLifecycleTest.awaitUi(() -> {
                for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && "Create profile".equals(((JDialog) w).getTitle())) { dialog.set((JDialog) w); return true; }
                return false;
            });
            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> type = combo(dialog.get(), "MODS"), loader = combo(dialog.get(), "FABRIC"); assertNotNull(type); assertNotNull(loader);
                select(type, "PLUGINS"); assertFalse(contains(loader, "FABRIC")); assertTrue(contains(loader, "PAPER"));
                select(type, "MODS_SERVER"); assertFalse(contains(loader, "PAPER")); assertTrue(contains(loader, "FABRIC"));
                select(type, "MODS"); assertFalse(contains(loader, "PAPER")); assertTrue(contains(loader, "VANILLA"));
                select(type, "PLUGINS"); select(loader, "PAPER"); fillName(dialog.get());
                String output = System.getProperty("autoplug.derived.uiEvidence");
                if (output != null) try {
                    BufferedImage image = new BufferedImage(dialog.get().getWidth(), dialog.get().getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = image.createGraphics(); dialog.get().paint(g); g.dispose();
                    Files.createDirectories(Paths.get(output)); ImageIO.write(image, "png", Paths.get(output, "profile-plugin-dialog.png").toFile());
                } catch (Exception e) { throw new AssertionError(e); }
                DashboardAuthenticationLifecycleTest.findOption(dialog.get()).setValue(JOptionPane.OK_OPTION);
            });
            assertTrue(created.await(5, TimeUnit.SECONDS)); Profile actual = store.get(saved.get().id);
            assertEquals(ProfileType.PLUGINS, actual.type); assertEquals("PAPER", actual.loader); assertEquals("Native fixture", actual.name);
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (panel.get() != null) panel.get().close(); for (Window w : Window.getWindows()) if (w instanceof JDialog && (w.getOwner() == frame.get() || SwingUtilities.isDescendingFrom(w, frame.get()))) w.dispose(); if (frame.get() != null) frame.get().dispose(); });
        }
    }
    private static JComboBox<?> combo(Container root, String item) {
        for (Component c : root.getComponents()) { if (c instanceof JComboBox && contains((JComboBox<?>) c, item)) return (JComboBox<?>) c;
            if (c instanceof Container) { JComboBox<?> result = combo((Container) c, item); if (result != null) return result; } }
        return null;
    }
    private static boolean contains(JComboBox<?> combo, String item) { for (int i = 0; i < combo.getItemCount(); i++) if (item.equals(combo.getItemAt(i))) return true; return false; }
    private static void select(JComboBox<?> combo, String value) { combo.setSelectedItem(value); }
    private static void fillName(Container root) { for (Component c : root.getComponents()) { if (c instanceof JTextField && ((JTextField)c).getText().isEmpty()) ((JTextField)c).setText("Native fixture"); else if (c instanceof Container) fillName((Container)c); } }
}
