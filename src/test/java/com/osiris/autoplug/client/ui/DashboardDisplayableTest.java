package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.browser.ServerBrowserService;
import com.osiris.autoplug.client.ui.LauncherActions.ProfileInfo;
import com.osiris.autoplug.client.ui.LauncherActions.SettingsInfo;
import com.osiris.autoplug.client.ui.LauncherActions.WorldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Exercises real, valid Swing peers without showing a window or contacting game services. */
@Tag("displayable")
class DashboardDisplayableTest {
    @TempDir Path directory;

    @Test void hiddenDisplayableDashboardReflowsSettingsAndWorldsInBothThemes() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Requires hidden native Swing peers; run with java.awt.headless=false");
        LookAndFeel original = UIManager.getLookAndFeel();
        RecordingEventQueue events = new RecordingEventQueue();
        AtomicReference<JFrame> frame = new AtomicReference<>();
        AtomicReference<DashboardPanel> dashboard = new AtomicReference<>();
        onEdt(() -> Toolkit.getDefaultToolkit().getSystemEventQueue().push(events));
        try {
            for (boolean dark : new boolean[]{false, true}) {
                onEdt(() -> {
                    UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
                    DashboardPanel panel = new DashboardPanel(fixtures(), new ServerBrowserService(
                            directory.resolve("missing-favorites.json"), directory.resolve("missing-servers.dat")), false);
                    dashboard.set(panel);
                    JFrame window = new JFrame("Hidden dashboard lifecycle test");
                    frame.set(window);
                    window.setContentPane(panel);
                    // addNotify/pack create real peers, but setVisible(true) is never called.
                    window.addNotify();
                    window.pack();
                    window.setSize(1200, 820);
                    window.validate();
                    assertTrue(window.isDisplayable());
                    assertFalse(window.isVisible());
                    assertFalse(window.isShowing());
                });
                awaitUi(() -> ((JProgressBar) required(dashboard.get(), "activity-progress")).getValue() == 100
                        && findNamed(dashboard.get(), "world-local:fixture-7") != null, events);

                for (int width : new int[]{1200, 950, 1200, 950}) {
                    onEdt(() -> {
                        button(dashboard.get(), "Settings").doClick();
                        frame.get().setSize(width, width == 950 ? 620 : 820);
                        frame.get().validate();
                    });
                    settle(frame.get(), events);
                    onEdt(() -> {
                        Container settings = required(dashboard.get(), "responsive-settings");
                        assertValidAncestors(settings, frame.get());
                        assertSettingsColumns(settings, width);
                        Container runtime = required(dashboard.get(), "advanced-runtime-fields");
                        AbstractButton advanced = button(dashboard.get(), runtime.isVisible() ? "Advanced −" : "Advanced");
                        if (!runtime.isVisible()) advanced.doClick();
                    });
                    settle(frame.get(), events);
                    onEdt(() -> {
                        Container runtime = required(dashboard.get(), "advanced-runtime-fields");
                        List<JTextField> fields = textFields(runtime);
                        assertEquals(4, fields.size(), "All Java override fields remain available");
                        JTextField java8 = fields.get(0);
                        if (java8.getText().isEmpty()) java8.setText("C:/fixture/runtime with spaces/bin/java");
                        assertEquals("C:/fixture/runtime with spaces/bin/java", java8.getText());
                        for (JTextField field : fields) {
                            assertTrue(field.getWidth() >= 80, "Runtime field must remain usable");
                            assertTrue(field.getHeight() > 0);
                        }
                        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, runtime);
                        assertNotNull(scroll);
                        scroll.doLayout(); // Calls ScrollPaneLayout -> ResponsiveSettings.getPreferredSize.
                        JTextField last = fields.get(fields.size() - 1);
                        // JTextField.scrollRectToVisible only scrolls the text horizontally.
                        JComponent view = (JComponent) scroll.getViewport().getView();
                        view.scrollRectToVisible(SwingUtilities.convertRectangle(last.getParent(), last.getBounds(), view));
                        assertInsideViewport(last, scroll);
                        if (width == 950) assertTrue(scroll.getViewport().getViewSize().height
                                > scroll.getViewport().getExtentSize().height, "Expanded narrow settings must scroll");
                        assertInside(button(dashboard.get(), "Save settings"), dashboard.get());
                        button(dashboard.get(), "Advanced −").doClick();
                    });
                    settle(frame.get(), events);
                    onEdt(() -> {
                        assertFalse(required(dashboard.get(), "advanced-runtime-fields").isVisible());
                        button(dashboard.get(), "Advanced").doClick();
                    });
                    settle(frame.get(), events);
                    onEdt(() -> assertEquals("C:/fixture/runtime with spaces/bin/java",
                            textFields(required(dashboard.get(), "advanced-runtime-fields")).get(0).getText()));

                    onEdt(() -> button(dashboard.get(), "Worlds").doClick());
                    awaitUi(() -> ((JProgressBar) required(dashboard.get(), "activity-progress")).getValue() == 100, events);
                    settle(frame.get(), events);
                    onEdt(() -> verifyWorlds(dashboard.get(), frame.get()));
                    events.assertClean();
                }
                onEdt(() -> {
                    dashboard.getAndSet(null).close();
                    frame.getAndSet(null).dispose();
                });
                onEdt(() -> { });
                events.assertClean();
            }
        } finally {
            onEdt(() -> {
                try {
                    if (dashboard.get() != null) dashboard.get().close();
                    if (frame.get() != null) frame.get().dispose();
                    if (original != null) UIManager.setLookAndFeel(original);
                } finally { events.restore(); }
            });
        }
        events.assertClean();
    }

    private LauncherActions fixtures() {
        return new LauncherActions() {
            @Override public List<ProfileInfo> profiles() {
                assertFalse(SwingUtilities.isEventDispatchThread());
                return Arrays.asList(new ProfileInfo("fixture", "Fixture profile", "1.21.1", "VANILLA", "MODS", "", false));
            }
            @Override public SettingsInfo settings() {
                assertFalse(SwingUtilities.isEventDispatchThread());
                SettingsInfo settings = new SettingsInfo(); settings.account = "Offline fixture"; settings.defaultProfile = "fixture";
                return settings;
            }
            @Override public List<WorldInfo> worlds() {
                assertFalse(SwingUtilities.isEventDispatchThread());
                List<WorldInfo> worlds = new ArrayList<>();
                for (int i = 0; i < 8; i++) worlds.add(new WorldInfo("local:fixture-" + i,
                        "Fixture world " + i + " — long metadata and Unicode 世界", "", "",
                        directory.resolve("a long fixture path with spaces/another long directory/original saves/world " + i).toString(),
                        "", false, true, "1.21.1", true, 1791072000000L, 184549376L, true,
                        Arrays.asList("vanilla", "file/long fixture datapack name"), true, false, true));
                return worlds;
            }
        };
    }

    private static void verifyWorlds(DashboardPanel panel, JFrame frame) {
        Container first = required(panel, "world-local:fixture-0");
        assertValidAncestors(first, frame);
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, first);
        assertNotNull(scroll);
        scroll.doLayout();
        Container view = (Container) scroll.getViewport().getView();
        int previousBottom = 0;
        for (int i = 0; i < 8; i++) {
            Container card = required(panel, "world-local:fixture-" + i);
            assertTrue(card.getWidth() > 0 && card.getHeight() > 0);
            assertTrue(card.getY() >= previousBottom, "World cards must not overlap");
            assertTrue(card.getX() >= 0 && card.getX() + card.getWidth() <= view.getWidth());
            assertInside(button(card, "Launch"), card);
            assertInside(button(card, "Open folder"), card);
            previousBottom = card.getY() + card.getHeight();
        }
        assertTrue(view.getHeight() > scroll.getViewport().getExtentSize().height);
        AbstractButton lastLaunch = button(required(panel, "world-local:fixture-7"), "Launch");
        lastLaunch.scrollRectToVisible(new Rectangle(0, 0, lastLaunch.getWidth(), lastLaunch.getHeight()));
        assertInsideViewport(lastLaunch, scroll);
        assertTrue(scroll.getViewport().getViewPosition().y > 0, "Final world must be reachable by scrolling");
        assertFalse(frame.isVisible());
    }

    private static void assertSettingsColumns(Container settings, int windowWidth) {
        Component left = settings.getComponent(0), right = settings.getComponent(1);
        assertTrue(left.getWidth() > 0 && right.getWidth() > 0);
        if (windowWidth == 1200) {
            assertEquals(left.getY(), right.getY());
            assertTrue(right.getX() >= left.getX() + left.getWidth());
        } else {
            assertEquals(left.getX(), right.getX());
            assertTrue(right.getY() >= left.getY() + left.getHeight());
        }
    }

    private static void assertValidAncestors(Component component, JFrame frame) {
        assertTrue(component.isDisplayable(), "The test must use real native peers");
        for (Component current = component; current != null; current = current.getParent()) {
            assertTrue(current.isValid(), "Expected validated ancestor: " + current.getClass().getSimpleName());
            if (current == frame) return;
        }
        fail("Component is not attached to the fixture frame");
    }

    private static void assertInside(Component child, Container ancestor) {
        Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), ancestor);
        assertTrue(bounds.width > 0 && bounds.height > 0);
        assertTrue(new Rectangle(0, 0, ancestor.getWidth(), ancestor.getHeight()).contains(bounds),
                "Control outside its container: " + bounds);
    }

    private static void assertInsideViewport(Component child, JScrollPane scroll) {
        Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), scroll.getViewport().getView());
        assertTrue(scroll.getViewport().getViewRect().contains(bounds), "Control must be reachable in the viewport: " + bounds + " within " + scroll.getViewport().getViewRect());
    }

    private static void settle(JFrame frame, RecordingEventQueue events) throws Exception {
        // Separate EDT turns allow queued validation to run; this is not recursive doLayout.
        for (int pass = 0; pass < 3; pass++) {
            onEdt(() -> { RepaintManager.currentManager(frame).validateInvalidComponents(); frame.validate(); });
            events.assertClean();
        }
    }

    private static void awaitUi(BooleanSupplier ready, RecordingEventQueue events) throws Exception {
        AtomicBoolean result = new AtomicBoolean(); long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!result.get() && System.nanoTime() < end) {
            onEdt(() -> result.set(ready.getAsBoolean())); events.assertClean();
            if (!result.get()) Thread.sleep(20);
        }
        assertTrue(result.get(), "Fixture callbacks did not complete");
    }

    private static Container required(Container root, String name) {
        Container found = findNamed(root, name); assertNotNull(found, "Missing component: " + name); return found;
    }
    private static Container findNamed(Container root, String name) {
        if (name.equals(root.getName())) return root;
        for (Component child : root.getComponents()) if (child instanceof Container) {
            Container found = findNamed((Container) child, name); if (found != null) return found;
        }
        return null;
    }
    private static AbstractButton button(Container root, String text) {
        AbstractButton found = findButton(root, text); assertNotNull(found, "Missing button: " + text); return found;
    }
    private static AbstractButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton && text.equals(((AbstractButton) child).getText())) return (AbstractButton) child;
            if (child instanceof Container) { AbstractButton found = findButton((Container) child, text); if (found != null) return found; }
        }
        return null;
    }
    private static List<JTextField> textFields(Container root) {
        List<JTextField> fields = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JTextField) fields.add((JTextField) child);
            else if (child instanceof Container) fields.addAll(textFields((Container) child));
        }
        return fields;
    }

    private interface EdtAction { void run() throws Exception; }
    private static void onEdt(EdtAction action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError("Displayable EDT action failed", failure.get());
    }

    private static final class RecordingEventQueue extends EventQueue {
        private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        @Override protected void dispatchEvent(AWTEvent event) {
            try { super.dispatchEvent(event); } catch (Throwable failure) { failures.add(failure); }
        }
        void assertClean() {
            Throwable failure = failures.peek();
            if (failure != null) throw new AssertionError("Uncaught exception on the Swing event queue", failure);
        }
        void restore() { pop(); }
    }
}
