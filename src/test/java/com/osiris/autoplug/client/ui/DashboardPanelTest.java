package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.browser.ServerBrowserService;
import com.osiris.autoplug.client.ui.LauncherActions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DashboardPanelTest {
    @TempDir Path directory;

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
                for (String view : new String[]{"Server Browser", "Virtual Worlds", "Profiles", "Server Manager", "Settings"})
                    assertNotNull(findButton(panel.get(), view), "Missing navigation: " + view);
                assertNotNull(findButton(panel.get(), "Create world"));
                assertNotNull(findButton(panel.get(), "Sign in with Microsoft"));
            });
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
            FlatLightLaf.setup();
            com.osiris.autoplug.client.utils.GD.TARGET = com.osiris.autoplug.client.Target.MINECRAFT_SERVER;
            panel.set(new DashboardPanel(fixtures(new AtomicBoolean(), calls), new ServerBrowserService(data.resolve("servers.json"), data.resolve("servers.dat")), true));
        });
        calls.await(4, TimeUnit.SECONDS);
        // Waiting behind the worker callbacks allows the fixture models to arrive before printing.
        Thread.sleep(250);
        try {
            for (String view : new String[]{"Server Browser", "Virtual Worlds", "Profiles", "Server Manager", "Settings"}) {
                SwingUtilities.invokeAndWait(() -> findButton(panel.get(), view).doClick());
                Thread.sleep(50);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                    panel.get().setSize(width, height); layout(panel.get());
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
                called(); return Arrays.asList(new WorldInfo("cove", "Quiet Cove", "paper", "vanilla", "/worlds/quiet-cove", "", false),
                        new WorldInfo("forest", "The long weekend", "paper", "fabric", "/worlds/long-weekend", "", false));
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
    private static void layout(Container container) { container.doLayout(); for (Component component : container.getComponents()) if (component instanceof Container) layout((Container) component); }
}
