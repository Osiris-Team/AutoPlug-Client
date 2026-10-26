package com.osiris.autoplug.client.clientapp;

import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.browser.*;
import com.osiris.autoplug.client.profiles.*;
import com.osiris.autoplug.client.ui.client.ClientDashboard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offscreen rendering of production Swing panels with local fixture data; no desktop automation. */
class DashboardLayoutTest {
    @TempDir Path temp;
    @Test void realClientPanelsRenderAtMinimumAndLargeWindowSizesWithoutNetworkOrAccountActivity() throws Exception {
        ProfileStore store = new ProfileStore(temp.resolve("autoplug")); store.create("survival-1-20-4", Profile.Type.MODS, "1.20.4", "fabric", false); store.create("client-base", Profile.Type.MODS, "1.21.1", "fabric", true); store.create("hub-plugins", Profile.Type.PLUGINS, "1.20.4", "paper", true);
        ServerCatalog catalog = new ServerCatalog(store.getRoot()); catalog.save(Arrays.asList(new SavedServer("Survival world", ServerAddress.parse("play.example.org"), "FABRIC"), new SavedServer("Local testing", ServerAddress.parse("localhost:25566"), "VANILLA")));
        NativeClientService service = new NativeClientService(store, catalog, new ClientSettings(store.getRoot()), (profile, account, address) -> { throw new AssertionError("Rendering must not launch a process"); }, new NativeClientService.Migration() {
            public ProfileUpdater.Plan check(Profile profile) { throw new AssertionError("Rendering must not access an updater"); }
            public void apply(ProfileUpdater.Plan plan, boolean confirm) { throw new AssertionError("Rendering must not apply updates"); }
        });
        Path destination = Paths.get("target", "dashboard-fixture-render"); Files.createDirectories(destination);
        WorldClientContext.of(service).worlds.create("Workshop world", "hub-plugins");
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(FlatLightLaf.setup()); ClientDashboard dashboard = new ClientDashboard(service, null, false); assertEquals(4, dashboard.getTabCount());
            for (int width : new int[]{800, 1180}) for (int tab = 0; tab < dashboard.getTabCount(); tab++) {
                dashboard.setSelectedIndex(tab); dashboard.setSize(width, 650); layout(dashboard);
                assertTrue(dashboard.getSelectedComponent().getWidth() > 500); assertTrue(dashboard.getSelectedComponent().getHeight() > 450);
                BufferedImage image = new BufferedImage(width, 650, BufferedImage.TYPE_INT_RGB); Graphics2D graphics = image.createGraphics(); graphics.setColor(UIManager.getColor("Panel.background")); graphics.fillRect(0, 0, width, 650); dashboard.printAll(graphics); graphics.dispose();
                try { ImageIO.write(image, "png", destination.resolve("client-tab-" + tab + "-" + width + ".png").toFile()); } catch (Exception failure) { throw new RuntimeException(failure); }
            }
            dashboard.release();
        });
        assertEquals(3, store.list().size()); assertEquals(2, catalog.list().size());
        WorldClientContext.of(service).lifecycle.close();
    }
    private static void layout(Container container) { container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container) layout((Container)child); }
}
