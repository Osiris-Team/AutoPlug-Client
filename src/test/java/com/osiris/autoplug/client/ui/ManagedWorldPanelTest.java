package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.ui.LauncherActions.WorldInfo;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Headless Swing tests: real EDT and worker queue, no frames, processes, network or Robot. */
class ManagedWorldPanelTest {
    private static final WorldInfo ALPHA = world("alpha", false), BETA = world("beta", false), LOCAL = world("local", true);

    @Test void serviceCallsStayOffEdtAndLocalWorldsNeverEnterManagedConsole() throws Exception {
        Actions actions = new Actions(); ManagedWorldPanel panel = create(actions);
        try {
            drain(panel);
            edt(() -> {
                assertEquals(2, selector(panel).getItemCount());
                for (int i = 0; i < selector(panel).getItemCount(); i++) assertFalse(((WorldInfo) selector(panel).getItemAt(i)).local);
                for (String label : Arrays.asList("Stop", "Restart", "Back up", "AutoPlug help", "Server status")) button(panel, label).doClick(0);
                return null;
            });
            drain(panel);
            assertTrue(actions.calls.contains("stop:alpha"));
            assertTrue(actions.calls.contains("restart:alpha"));
            assertTrue(actions.calls.contains("command:alpha:.backup"));
            assertTrue(actions.calls.contains("command:alpha:.help"));
            assertTrue(actions.calls.contains("command:alpha:list"));
            assertTrue(actions.calls.contains("log:alpha"));
            assertFalse(actions.calledOnEdt.get(), "World listing, log reads and commands must run in the worker");
        } finally { close(panel); }
    }

    @Test void slowRefreshPreservesASelectionMadeWhileTheListingWasInFlight() throws Exception {
        Actions actions = new Actions(); ManagedWorldPanel panel = create(actions); Gate gate = new Gate();
        try {
            drain(panel); actions.nextList.set(gate);
            edt(() -> { panel.refresh(); return null; }); gate.awaitStarted();
            edt(() -> { selector(panel).setSelectedItem(BETA); return null; });
            gate.release.countDown(); drain(panel);
            assertEquals("beta", edt(() -> ((WorldInfo) selector(panel).getSelectedItem()).id));
            assertEquals("log from beta", edt(() -> output(panel).getText()));
        } finally { gate.release.countDown(); close(panel); }
    }

    @Test void staleLogCannotReplaceTheNewSelectionsConsole() throws Exception {
        Actions actions = new Actions(); ManagedWorldPanel panel = create(actions); Gate gate = new Gate();
        try {
            drain(panel); actions.nextLog.set(gate);
            edt(() -> { panel.refreshLog(); return null; }); gate.awaitStarted();
            edt(() -> { selector(panel).setSelectedItem(BETA); return null; });
            gate.release.countDown(); drain(panel);
            assertNotEquals("log from alpha", edt(() -> output(panel).getText()));
            edt(() -> { panel.refreshLog(); return null; }); drain(panel);
            assertEquals("log from beta", edt(() -> output(panel).getText()));
            assertFalse(actions.calledOnEdt.get());
        } finally { gate.release.countDown(); close(panel); }
    }

    @Test void queuedCommandsRetainTheWorldCapturedWhenSendWasClicked() throws Exception {
        Actions actions = new Actions(); ManagedWorldPanel panel = create(actions); Gate gate = new Gate();
        try {
            drain(panel); actions.nextCommand.set(gate);
            send(panel, "first world command"); gate.awaitStarted();
            edt(() -> { selector(panel).setSelectedItem(BETA); return null; });
            send(panel, "second world command");
            gate.release.countDown(); drain(panel);
            assertTrue(actions.calls.contains("command:alpha:first world command"));
            assertTrue(actions.calls.contains("command:beta:second world command"));
            assertFalse(actions.calls.contains("command:beta:first world command"));
            assertEquals("beta", edt(() -> ((WorldInfo) selector(panel).getSelectedItem()).id));
            assertEquals("", edt(() -> command(panel).getText()));
        } finally { gate.release.countDown(); close(panel); }
    }

    @Test void commandFailureFromPreviousSelectionCannotOverwriteCurrentStatus() throws Exception {
        Actions actions = new Actions(); ManagedWorldPanel panel = create(actions); Gate gate = new Gate();
        List<String> statuses = new CopyOnWriteArrayList<>();
        try {
            drain(panel); actions.nextCommand.set(gate); actions.failCommand.set(true);
            edt(() -> { state(panel).addPropertyChangeListener("text", event -> statuses.add(String.valueOf(event.getNewValue()))); return null; });
            send(panel, "failing command"); gate.awaitStarted();
            edt(() -> { selector(panel).setSelectedItem(BETA); statuses.clear(); return null; });
            gate.release.countDown(); drain(panel);
            assertTrue(actions.calls.contains("command:alpha:failing command"));
            assertFalse(statuses.stream().anyMatch(value -> value.contains("alpha command failed")), statuses.toString());
            assertEquals("log from beta", edt(() -> output(panel).getText()));
        } finally { gate.release.countDown(); close(panel); }
    }

