package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.ui.LauncherActions.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** External offscreen renderer. All accounts, logs, icons, states and progress are fixtures.
 * No Minecraft launch, downloaded artifact, public server connection or account operation occurs.
 */
public final class FinalUiPreview {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Usage: FinalUiPreview <output-directory> <width> <height> <light|dark>");
        Path output = Paths.get(args[0]).toAbsolutePath(); Files.createDirectories(output);
        int width = Integer.parseInt(args[1]), height = Integer.parseInt(args[2]);
        Path data = Files.createTempDirectory("autoplug-final-ui-fixture-");
        FixtureActions actions = new FixtureActions(); AtomicReference<DashboardPanel> dashboard = new AtomicReference<>();
        AtomicReference<LaunchChoicePanel> chooser = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            if ("dark".equalsIgnoreCase(args[3])) FlatDarkLaf.setup(); else FlatLightLaf.setup();
            dashboard.set(new DashboardPanel(actions, new ServerBrowserService(data.resolve("servers.json"), data.resolve("servers.dat")), false));
        });
        DashboardPanel panel = dashboard.get();
        try {
            await(() -> ((JProgressBar)find(panel, "activity-progress")).getValue() == 100);
            SwingUtilities.invokeAndWait(() -> {
                SavedServer newest = new SavedServer("Quiet Cove", "cove.example.invalid", 3000);
                SavedServer generic = new SavedServer("Minecraft Server", "skyline.example.invalid:25566", 2000);
                SavedServer pending = new SavedServer("Weekend Adventure", "weekend.example.invalid", 1000);
                Map<String, ServerStatus> states = new LinkedHashMap<>();
                states.put(newest.address, new ServerStatus(true, "Build together, explore new places, and relax.\nWelcome to our community survival world.", "Paper 1.21.1", 12, 40, 767, 38, "SUCCESS", fixtureIcon()));
                states.put(generic.address, new ServerStatus(false, "", "", 0, 0, 0, 0, "Could not reach the server. Try again when it is online."));
                panel.displayServers(Arrays.asList(pending, generic, newest), states);
            });
            for (String view : new String[]{"Server Browser", "Worlds", "Profiles", "Settings"}) {
                SwingUtilities.invokeAndWait(() -> button(panel, view).doClick(0));
                if ("Profiles".equals(view)) {
                    SwingUtilities.invokeAndWait(() -> table(panel).setRowSelectionInterval(1, 1));
                    await(() -> ((JProgressBar)find(panel, "activity-progress")).getValue() == 100);
                }
                render(panel, output.resolve(view.toLowerCase(Locale.ROOT).replace(' ', '-') + ".png"), width, height);
                if ("Settings".equals(view) && width < 1000) {
                    SwingUtilities.invokeAndWait(() -> {
                        Container defaults = find(panel, "settings-defaults");
                        JViewport viewport = (JViewport)SwingUtilities.getAncestorOfClass(JViewport.class, defaults);
                        if (viewport != null) viewport.setViewPosition(new Point(0, Math.max(0, defaults.getParent().getY() - 6)));
                    });
                    render(panel, output.resolve("settings-defaults.png"), width, height);
                }
            }
            SwingUtilities.invokeAndWait(() -> {
                button(panel, "Server Browser").doClick(0);
                ((JTabbedPane)find(panel, "server-browser-tabs")).setSelectedIndex(1);
                ((ManagedWorldPanel)find(panel, "managed-world-console")).select("cove");
            });
            await(() -> ((JTextArea)find(panel, "managed-world-output")).getText().contains("Preview fixture"));
            render(panel, output.resolve("managed-console.png"), width, height);
            SwingUtilities.invokeAndWait(() -> chooser.set(new LaunchChoicePanel(actions.clients(), actions.profiles().get(1), true,
                    ignored -> { throw new AssertionError("Fixture must never launch a profile"); }, () -> 0L)));
            render(chooser.get(), output.resolve("launch-chooser.png"), width < 1000 ? 460 : 610, 350);
            SwingUtilities.invokeAndWait(() -> {
                button(panel, "Profiles").doClick(0);
                panel.importArtifact(actions.profiles().get(1), "fixture-utility.jar", "fixture-project");
            });
            if (!actions.started.await(4, TimeUnit.SECONDS)) throw new AssertionError("Fixture work did not start");
            actions.progress.accept("Preparing utility mods — fixture download 37% · https://example.invalid/fixture-utility.jar");
            await(() -> ((JTextField)find(panel, "download-source")).isVisible());
            // Presentation-only position for this synthetic activity; the real progress estimator is tested separately.
            SwingUtilities.invokeAndWait(() -> ((JProgressBar)find(panel, "activity-progress")).setValue(48));
            render(panel, output.resolve("overall-progress.png"), width, height);
            System.out.println("Rendered fixture previews: " + output);
        } finally {
            actions.release.countDown();
            SwingUtilities.invokeAndWait(() -> { if (chooser.get() != null) chooser.get().close(); panel.close(); });
            Files.deleteIfExists(data); // This fixture directory remains empty; never recursively remove data.
        }
    }

    private static final class FixtureActions implements LauncherActions {
        volatile Consumer<String> progress = ignored -> {};
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        public void onProgress(Consumer<String> listener) { progress = listener == null ? ignored -> {} : listener; }
        public List<ProfileInfo> profiles() { return Arrays.asList(
                new ProfileInfo("vanilla", "Default (VANILLA)", "1.21.1", "VANILLA", "MODS", "sample-profiles/default-vanilla", false),
                new ProfileInfo("fabric", "Default (FABRIC)", "1.21.1", "FABRIC", "MODS", "sample-profiles/default-fabric", false,
                        "Client utility mods install on first launch: Fabric API, Sodium, Entity Culling, ImmediatelyFast and Mod Menu, with required libraries. Exact-version availability is checked before launch.", true),
                new ProfileInfo("server", "Default", "1.21.1", "VANILLA", "MODS_SERVER", "sample-profiles/default-server", false),
                new ProfileInfo("friends", "Friends server", "1.21.1", "PAPER", "PLUGINS", "sample-profiles/friends", false),
                new ProfileInfo("template", "Exploration base", "1.21.1", "FABRIC", "MODS", "sample-profiles/exploration", true)); }
        List<ProfileInfo> clients() { return profiles().subList(0, 2); }
        public List<WorldInfo> worlds() { return Arrays.asList(
                new WorldInfo("fresh", "Fresh start", "server", "vanilla", "sample-worlds/fresh-start", "", false, false, "1.21.1",
                        false, -1, 0, true, Collections.emptyList(), false, false, false),
                new WorldInfo("cove", "Quiet Cove", "friends", "fabric", "sample-worlds/quiet-cove", "", true, false, "1.21.1",
                        true, 1791140400000L, 597688320, true, Arrays.asList("vanilla", "file/terrain"), true, false, false),
                new WorldInfo("local:cottage", "Lakeside cottage", "", "", "sample-minecraft/saves/Lakeside cottage", "", false, true, "1.21.1",
                        false, 1791072000000L, 184549376, true, Collections.singletonList("vanilla"), false, false, true)); }
        public SettingsInfo settings() { SettingsInfo settings = new SettingsInfo(); settings.account = "Offline · Alex";
            settings.defaultProfile = "fabric"; settings.fullscreen = true; settings.upnp = true; return settings; }
        public String serverLog(String id) { return "[Preview fixture — no live server]\n"
                + "[AutoPlug] Isolated instance: Quiet Cove\n[AutoPlug] Configuration: sample-worlds/quiet-cove/autoplug\n"
                + "[AutoPlug] Started server process for Minecraft 1.21.1\n[Server] Preparing spawn area\n"
                + "[Server] Done (3.842s)! For help, type \"help\"\n[Server] Local bind: 127.0.0.1:25565\n"
                + "[AutoPlug] Ready. Sharing is disabled until explicitly requested.\n"; }
        public void addArtifact(String profile, String jar, String project) throws Exception { started.countDown(); release.await(); }
    }

    private static BufferedImage fixtureIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); try {
            g.setColor(new Color(0x24577B)); g.fillRect(0, 0, 64, 64);
            g.setColor(new Color(0x61B5CD)); g.fillRect(0, 42, 64, 22);
            g.setColor(new Color(0xF5D47A)); g.fillRect(43, 8, 12, 12);
            g.setColor(new Color(0x39795F)); g.fillPolygon(new int[]{4, 28, 51}, new int[]{45, 10, 45}, 3);
            g.setColor(new Color(0xE9EEE9)); g.fillPolygon(new int[]{20, 28, 36}, new int[]{22, 10, 22}, 3);
        } finally { g.dispose(); } return image;
    }
    private static void render(JComponent component, Path destination, int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                component.setSize(width, height); for (int i = 0; i < 8; i++) layout(component);
                BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = bitmap.createGraphics(); try { g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); component.printAll(g); }
                finally { g.dispose(); }
                if (!ImageIO.write(bitmap, "png", destination.toFile())) throw new IllegalStateException("PNG writer missing");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    private static void await(BooleanSupplier condition) throws Exception {
        AtomicBoolean ready = new AtomicBoolean(); long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!ready.get() && System.nanoTime() < deadline) { SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean())); if (!ready.get()) Thread.sleep(20); }
        if (!ready.get()) throw new AssertionError("Preview fixture did not settle");
    }
    private static Container find(Container parent, String name) {
        if (name.equals(parent.getName())) return parent;
        for (Component child : parent.getComponents()) if (child instanceof Container) { Container match = find((Container)child, name); if (match != null) return match; }
        return null;
    }
    private static AbstractButton button(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton && text.equals(((AbstractButton)child).getText())) return (AbstractButton)child;
            if (child instanceof Container) { AbstractButton match = button((Container)child, text); if (match != null) return match; }
        } return null;
    }
    private static JTable table(Container parent) {
        for (Component child : parent.getComponents()) { if (child instanceof JTable) return (JTable)child;
            if (child instanceof Container) { JTable match = table((Container)child); if (match != null) return match; } } return null;
    }
    private static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container)child); }
}
