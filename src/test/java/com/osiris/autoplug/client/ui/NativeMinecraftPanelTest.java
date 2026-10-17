package com.osiris.autoplug.client.ui;

import com.google.gson.JsonObject;
import com.osiris.autoplug.client.mc.client.ClientSettings;
import com.osiris.autoplug.client.mc.client.NativeClientContext;
import com.osiris.autoplug.client.mc.launch.Cancellation;
import com.osiris.autoplug.client.mc.launch.LaunchTransport;
import com.osiris.autoplug.client.mc.profiles.Profile;
import com.osiris.autoplug.client.mc.profiles.ProfileType;
import com.osiris.autoplug.client.mc.update.ProfileUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NativeMinecraftPanelTest {
    @TempDir Path temporary;

    @Test void detectsReleaseVersionsInsideServerSoftwareLabels() {
        assertEquals("1.20.4", NativeMinecraftPanel.detectVersion("Paper 1.20.4"));
        assertEquals("1.20.1", NativeMinecraftPanel.detectVersion("Forge 47.2.20 / Minecraft 1.20.1"));
        assertEquals("1.21", NativeMinecraftPanel.detectVersion("1.21"));
    }

    @Test void doesNotInventAReleaseVersionFromSnapshotOrMalformedStatus() {
        assertEquals("", NativeMinecraftPanel.detectVersion(null));
        assertEquals("", NativeMinecraftPanel.detectVersion(""));
        assertEquals("", NativeMinecraftPanel.detectVersion("Snapshot 24w14a"));
        assertEquals("", NativeMinecraftPanel.detectVersion("Proxy supports 1.8 through 1.20.4"));
        assertEquals("", NativeMinecraftPanel.detectVersion("unsupported 1.20.4.8"));
    }

    @Test void bootstrapRendersOwnSavedDataAndTemplatesWithoutNetworkOrGameLaunch() throws Exception {
        temporary = temporary.toRealPath(); // macOS /var is an alias; storage deliberately requires canonical ancestors.
        Path data = Files.createDirectory(temporary.resolve("data"));
        ClientSettings settings = new ClientSettings(data);
        settings.set("cacheDirectory", temporary.resolve("cache").toString()); settings.save();
        AtomicInteger externalCalls = new AtomicInteger();
        LaunchTransport noNetwork = new LaunchTransport() {
            public JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body, Cancellation cancellation) {
                externalCalls.incrementAndGet(); throw new AssertionError("UI bootstrap must not contact a service");
            }
            public void download(URI uri, Path destination, Cancellation cancellation) {
                externalCalls.incrementAndGet(); throw new AssertionError("UI bootstrap must not download");
            }
        };
        ProfileUpdateService.Engine noUpdate = new ProfileUpdateService.Engine() {
            public ProfileUpdateService.Decision resolve(Profile profile, Path jar, String target) {
                externalCalls.incrementAndGet(); throw new AssertionError("UI bootstrap must not check assets");
            }
            public void download(ProfileUpdateService.Decision decision, Path destination) {
                externalCalls.incrementAndGet(); throw new AssertionError("UI bootstrap must not download assets");
            }
        };
        try (NativeClientContext context = new NativeClientContext(data, temporary, noNetwork, noUpdate)) {
            // A populated private fixture list prevents automatic inspection of real servers.dat.
            context.servers().add("Synthetic server", "example.invalid:25565", "VANILLA");
            context.profiles().create("client", ProfileType.MODS, "1.20.4", "VANILLA", false);
            context.profiles().create("template", ProfileType.MODS, "1.21", "FABRIC", true);
            context.profiles().create("server", ProfileType.PLUGINS, "1.20.4", "PAPER", false);
            context.worlds().create("synthetic", "server");
            AtomicReference<NativeMinecraftPanel> panel = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> panel.set(new NativeMinecraftPanel(context)));
            AtomicBoolean loading = new AtomicBoolean(true);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (loading.get() && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> loading.set(components(panel.get(), JButton.class).stream()
                        .filter(button -> button.getText().equals("Cancel operation")).findFirst().orElseThrow(AssertionError::new).isEnabled()));
                if (loading.get()) Thread.sleep(10);
            }
            assertFalse(loading.get(), "Read-only fixture bootstrap should finish");
            SwingUtilities.invokeAndWait(() -> {
                JTabbedPane tabs = components(panel.get(), JTabbedPane.class).get(0);
                assertEquals(4, tabs.getTabCount());
                List<JTable> tables = components(panel.get(), JTable.class);
                assertEquals(3, tables.size());
                assertEquals(1, tables.get(0).getRowCount());
                assertEquals("Synthetic server", tables.get(0).getValueAt(0, 0));
                assertEquals(3, tables.get(1).getRowCount());
                assertEquals("template", tables.get(1).getValueAt(2, 0));
                assertEquals(Boolean.TRUE, tables.get(1).getValueAt(2, 4));
                assertEquals(1, tables.get(2).getRowCount());
                assertEquals("Stopped", tables.get(2).getValueAt(0, 3));
            });
            assertEquals(0, externalCalls.get());
        }
    }

    @Test void loadsOnlyBoundedPngWorldPreviewAndIgnoresCorruption() throws Exception {
        Path world=Files.createDirectory(temporary.resolve("preview"));
        assertNull(NativeMinecraftPanel.worldPreview(world));
        Files.write(world.resolve("server-icon.png"),new byte[]{1,2,3});
        assertNull(NativeMinecraftPanel.worldPreview(world));
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(image,"png",world.resolve("server-icon.png").toFile());
        javax.swing.Icon icon=NativeMinecraftPanel.worldPreview(world);
        assertNotNull(icon);assertEquals(48,icon.getIconWidth());assertEquals(48,icon.getIconHeight());
        image=new java.awt.image.BufferedImage(1024,1024,java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(image,"png",world.resolve("server-icon.png").toFile());
        assertNull(NativeMinecraftPanel.worldPreview(world));
    }

    private static <T extends Component> List<T> components(Container parent, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container) result.addAll(components((Container) child, type));
        }
        return result;
    }
}