    @Test void closeDiscardsQueuedCommandsAndLateResultsWithoutTouchingAnotherWorld() throws Exception {
        Actions actions = new Actions(); ManagedWorldPanel panel = create(actions); Gate gate = new Gate();
        try {
            drain(panel); actions.nextLog.set(gate);
            edt(() -> { panel.refreshLog(); return null; }); gate.awaitStarted();
            send(panel, "must not execute");
            String before = edt(() -> { panel.close(); return output(panel).getText(); });
            gate.release.countDown();
            assertTrue(worker(panel).awaitTermination(3, TimeUnit.SECONDS));
            edt(() -> { panel.refresh(); panel.refreshLog(); return null; });
            edt(() -> null);
            assertFalse(actions.calls.contains("command:alpha:must not execute"));
            assertEquals(before, edt(() -> output(panel).getText()));
            assertFalse(actions.calls.stream().anyMatch(value -> value.startsWith("command:beta:")));
            assertFalse(timer(panel).isRunning());
        } finally { gate.release.countDown(); close(panel); }
    }

    private static ManagedWorldPanel create(Actions actions) throws Exception {
        return edt(() -> {
            ManagedWorldPanel panel = new ManagedWorldPanel(actions, world -> { }, path -> { });
            panel.refresh(); return panel;
        });
    }
    private static void send(ManagedWorldPanel panel, String text) throws Exception {
        edt(() -> { command(panel).setText(text); button(panel, "Send").doClick(0); return null; });
    }
    private static void drain(ManagedWorldPanel panel) throws Exception {
        // A completion on the EDT may enqueue a refresh and then a log read.
        for (int i = 0; i < 4; i++) {
            worker(panel).submit(() -> { }).get(3, TimeUnit.SECONDS);
            edt(() -> null);
        }
    }
    private static void close(ManagedWorldPanel panel) throws Exception {
        edt(() -> { panel.close(); return null; });
        assertTrue(worker(panel).awaitTermination(3, TimeUnit.SECONDS));
        edt(() -> null);
    }
    private static <T> T edt(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(future);
        return future.get(3, TimeUnit.SECONDS);
    }
    private static Object field(ManagedWorldPanel panel, String name) throws Exception {
        Field field = ManagedWorldPanel.class.getDeclaredField(name); field.setAccessible(true); return field.get(panel);
    }
    private static JComboBox<?> selector(ManagedWorldPanel panel) throws Exception { return (JComboBox<?>) field(panel, "worlds"); }
    private static JTextArea output(ManagedWorldPanel panel) throws Exception { return (JTextArea) field(panel, "output"); }
    private static JTextField command(ManagedWorldPanel panel) throws Exception { return (JTextField) field(panel, "command"); }
    private static JLabel state(ManagedWorldPanel panel) throws Exception { return (JLabel) field(panel, "state"); }
    private static ExecutorService worker(ManagedWorldPanel panel) throws Exception { return (ExecutorService) field(panel, "worker"); }
    private static Timer timer(ManagedWorldPanel panel) throws Exception { return (Timer) field(panel, "timer"); }
    private static JButton button(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton && label.equals(((JButton) child).getText())) return (JButton) child;
            if (child instanceof Container) { JButton found = findButton((Container) child, label); if (found != null) return found; }
        }
        throw new AssertionError("Missing button " + label);
    }
    private static JButton findButton(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton && label.equals(((JButton) child).getText())) return (JButton) child;
            if (child instanceof Container) { JButton found = findButton((Container) child, label); if (found != null) return found; }
        }
        return null;
    }
    private static WorldInfo world(String id, boolean local) {
        return new WorldInfo(id, id, "server-" + id, "client-" + id, "world-" + id, null, true, local, "1.21.1");
    }
    private static final class Gate {
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        void awaitStarted() throws InterruptedException { assertTrue(started.await(3, TimeUnit.SECONDS), "Worker did not reach controlled operation"); }
        void pause() throws InterruptedException { started.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new InterruptedException("Fixture gate timeout"); }
    }
    private static final class Actions implements LauncherActions {
        final List<String> calls = new CopyOnWriteArrayList<>();
        final AtomicBoolean calledOnEdt = new AtomicBoolean(), failCommand = new AtomicBoolean();
        final AtomicReference<Gate> nextList = new AtomicReference<>(), nextLog = new AtomicReference<>(), nextCommand = new AtomicReference<>();
        private void record(String call) { if (SwingUtilities.isEventDispatchThread()) calledOnEdt.set(true); calls.add(call); }
        private void pause(AtomicReference<Gate> next) throws InterruptedException { Gate gate = next.getAndSet(null); if (gate != null) gate.pause(); }
        @Override public List<WorldInfo> worlds() throws Exception { record("worlds"); pause(nextList); return Arrays.asList(ALPHA, BETA, LOCAL); }
        @Override public String serverLog(String id) throws Exception { record("log:" + id); pause(nextLog); return "log from " + id; }
        @Override public void serverCommand(String id, String command) throws Exception {
            record("command:" + id + ":" + command); pause(nextCommand);
            if (failCommand.getAndSet(false)) throw new IOException(id + " command failed");
        }
        @Override public void stopWorld(String id) { record("stop:" + id); }
        @Override public void restartWorld(String id) { record("restart:" + id); }
    }
}
