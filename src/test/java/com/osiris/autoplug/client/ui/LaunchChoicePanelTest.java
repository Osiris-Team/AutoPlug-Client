package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.ui.LauncherActions.ProfileInfo;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class LaunchChoicePanelTest {
    private final ProfileInfo preferred = new ProfileInfo("first", "First", "1.21.1", "VANILLA", "MODS", "", false);
    private final ProfileInfo alternative = new ProfileInfo("second", "Second", "1.21.1", "FABRIC", "MODS", "", false);

    @Test void rememberedChoiceWaitsTwoSecondsAndLaunchesOnlyOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong(); List<ProfileInfo> chosen = new ArrayList<>();
            try (LaunchChoicePanel panel = new LaunchChoicePanel(Arrays.asList(preferred, alternative), preferred, true, chosen::add, clock::get)) {
                AbstractButton cancel = named(panel, "abort-auto-launch"); assertTrue(cancel.isVisible()); assertTrue(cancel.getText().contains("2s"));
                clock.set(1_000_000_000L); panel.tick(); assertTrue(cancel.getText().contains("1s")); assertTrue(chosen.isEmpty());
                clock.set(1_999_999_999L); panel.tick(); assertTrue(chosen.isEmpty());
                clock.set(2_000_000_000L); panel.tick(); panel.tick(); named(panel, "launch-choice-second").doClick(0);
                assertEquals(Arrays.asList(preferred), chosen);
            }
        });
    }

    @Test void abortStopsAutomaticLaunchButAllowsOneDeliberateAlternative() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong(); List<ProfileInfo> chosen = new ArrayList<>();
            try (LaunchChoicePanel panel = new LaunchChoicePanel(Arrays.asList(preferred, alternative), preferred, true, chosen::add, clock::get)) {
                named(panel, "abort-auto-launch").doClick(0);
                clock.set(9_000_000_000L); panel.tick(); assertTrue(chosen.isEmpty());
                assertFalse(named(panel, "abort-auto-launch").isVisible());
                named(panel, "launch-choice-second").doClick(0); named(panel, "launch-choice-first").doClick(0); panel.tick();
                assertEquals(Arrays.asList(alternative), chosen);
            }
        });
    }

    @Test void closingChooserCancelsQueuedTicksAndButtonEvents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicLong clock = new AtomicLong(); List<ProfileInfo> chosen = new ArrayList<>();
            LaunchChoicePanel panel = new LaunchChoicePanel(Arrays.asList(preferred, alternative), preferred, true, chosen::add, clock::get);
            panel.close(); clock.set(9_000_000_000L); panel.tick(); named(panel, "launch-choice-first").doClick(0); panel.close();
            assertTrue(chosen.isEmpty(), "Disposing/closing the chooser cannot launch after it is gone");
        });
    }

    @Test void choicesAreFullWidthAtCompactAndWideSizesAndLiteralNamesStayLiteral() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ProfileInfo literal = new ProfileInfo("literal", "<html><b>Literal name</b>", "1.21.1", "VANILLA", "MODS", "", false);
            try (LaunchChoicePanel panel = new LaunchChoicePanel(Arrays.asList(preferred, literal), preferred, true, ignored -> {}, () -> 0L)) {
                AbstractButton choice = named(panel, "launch-choice-literal");
                assertEquals(literal.toString(), choice.getText()); assertNull(choice.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
                for (int width : new int[]{800, 360}) {
                    panel.setSize(width, 460); for (int i = 0; i < 5; i++) layout(panel);
                    JViewport viewport = (JViewport)SwingUtilities.getAncestorOfClass(JViewport.class, choice);
                    assertNotNull(viewport); assertEquals(viewport.getExtentSize().width, choice.getWidth());
                    assertTrue(choice.getHeight() >= 50); assertEquals(0, choice.getX());
                    AbstractButton cancel = named(panel, "abort-auto-launch"); assertEquals(width - 32, cancel.getWidth()); assertTrue(cancel.getHeight() >= 50);
                    assertTrue(cancel.getBackground().getRed() > cancel.getBackground().getGreen());
                }
            }
        });
    }

    @Test void pendingOrTemplateProfilesCannotAutoLaunchOrBeSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ProfileInfo pending = new ProfileInfo("pending", "Pending", "1.21.1", "FABRIC", "MODS", "", false, "Review migration", false);
            ProfileInfo template = new ProfileInfo("template", "Template", "1.21.1", "VANILLA", "MODS", "", true);
            AtomicLong clock = new AtomicLong(); List<ProfileInfo> chosen = new ArrayList<>();
            try (LaunchChoicePanel panel = new LaunchChoicePanel(Arrays.asList(pending, template), pending, true, chosen::add, clock::get)) {
                assertFalse(named(panel, "launch-choice-pending").isEnabled()); assertFalse(named(panel, "launch-choice-template").isEnabled());
                clock.set(9_000_000_000L); panel.tick(); assertTrue(chosen.isEmpty()); assertFalse(named(panel, "abort-auto-launch").isVisible());
            }
        });
    }

    private static AbstractButton named(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (component instanceof AbstractButton && name.equals(component.getName())) return (AbstractButton)component;
            if (component instanceof Container) { AbstractButton match = named((Container)component, name); if (match != null) return match; }
        }
        return null;
    }
    private static void layout(Container container) { container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container) layout((Container)child); }
}
