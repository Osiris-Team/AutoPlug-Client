package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.profiles.*;
import com.osiris.autoplug.client.worlds.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Native console clicks exercise the real WorldService with an interrupted, late fake installer. */
class ManagedWorldStartupNativeTest {
    @TempDir Path temp;
    @Test void repeatedStopClicksCancelActualPendingServiceLaunch() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ProfileStore profiles = new ProfileStore(temp.resolve("profiles"));
        Profile server = profiles.create("Server", "1.20.4", "PAPER", ProfileType.PLUGINS), client = profiles.create("Client", "1.20.4", "VANILLA", ProfileType.MODS);
        WorldStore worlds = new WorldStore(temp.resolve("worlds")); VirtualWorld world = worlds.create("Delayed fixture world", server.id, client.id, true);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), stopped = new CountDownLatch(2); AtomicInteger starts = new AtomicInteger();
        ExecutorService launchWorker = Executors.newSingleThreadExecutor(); AtomicReference<Future<WorldSession>> launch = new AtomicReference<>();
        AtomicReference<ManagedWorldPanel> panel = new AtomicReference<>(); AtomicReference<JFrame> frame = new AtomicReference<>();
        try (WorldService service = new WorldService(profiles, worlds, (p, d) -> {
            entered.countDown(); while (release.getCount() != 0) try { release.await(); } catch (InterruptedException ignored) { }
            return new ServerLaunch(Collections.singletonList("never-executed-fixture"), d);
        }, (p, h, n) -> { throw new AssertionError("No client expected"); }, port -> { throw new AssertionError("No gateway"); },
                (h, p) -> true, request -> { starts.incrementAndGet(); throw new IOException("No actual server binary"); }, Duration.ofSeconds(5))) {
            LauncherActions actions = new LauncherActions() {
                @Override public List<WorldInfo> worlds() throws Exception { return Collections.singletonList(new WorldInfo(world.id, world.name, server.id, client.id, worlds.getDirectory(world.id).toString(), "", false)); }
                @Override public void stopWorld(String id) { assertFalse(SwingUtilities.isEventDispatchThread()); service.stopWorld(id); stopped.countDown(); }
                @Override public String serverLog(String id) { return release.getCount() == 0 ? "FAKE launch cancelled. No late process started: " + starts.get() + ". No Minecraft process or router exists." : "FAKE installer waiting. No Minecraft process or router exists."; }
            };
            SwingUtilities.invokeAndWait(() -> {
                ManagedWorldPanel p = new ManagedWorldPanel(actions, w -> launch.set(launchWorker.submit(() -> service.launch(w.id, false))), ignored -> {});
                JFrame f = new JFrame("World startup - FAKE fixture only"); f.setContentPane(p); f.setSize(950, 620); f.setVisible(true); panel.set(p); frame.set(f); p.select(world.id);
            });
            DashboardAuthenticationLifecycleTest.awaitUi(() -> selected(panel.get()));
            SwingUtilities.invokeAndWait(() -> DashboardAuthenticationLifecycleTest.button(panel.get(), "Play / start").doClick(0));
            assertTrue(entered.await(5, TimeUnit.SECONDS)); snapshot(frame.get(), "world-preparation-before-stop");
            SwingUtilities.invokeAndWait(() -> { DashboardAuthenticationLifecycleTest.button(panel.get(), "Stop").doClick(0); DashboardAuthenticationLifecycleTest.button(panel.get(), "Stop").doClick(0); });
            assertTrue(stopped.await(5, TimeUnit.SECONDS)); release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> launch.get().get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof InterruptedException); assertEquals(0, starts.get()); assertTrue(service.activeSessions().isEmpty());
            assertFalse(Files.exists(worlds.getDirectory(world.id).resolve("eula.txt")));
            try (ProfileLease ignored = new ProfileLease(worlds.getDirectory(world.id))) { }
            SwingUtilities.invokeAndWait(() -> panel.get().refresh());
            DashboardAuthenticationLifecycleTest.awaitUi(() -> outputContains(panel.get(), "FAKE launch cancelled"));
            snapshot(frame.get(), "world-preparation-after-stop");
        } finally {
            release.countDown(); launchWorker.shutdownNow(); assertTrue(launchWorker.awaitTermination(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> { if (panel.get() != null) panel.get().close(); if (frame.get() != null) frame.get().dispose(); });
        }
    }
    private static boolean selected(Container root) { for (Component c : root.getComponents()) { if (c instanceof JComboBox && ((JComboBox<?>)c).getSelectedItem() instanceof LauncherActions.WorldInfo) return true; if (c instanceof Container && selected((Container)c)) return true; } return false; }
    private static boolean outputContains(Container root, String text) { for (Component c : root.getComponents()) { if (c instanceof JTextArea && ((JTextArea)c).getText().contains(text)) return true; if (c instanceof Container && outputContains((Container)c, text)) return true; } return false; }
    private static void snapshot(JFrame frame, String name) throws Exception {
        String output = System.getProperty("autoplug.derived.uiEvidence"); if (output == null) return;
        SwingUtilities.invokeAndWait(() -> { try {
            BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB); Graphics2D g = image.createGraphics(); frame.paint(g); g.dispose();
            Files.createDirectories(Paths.get(output)); ImageIO.write(image, "png", Paths.get(output, name + ".png").toFile());
        } catch (Exception e) { throw new AssertionError(e); } });
    }
}
