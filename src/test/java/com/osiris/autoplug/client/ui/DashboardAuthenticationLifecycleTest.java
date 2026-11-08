package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.osiris.autoplug.client.browser.ServerBrowserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Agent-driven native Swing fixture interactions; no account/browser/network authorization. */
class DashboardAuthenticationLifecycleTest {
    @TempDir Path temporary;
    static class Attempt {
        final String name; final CountDownLatch waiting = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        Attempt(String name) { this.name = name; }
    }
    static class FakeActions implements LauncherActions {
        long generation; String label = "Player (offline)";
        final Queue<Attempt> attempts = new ConcurrentLinkedQueue<>();
        @Override public synchronized long beginAccountChange() { return ++generation; }
        @Override public synchronized void cancelMicrosoftSignIn() { generation++; }
        @Override public synchronized SettingsInfo settings() { SettingsInfo s = new SettingsInfo(); s.microsoftClientId = "fixture-app-id"; s.account = label; return s; }
        @Override public void saveSettings(SettingsInfo settings) {}
        @Override public synchronized void useOfflineAccount(long attempt, String name) { if (attempt != generation) throw new CancellationException(); label = name + " (offline)"; }
        @Override public void signInMicrosoft(long attempt, SettingsInfo settings, Consumer<String> instructions) throws Exception {
            Attempt fixture = attempts.remove();
            try {
                instructions.accept("FAKE " + fixture.name + " - no real sign-in. example.invalid only."); fixture.waiting.countDown();
                assertTrue(fixture.release.await(10, TimeUnit.SECONDS), "fixture release timed out");
                // Send a late UI callback even after cancellation to test dashboard suppression.
                instructions.accept("LATE FAKE " + fixture.name);
                synchronized (this) { if (attempt != generation) throw new CancellationException(); label = fixture.name + " (Microsoft)"; }
            } finally { fixture.done.countDown(); }
        }
    }
    DashboardPanel panel; JFrame frame; javax.swing.Timer confirmation;
    void open(FakeActions fake) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Native Swing peer test must be run with headless=false");
        SwingUtilities.invokeAndWait(() -> {
            FlatLightLaf.setup(); panel = new DashboardPanel(fake, new ServerBrowserService(temporary.resolve("servers.json"), temporary.resolve("servers.dat")), false);
            frame = new JFrame("AutoPlug - FAKE AUTH fixture only"); frame.setContentPane(panel); frame.setSize(1100, 720); frame.setVisible(true);
            confirmation = new javax.swing.Timer(30, event -> {
                for (Window window : Window.getWindows()) if (window.isVisible() && window instanceof JDialog && ((JDialog)window).getTitle().equals("Microsoft sign-in")) {
                    JOptionPane option = findOption(window); if (option != null) option.setValue(JOptionPane.OK_OPTION);
                }
            }); confirmation.start();
        });
        awaitUi(() -> field("clientId", JTextField.class).getText().equals("fixture-app-id"));
        SwingUtilities.invokeAndWait(() -> button(panel, "Settings").doClick(0));
    }
    void close() throws Exception { SwingUtilities.invokeAndWait(() -> { if (confirmation != null) confirmation.stop(); if (panel != null) panel.close(); if (frame != null) frame.dispose(); }); }
    <T> T field(String name, Class<T> type) {
        try { Field field = DashboardPanel.class.getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(panel)); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    void signIn(Attempt attempt, FakeActions fake) throws Exception {
        fake.attempts.add(attempt);
        SwingUtilities.invokeAndWait(() -> button(panel, "Sign in with Microsoft").doClick(0));
        assertTrue(attempt.waiting.await(5, TimeUnit.SECONDS)); awaitUi(() -> { JDialog d=field("signInDialog", JDialog.class); return d!=null && d.isVisible(); });
    }
    void finish(Attempt a) throws Exception { a.release.countDown(); assertTrue(a.done.await(5, TimeUnit.SECONDS)); }
    void snapshot(String name) throws Exception {
        String destination = System.getProperty("autoplug.auth.uiEvidence"); if (destination == null) return;
        SwingUtilities.invokeAndWait(() -> {
            try {
                BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); frame.paint(graphics); graphics.dispose();
                Path directory = Paths.get(destination); Files.createDirectories(directory); ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
                JDialog dialog = field("signInDialog", JDialog.class);
                if (dialog != null && dialog.isVisible()) {
                    BufferedImage dialogImage = new BufferedImage(dialog.getWidth(), dialog.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = dialogImage.createGraphics(); dialog.paint(g); g.dispose(); ImageIO.write(dialogImage, "png", directory.resolve(name + "-dialog.png").toFile());
                }
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
    @Test void repeatedSignInClickKeepsNewDialogAfterOldResponse() throws Exception {
        FakeActions fake = new FakeActions(); Attempt a = new Attempt("LoginA"), b = new Attempt("LoginB");
        try { open(fake); signIn(a, fake); signIn(b, fake); snapshot("fixed-repeated-click"); finish(a);
            awaitUi(() -> field("signInDialog", JDialog.class) != null && field("signInDialog", JDialog.class).isVisible());
            SwingUtilities.invokeAndWait(() -> assertTrue(text(field("signInDialog", JDialog.class)).contains("FAKE LoginB")));
            finish(b); awaitUi(() -> field("account", JLabel.class).getText().equals("LoginB (Microsoft)") && field("signInDialog", JDialog.class) == null && field("progress", JProgressBar.class).getValue() == 100);
            snapshot("fixed-latest-login");
        } finally { a.release.countDown(); b.release.countDown(); close(); }
    }
    @Test void windowCloseSuppressesLateResponseAndNewDialog() throws Exception {
        FakeActions fake = new FakeActions(); Attempt a = new Attempt("LoginA");
        try { open(fake); signIn(a, fake); snapshot("fixed-before-close");
            SwingUtilities.invokeAndWait(() -> { JDialog d=field("signInDialog", JDialog.class); d.dispatchEvent(new WindowEvent(d, WindowEvent.WINDOW_CLOSING)); });
            finish(a); awaitUi(() -> field("signInDialog", JDialog.class)==null);
            SwingUtilities.invokeAndWait(() -> assertEquals("Player (offline)", field("account", JLabel.class).getText())); snapshot("fixed-after-close");
        } finally { a.release.countDown(); close(); }
    }
    @Test void offlineClickClosesDialogAndKeepsOfflineLabel() throws Exception {
        FakeActions fake = new FakeActions(); Attempt a = new Attempt("LoginA");
        try { open(fake); signIn(a, fake);
            SwingUtilities.invokeAndWait(() -> { field("offlineName", JTextField.class).setText("LocalPlayer"); button(panel, "Use offline name").doClick(0); });
            finish(a); awaitUi(() -> field("account", JLabel.class).getText().equals("LocalPlayer (offline)") && field("signInDialog", JDialog.class)==null); snapshot("fixed-after-offline");
        } finally { a.release.countDown(); close(); }
    }
    static void awaitUi(BooleanSupplier predicate) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime()<deadline) { AtomicReference<Boolean> ready=new AtomicReference<>(); SwingUtilities.invokeAndWait(() -> ready.set(predicate.getAsBoolean())); if (ready.get()) return; Thread.sleep(20); }
        fail("Native UI milestone timed out");
    }
    static AbstractButton button(Container root, String title) {
        for (Component child: root.getComponents()) { if (child instanceof AbstractButton && title.equals(((AbstractButton)child).getText())) return (AbstractButton)child; if (child instanceof Container) { AbstractButton found=button((Container)child,title); if(found!=null)return found; } } return null;
    }
    static JOptionPane findOption(Container root) { if(root instanceof JOptionPane)return (JOptionPane)root; for(Component child:root.getComponents())if(child instanceof Container){JOptionPane found=findOption((Container)child);if(found!=null)return found;}return null; }
    static String text(Container root) { StringBuilder result=new StringBuilder(); for(Component child:root.getComponents()){if(child instanceof JTextArea)result.append(((JTextArea)child).getText());if(child instanceof Container)result.append(text((Container)child));}return result.toString(); }
}
